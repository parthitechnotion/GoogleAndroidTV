/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.usbtuner.exoplayer;

import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.android.usbtuner.tvinput.PlaybackCacheListener;

import junit.framework.Assert;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Extracts samples from {@link MediaDataSource} and stores them on the disk, which enables
 * trickplay.
 */
public class CachedSampleSourceExtractor extends BaseSampleSourceExtractor implements
        CacheManager.EvictListener {
    private static final String TAG = "CachedSampleSourceExt";
    private static final boolean DEBUG = false;

    public static final long CHUNK_DURATION_US = TimeUnit.MILLISECONDS.toMicros(500);

    private static final long LIVE_THRESHOLD_US = TimeUnit.SECONDS.toMicros(1);
    private static final long CACHE_WRITE_TIMEOUT_MS = 10 * 1000;  // 10 seconds

    private final CacheManager mCacheManager;
    private final String mId;

    private final PlaybackCacheListener mCacheListener;
    private long[] mCacheEndPositionUs;
    private SampleCache[] mSampleCaches;
    private CachedSampleQueue[] mPlayingSampleQueues;
    private final SamplePool mSamplePool = new SamplePool();
    private long mLastBufferedPositionUs = C.UNKNOWN_TIME_US;
    private long mCurrentPlaybackPositionUs = 0;

    private class CachedSampleQueue extends SampleQueue {
        private SampleCache mCache = null;

        public CachedSampleQueue(SamplePool samplePool) {
            super(samplePool);
        }

        public void setSource(SampleCache newCache) {
            for (SampleCache cache = mCache; cache != null; cache = cache.getNext()) {
                cache.clear();
                cache.close();
            }
            mCache = newCache;
            for (SampleCache cache = mCache; cache != null; cache = cache.getNext()) {
                cache.resetRead();
            }
        }

        public boolean maybeReadSample() {
            if (isDurationGreaterThan(CHUNK_DURATION_US)) {
                return false;
            }
            SampleHolder sample = mCache.maybeReadSample();
            if (sample == null) {
                if (!mCache.canReadMore() && mCache.getNext() != null) {
                    mCache.clear();
                    mCache.close();
                    mCache = mCache.getNext();
                    mCache.resetRead();
                    return maybeReadSample();
                } else {
                    return false;
                }
            } else {
                queueSample(sample);
                return true;
            }
        }

        public int dequeueSample(SampleHolder sample) {
            maybeReadSample();
            return super.dequeueSample(sample);
        }

        @Override
        public void clear() {
            super.clear();
            for (SampleCache cache = mCache; cache != null; cache = cache.getNext()) {
                cache.clear();
                cache.close();
            }
            mCache = null;
        }

        public long getSourceStartPositionUs() {
            return mCache == null ? -1 : mCache.getStartPositionUs();
        }
    }

    public CachedSampleSourceExtractor(MediaDataSource source, CacheManager cacheManager,
            PlaybackCacheListener cacheListener) {
        super(source);
        mCacheManager = cacheManager;
        mCacheListener = cacheListener;
        mId = Long.toHexString(new Random().nextLong());
        cacheListener.onCacheStateChanged(true);  // Enable trickplay
    }

    @Override
    public void queueSample(int index, SampleHolder sample, ConditionVariable conditionVariable)
            throws IOException {
        long writeStartTimeNs = SystemClock.elapsedRealtimeNanos();
        synchronized (this) {
            SampleCache cache = mSampleCaches[index];
            if ((sample.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                if (sample.timeUs >= mCacheEndPositionUs[index]) {
                    try {
                        SampleCache nextCache = mCacheManager.createNewWriteFile(
                                getTrackId(index), mCacheEndPositionUs[index], mSamplePool);
                        cache.finishWrite(nextCache);
                        mSampleCaches[index] = cache = nextCache;
                        mCacheEndPositionUs[index] =
                                ((sample.timeUs / CHUNK_DURATION_US) + 1) * CHUNK_DURATION_US;
                    } catch (IOException e) {
                        cache.finishWrite(null);
                        throw e;
                    }
                }
            }
            cache.writeSample(sample, conditionVariable);
        }
        if (!conditionVariable.block(CACHE_WRITE_TIMEOUT_MS)) {
            Log.e(TAG, "Error: Serious delay on writing cache");
            conditionVariable.block();
        }

        // Check if the storage has enough bandwidth for trickplay. Otherwise we disable it
        // and notify the slowness through the playback cache listener.
        mCacheManager.addWriteStat(sample.size,
                SystemClock.elapsedRealtimeNanos() - writeStartTimeNs);
        if (mCacheManager.isWriteSlow()) {
            Log.w(TAG, "Disk is too slow for trickplay. Disable trickplay.");
            mCacheManager.disable();
            mCacheListener.onDiskTooSlow();
        }
    }

    private String getTrackId(int index) {
        return String.format(Locale.ENGLISH, "%s_%x", mId, index);
    }

    @Override
    public void initOnPrepareLocked(int trackCount) throws IOException {
        mSampleCaches = new SampleCache[trackCount];
        mPlayingSampleQueues = new CachedSampleQueue[trackCount];
        mCacheEndPositionUs = new long[trackCount];
        for (int i = 0; i < trackCount; i++) {
            mSampleCaches[i] = mCacheManager.createNewWriteFile(getTrackId(i), 0, mSamplePool);
            mPlayingSampleQueues[i] = null;
            mCacheEndPositionUs[i] = CHUNK_DURATION_US;
        }
    }

    @Override
    public void selectTrack(int index) {
        synchronized (this) {
            if (mPlayingSampleQueues[index] == null) {
                String trackId = getTrackId(index);
                mPlayingSampleQueues[index] = new CachedSampleQueue(mSamplePool);
                mCacheManager.registerEvictListener(trackId, this);
                seekIndividualTrackLocked(index, mCurrentPlaybackPositionUs,
                        isLiveLocked(mCurrentPlaybackPositionUs));
                mPlayingSampleQueues[index].maybeReadSample();
            }
        }
    }

    @Override
    public void deselectTrack(int index) {
        synchronized (this) {
            if (mPlayingSampleQueues[index] != null) {
                mPlayingSampleQueues[index].clear();
                mPlayingSampleQueues[index] = null;
                mCacheManager.unregisterEvictListener(getTrackId(index));
            }
        }
    }

    @Override
    public long getBufferedPositionUs() {
        synchronized (this) {
            Long result = null;
            for (CachedSampleQueue queue : mPlayingSampleQueues) {
                if (queue == null) {
                    continue;
                }
                Long bufferedPositionUs = queue.getEndPositionUs();
                if (bufferedPositionUs == null) {
                    continue;
                }
                if (result == null || result > bufferedPositionUs) {
                    result = bufferedPositionUs;
                }
            }
            if (result == null) {
                return mLastBufferedPositionUs;
            } else {
                return (mLastBufferedPositionUs = result);
            }
        }
    }

    @Override
    public void seekTo(long positionUs) {
        synchronized (this) {
            boolean isLive = isLiveLocked(positionUs);

            // Seek video track first
            for (int i = 0; i < mPlayingSampleQueues.length; ++i) {
                CachedSampleQueue queue = mPlayingSampleQueues[i];
                if (queue == null) {
                    continue;
                }
                seekIndividualTrackLocked(i, positionUs, isLive);
                if (DEBUG) {
                    Log.d(TAG, "start time = " + queue.getSourceStartPositionUs());
                }
            }
            mLastBufferedPositionUs = positionUs;
        }
    }

    private boolean isLiveLocked(long positionUs) {
        Long livePositionUs = null;
        for (SampleCache cache : mSampleCaches) {
            if (livePositionUs == null || livePositionUs < cache.getEndPositionUs()) {
                livePositionUs = cache.getEndPositionUs();
            }
        }
        return (livePositionUs == null
                || Math.abs(livePositionUs - positionUs) < LIVE_THRESHOLD_US);
    }

    private void seekIndividualTrackLocked(int index, long positionUs, boolean isLive) {
        CachedSampleQueue queue = mPlayingSampleQueues[index];
        if (queue == null) {
            return;
        }
        queue.clear();
        if (isLive) {
            queue.setSource(mSampleCaches[index]);
        } else {
            queue.setSource(mCacheManager.getReadFile(getTrackId(index), positionUs));
        }
        queue.maybeReadSample();
    }

    @Override
    public int readSample(int track, SampleHolder sampleHolder) {
        synchronized (this) {
            CachedSampleQueue queue = mPlayingSampleQueues[track];
            Assert.assertNotNull(queue);
            queue.maybeReadSample();
            int result = queue.dequeueSample(sampleHolder);
            if (result != SampleSource.SAMPLE_READ && getEos()) {
                return SampleSource.END_OF_STREAM;
            }
            return result;
        }
    }

    @Override
    public void cleanUpImpl() {
        if (mSampleCaches == null) {
            return;
        }
        for (int i = 0; i < mSampleCaches.length; ++i) {
            mSampleCaches[i].finishWrite(null);
            mCacheManager.unregisterEvictListener(getTrackId(i));
        }
        for (int i = 0; i < mSampleCaches.length; ++i) {
            mCacheManager.clearTrack(getTrackId(i));
        }
    }

    @Override
    public boolean continueBuffering(long positionUs) {
        synchronized (this) {
            boolean hasSamples = true;
            mCurrentPlaybackPositionUs = positionUs;
            for (CachedSampleQueue queue : mPlayingSampleQueues) {
                if (queue == null) {
                    continue;
                }
                queue.maybeReadSample();
                if (queue.isEmpty()) {
                    hasSamples = false;
                }
            }
            return hasSamples;
        }
    }

    // CacheEvictListener
    @Override
    public void onCacheEvicted(String id, long createdTimeMs) {
        mCacheListener.onCacheStartTimeChanged(
                createdTimeMs + TimeUnit.MICROSECONDS.toMillis(CHUNK_DURATION_US));
    }
}

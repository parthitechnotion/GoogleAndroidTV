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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.android.usbtuner.tvinput.PlaybackCacheListener;

import junit.framework.Assert;

import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A class that plays a recorded stream without using {@link MediaExtractor},
 * since all samples are extracted and stored to the permanent storage already.
 */
public class ReplaySampleSourceExtractor implements SampleExtractor, CacheManager.EvictListener {
    private static final String TAG = "ReplaySampleSourceExt";
    private static final boolean DEBUG = false;

    public static final long CHUNK_DURATION_US = TimeUnit.MILLISECONDS.toMicros(500);

    private android.media.MediaFormat[] mMediaFormats;
    private TrackInfo[] mTrackInfos;
    private String[] mIds;

    private boolean mEos;
    private boolean mReleased;


    private final CacheManager mCacheManager;

    private final PlaybackCacheListener mCacheListener;
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
                if (!mCache.canReadMore()) {
                    if (mCache.getNext() == null) {
                        // reached the end of the recording
                        setEos();
                        return false;
                    } else {
                        mCache.clear();
                        mCache.close();
                        mCache = mCache.getNext();
                        mCache.resetRead();
                        return maybeReadSample();
                    }
                }
                return false;
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

    public ReplaySampleSourceExtractor(
            CacheManager cacheManager, PlaybackCacheListener cacheListener) {
        mCacheManager = cacheManager;
        mCacheListener = cacheListener;
        cacheListener.onCacheStateChanged(true);  // Enable trickplay
    }

    @Override
    public boolean prepare() throws IOException {
        ArrayList<Pair<String, android.media.MediaFormat>> trackInfos =
                mCacheManager.readTrackInfoFiles();
        if (trackInfos == null || trackInfos.size() <= 0) {
            return false;
        }
        int trackCount = trackInfos.size();
        mIds = new String[trackCount];
        mMediaFormats = new android.media.MediaFormat[trackCount];
        mTrackInfos = new TrackInfo[trackCount];
        for (int i = 0; i < trackCount; ++i) {
            Pair<String, android.media.MediaFormat> pair = trackInfos.get(i);
            mIds[i] = pair.first;
            mMediaFormats[i] = pair.second;

            // TODO: save this according to recording length
            long durationUs = mMediaFormats[i].containsKey(android.media.MediaFormat.KEY_DURATION)
                    ? mMediaFormats[i].getLong(android.media.MediaFormat.KEY_DURATION)
                    : C.UNKNOWN_TIME_US;
            String mime = mMediaFormats[i].getString(android.media.MediaFormat.KEY_MIME);
            mTrackInfos[i] = new TrackInfo(mime, durationUs);
        }
        initOnLoad(trackCount);
        return true;
    }

    @Override
    public TrackInfo[] getTrackInfos() {
        return mTrackInfos;
    }

    private void setEos() {
        mEos = true;
    }

    public boolean getEos() {
        return mEos;
    }

    @Override
    public void getTrackMediaFormat(int track, MediaFormatHolder mediaFormatHolder) {
        mediaFormatHolder.format =
                MediaFormat.createFromFrameworkMediaFormatV16(mMediaFormats[track]);
        mediaFormatHolder.drmInitData = null;
    }

    @Override
    public void release() {
        if (!mReleased) {
            cleanUpImpl();
        }
        mReleased = true;
    }


    private String getTrackId(int index) {
        return mIds[index];
    }

    public void initOnLoad(int trackCount) throws IOException {
        mPlayingSampleQueues = new CachedSampleQueue[trackCount];
        for (int i = 0; i < trackCount; i++) {
            mCacheManager.loadTrackFormStorage(mIds[i], mSamplePool);
        }
    }

    @Override
    public void selectTrack(int index) {
        if (mPlayingSampleQueues[index] == null) {
            String trackId = getTrackId(index);
            mPlayingSampleQueues[index] = new CachedSampleQueue(mSamplePool);
            mCacheManager.registerEvictListener(trackId, this);
            seekIndividualTrack(index, mCurrentPlaybackPositionUs);
            mPlayingSampleQueues[index].maybeReadSample();
        }
    }

    @Override
    public void deselectTrack(int index) {
        if (mPlayingSampleQueues[index] != null) {
            mPlayingSampleQueues[index].clear();
            mPlayingSampleQueues[index] = null;
            mCacheManager.unregisterEvictListener(getTrackId(index));
        }
    }

    @Override
    public long getBufferedPositionUs() {
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

    @Override
    public void seekTo(long positionUs) {
        // Seek video track first
        for (int i = 0; i < mPlayingSampleQueues.length; ++i) {
            CachedSampleQueue queue = mPlayingSampleQueues[i];
            if (queue == null) {
                continue;
            }
            seekIndividualTrack(i, positionUs);
            if (DEBUG) {
                Log.d(TAG, "start time = " + queue.getSourceStartPositionUs());
            }
        }
        mLastBufferedPositionUs = positionUs;
    }

    private void seekIndividualTrack(int index, long positionUs) {
        CachedSampleQueue queue = mPlayingSampleQueues[index];
        if (queue == null) {
            return;
        }
        queue.clear();
        queue.setSource(mCacheManager.getReadFile(getTrackId(index), positionUs));
        queue.maybeReadSample();
    }

    @Override
    public int readSample(int track, SampleHolder sampleHolder) {
        CachedSampleQueue queue = mPlayingSampleQueues[track];
        Assert.assertNotNull(queue);
        queue.maybeReadSample();
        int result = queue.dequeueSample(sampleHolder);
        if (result != SampleSource.SAMPLE_READ && getEos()) {
            return SampleSource.END_OF_STREAM;
        }
        return result;
    }

    public void cleanUpImpl() {
        mCacheManager.close();
        for (int i = 0; i < mIds.length; ++i) {
            mCacheManager.unregisterEvictListener(getTrackId(i));
            mCacheManager.clearTrack(getTrackId(i));
        }
    }

    @Override
    public boolean continueBuffering(long positionUs) {
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

    // CacheEvictListener
    // TODO: Remove this. It will not be called.
    @Override
    public void onCacheEvicted(String id, long createdTimeMs) {
        mCacheListener.onCacheStartTimeChanged(
                createdTimeMs + TimeUnit.MICROSECONDS.toMillis(CHUNK_DURATION_US));
    }
}

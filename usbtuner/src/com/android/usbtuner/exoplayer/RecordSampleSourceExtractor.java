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
import android.media.MediaExtractor;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.util.MimeTypes;
import com.android.usbtuner.tvinput.PlaybackCacheListener;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Records live streams on the disk for DVR.
 * <p>
 * For the convenience of testing, it implements {@link SampleExtractor}.
 */
public class RecordSampleSourceExtractor implements SampleExtractor, CacheManager.EvictListener {
    // TODO: Decouple from {@link SampleExtractor}. Handle recording errors properly.

    private static final String TAG = "RecordSampleSourceExtractor";

    // Maximum bandwidth of 1080p channel is about 2.2MB/s. 2MB for a sample will suffice.
    private static final int SAMPLE_BUFFER_SIZE = 1024 * 1024 * 2;

    private final MediaDataSource mDataSource;
    private final MediaExtractor mMediaExtractor;
    private final ExtractorThread mExtractorThread;
    private int mTrackCount;
    private TrackInfo[] mTrackInfos;
    private android.media.MediaFormat[] mMediaFormat;

    private boolean mEos = false;
    private boolean mReleased = false;

    public static final long CHUNK_DURATION_US = TimeUnit.MILLISECONDS.toMicros(500);

    private static final long CACHE_WRITE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);  // 10 seconds

    private final CacheManager mCacheManager;
    private final String mId;

    private final PlaybackCacheListener mCacheListener;
    private long[] mCacheEndPositionsUs;
    private SampleCache[] mSampleCaches;
    private final SamplePool mSamplePool;

    public RecordSampleSourceExtractor(MediaDataSource source, CacheManager cacheManager,
            PlaybackCacheListener cacheListener) {
        mDataSource = source;
        mMediaExtractor = new MediaExtractor();
        mExtractorThread = new ExtractorThread();
        mCacheManager = cacheManager;
        mCacheListener = cacheListener;
        mSamplePool = new SamplePool();
        // TODO: Use UUID afterwards.
        mId = Long.toHexString(new Random().nextLong());
        cacheListener.onCacheStateChanged(true);  // Enable trickplay
    }

    private class ExtractorThread extends Thread {
        private volatile boolean mQuitRequested = false;

        public ExtractorThread() {
            super("ExtractorThread");
        }

        @Override
        public void run() {
            SampleHolder sample = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
            sample.replaceBuffer(SAMPLE_BUFFER_SIZE);
            ConditionVariable conditionVariable = new ConditionVariable();
            while (!mQuitRequested) {
                fetchSample(sample, conditionVariable);
            }
            cleanUp();
        }

        private void fetchSample(SampleHolder sample, ConditionVariable conditionVariable) {
            int index = mMediaExtractor.getSampleTrackIndex();
            if (index < 0) {
                Log.i(TAG, "EoS");
                mQuitRequested = true;
                setEos();
                return;
            }
            sample.data.clear();
            sample.size = mMediaExtractor.readSampleData(sample.data, 0);
            if (sample.size < 0 || sample.size > SAMPLE_BUFFER_SIZE) {
                // Should not happen
                Log.e(TAG, "Invalid sample size: " + sample.size);
                mMediaExtractor.advance();
                return;
            }
            sample.data.position(sample.size);
            sample.timeUs = mMediaExtractor.getSampleTime();
            sample.flags = mMediaExtractor.getSampleFlags();

            mMediaExtractor.advance();
            try {
                queueSample(index, sample, conditionVariable);
            } catch (IOException e) {
                mQuitRequested = true;
                setEos();
            }
        }

        public void quit() {
            mQuitRequested = true;
        }
    }

    private void queueSample(int index, SampleHolder sample, ConditionVariable conditionVariable)
            throws IOException {
        long writeStartTimeNs = SystemClock.elapsedRealtimeNanos();
        synchronized (this) {
            SampleCache cache = mSampleCaches[index];
            if ((sample.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                if (sample.timeUs >= mCacheEndPositionsUs[index]) {
                    try {
                        SampleCache nextCache = mCacheManager.createNewWriteFile(
                                getTrackId(index), mCacheEndPositionsUs[index], mSamplePool);
                        cache.finishWrite(nextCache);
                        mSampleCaches[index] = cache = nextCache;
                        mCacheEndPositionsUs[index] =
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

        // Check if the storage has enough bandwidth for recording. Otherwise we disable it
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

    private void initOnPrepareLocked(int trackCount) throws IOException {
        mSampleCaches = new SampleCache[trackCount];
        mCacheEndPositionsUs = new long[trackCount];
        for (int i = 0; i < trackCount; i++) {
            mSampleCaches[i] = mCacheManager.createNewWriteFile(getTrackId(i), 0, mSamplePool);
            mCacheEndPositionsUs[i] = CHUNK_DURATION_US;
        }
    }
    @Override
    public boolean prepare() throws IOException {
        synchronized (this) {
            mMediaExtractor.setDataSource(mDataSource);

            mTrackCount = mMediaExtractor.getTrackCount();
            initOnPrepareLocked(mTrackCount);
            mTrackInfos = new TrackInfo[mTrackCount];
            mMediaFormat = new android.media.MediaFormat[mTrackCount];
            for (int i = 0; i < mTrackCount; i++) {
                android.media.MediaFormat format = mMediaExtractor.getTrackFormat(i);
                long durationUs = format.containsKey(android.media.MediaFormat.KEY_DURATION)
                        ? format.getLong(android.media.MediaFormat.KEY_DURATION)
                        : C.UNKNOWN_TIME_US;
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                mMediaExtractor.selectTrack(i);
                mTrackInfos[i] = new TrackInfo(mime, durationUs);
                mMediaFormat[i] = format;
            }
        }
        mExtractorThread.start();
        return true;
    }

    @Override
    public synchronized TrackInfo[] getTrackInfos() {
        return mTrackInfos;
    }

    private synchronized void setEos() {
        mEos = true;
    }

    /**
     * Notifies whether sample extraction met end of stream.
     */
    public synchronized boolean getEos() {
        return mEos;
    }

    @Override
    public void getTrackMediaFormat(int track, MediaFormatHolder mediaFormatHolder) {
        mediaFormatHolder.format =
                MediaFormat.createFromFrameworkMediaFormatV16(mMediaExtractor
                        .getTrackFormat(track));
        mediaFormatHolder.drmInitData = null;
    }

    @Override
    public void selectTrack(int index) {
    }

    @Override
    public void deselectTrack(int index) {
    }

    @Override
    public void seekTo(long positionUs) {
    }

    @Override
    public int readSample(int track, SampleHolder sampleHolder) {
        return SampleSource.NOTHING_READ;
    }

    private void cleanUpInternal() {
        if (mSampleCaches == null) {
            return;
        }
        for (int i = 0; i < mSampleCaches.length; ++i) {
            mSampleCaches[i].finishWrite(null);
            mCacheManager.unregisterEvictListener(getTrackId(i));
        }
        if (mTrackCount > 0) {
            Pair<String, android.media.MediaFormat> audio = null, video = null;
            for (int i = 0; i < mTrackCount; ++i) {
                String mime = mTrackInfos[i].mimeType;
                if (MimeTypes.isAudio(mime)) {
                    audio = new Pair<>(getTrackId(i), mMediaFormat[i]);
                }
                if (MimeTypes.isVideo(mime)) {
                    video = new Pair<>(getTrackId(i), mMediaFormat[i]);
                }
            }
            mCacheManager.writeMetaFiles(audio, video);
        }
        for (int i = 0; i < mSampleCaches.length; ++i) {
            mCacheManager.clearTrack(getTrackId(i));
        }
    }

    @Override
    public void release() {
        synchronized (this) {
            mReleased = true;
        }
        if (mExtractorThread.isAlive()) {
            mExtractorThread.quit();

            // We don't join here to prevent hang --- MediaExtractor is released at the thread.
        } else {
            cleanUp();
        }
    }

    private synchronized void cleanUp() {
        if (!mReleased) {
            return;
        }
        cleanUpInternal();
        mMediaExtractor.release();
    }

    @Override
    public long getBufferedPositionUs() {
        // This will make player keep alive with no-op.
        return TrackRenderer.UNKNOWN_TIME_US;
    }

    @Override
    public boolean continueBuffering(long positionUs) {
        // This will make player keep alive with no-op.
        return true;
    }

    // CacheEvictListener
    @Override
    public void onCacheEvicted(String id, long createdTimeMs) {
        mCacheListener.onCacheStartTimeChanged(
                createdTimeMs + TimeUnit.MICROSECONDS.toMillis(CHUNK_DURATION_US));
    }
}

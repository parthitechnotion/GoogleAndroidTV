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

import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.os.ConditionVariable;
import android.util.Log;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.TrackInfo;

import java.io.IOException;

/**
 * Base class for feeding samples from a given media extractor using a extractor thread.
 */
public abstract class BaseSampleSourceExtractor implements SampleExtractor {
    private static final String TAG = "BaseSampleSourceExtractor";

    // Maximum bandwidth of 1080p channel is about 2.2MB/s. 2MB for a sample will suffice.
    private static final int SAMPLE_BUFFER_SIZE = 1024 * 1024 * 2;

    private final MediaDataSource mDataSource;
    private final MediaExtractor mMediaExtractor;
    private final ExtractorThread mExtractorThread;
    private TrackInfo[] mTrackInfos;

    private boolean mEos = false;
    private boolean mReleased = false;

    public BaseSampleSourceExtractor(MediaDataSource source) {
        mDataSource = source;
        mMediaExtractor = new MediaExtractor();
        mExtractorThread = new ExtractorThread();
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

    public abstract void queueSample(int index, SampleHolder sample, ConditionVariable
            conditionVariable) throws IOException;

    public void initOnPrepareLocked(int trackCount) throws IOException {}

    @Override
    public boolean prepare() throws IOException {
        synchronized (this) {
            mMediaExtractor.setDataSource(mDataSource);

            int trackCount = mMediaExtractor.getTrackCount();
            initOnPrepareLocked(trackCount);
            mTrackInfos = new TrackInfo[trackCount];
            for (int i = 0; i < trackCount; i++) {
                android.media.MediaFormat format = mMediaExtractor.getTrackFormat(i);
                long durationUs = format.containsKey(android.media.MediaFormat.KEY_DURATION)
                        ? format.getLong(android.media.MediaFormat.KEY_DURATION)
                        : C.UNKNOWN_TIME_US;
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                mMediaExtractor.selectTrack(i);
                mTrackInfos[i] = new TrackInfo(mime, durationUs);
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

    public void cleanUpImpl() {}

    public synchronized void cleanUp() {
        if (!mReleased) {
            return;
        }
        cleanUpImpl();
        mMediaExtractor.release();
    }
}

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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.android.usbtuner.tvinput.PlaybackCacheListener;

import junit.framework.Assert;

/**
 * Extracts from samples and keeps them in the memory.
 */
public class SimpleSampleSourceExtractor extends BaseSampleSourceExtractor {
    private final SamplePool mSamplePool = new SamplePool();
    private SampleQueue[] mPlayingSampleQueues;
    private long mLastBufferedPositionUs = C.UNKNOWN_TIME_US;

    public SimpleSampleSourceExtractor(MediaDataSource source,
            PlaybackCacheListener cacheListener) {
        super(source);
        cacheListener.onCacheStateChanged(false);  // Disable trickplay
    }

    public void queueSample(int index, SampleHolder sample, ConditionVariable conditionVariable) {
        sample.data.position(0).limit(sample.size);
        SampleHolder sampleToQueue = mSamplePool.acquireSample(sample.size);
        sampleToQueue.size = sample.size;
        sampleToQueue.clearData();
        sampleToQueue.data.put(sample.data);
        sampleToQueue.timeUs = sample.timeUs;
        sampleToQueue.flags = sample.flags;

        synchronized (this) {
            if (mPlayingSampleQueues[index] != null) {
                mPlayingSampleQueues[index].queueSample(sampleToQueue);
            }
        }
        Thread.yield();
    }

    @Override
    public void initOnPrepareLocked(int trackCount) {
        mPlayingSampleQueues = new SampleQueue[trackCount];
        for (int i = 0; i < trackCount; i++) {
            mPlayingSampleQueues[i] = null;
        }
    }

    @Override
    public void selectTrack(int index) {
        synchronized (this) {
            if (mPlayingSampleQueues[index] == null) {
                mPlayingSampleQueues[index] = new SampleQueue(mSamplePool);
            } else {
                mPlayingSampleQueues[index].clear();
            }
        }
    }

    @Override
    public void deselectTrack(int index) {
        synchronized (this) {
            if (mPlayingSampleQueues[index] != null) {
                mPlayingSampleQueues[index].clear();
                mPlayingSampleQueues[index] = null;
            }
        }
    }

    @Override
    public long getBufferedPositionUs() {
        synchronized (this) {
            Long result = null;
            for (SampleQueue queue : mPlayingSampleQueues) {
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
    public int readSample(int track, SampleHolder sampleHolder) {
        synchronized (this) {
            SampleQueue queue = mPlayingSampleQueues[track];
            Assert.assertNotNull(queue);
            int result = queue.dequeueSample(sampleHolder);
            if (result != SampleSource.SAMPLE_READ && getEos()) {
                return SampleSource.END_OF_STREAM;
            }
            return result;
        }
    }

    @Override
    public boolean continueBuffering(long positionUs) {
        synchronized (this) {
            for (SampleQueue queue : mPlayingSampleQueues) {
                if (queue == null) {
                    continue;
                }
                if (queue.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void seekTo(long positionUs) {
        // Not used.
    }
}

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

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;

import java.util.LinkedList;

/**
 * A sample queue which reads from the cache and passes to player pipeline.
 */
public class SampleQueue {
    private final LinkedList<SampleHolder> mQueue = new LinkedList<>();
    private final SamplePool mSamplePool;

    public SampleQueue(SamplePool samplePool) {
        mSamplePool = samplePool;
    }

    public void queueSample(SampleHolder sample) {
        mQueue.offer(sample);
    }

    public int dequeueSample(SampleHolder sample) {
        SampleHolder sampleFromQueue = mQueue.poll();
        if (sampleFromQueue == null) {
            return SampleSource.NOTHING_READ;
        }
        sample.size = sampleFromQueue.size;
        sample.flags = sampleFromQueue.flags;
        sample.timeUs = sampleFromQueue.timeUs;
        sample.clearData();
        sampleFromQueue.data.position(0).limit(sample.size);
        sample.data.put(sampleFromQueue.data);
        mSamplePool.releaseSample(sampleFromQueue);
        return SampleSource.SAMPLE_READ;
    }

    public void clear() {
        while (!mQueue.isEmpty()) {
            mSamplePool.releaseSample(mQueue.poll());
        }
    }

    public Long getStartPositionUs() {
        if (mQueue.isEmpty()) {
            return null;
        }
        return mQueue.getFirst().timeUs;
    }

    public Long getEndPositionUs() {
        if (mQueue.isEmpty()) {
            return null;
        }
        return mQueue.getLast().timeUs;
    }

    public boolean isEmpty() {
        return mQueue.isEmpty();
    }
}

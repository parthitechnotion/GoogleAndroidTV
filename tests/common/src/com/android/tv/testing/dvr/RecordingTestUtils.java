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
 * limitations under the License
 */

package com.android.tv.testing.dvr;

import android.support.annotation.RequiresPermission;

import com.android.tv.data.Channel;
import com.android.tv.dvr.Recording;

import junit.framework.Assert;

import java.util.Collections;

/**
 * Static utils for using {@link Recording} in tests.
 */
public final class RecordingTestUtils {
    public static Recording createTestRecordingWithIdAndPeriod(long id, long startTime,
            long endTime) {
        return Recording.builder(new Channel.Builder().build(), startTime, endTime)
                .setId(id)
                .setPrograms(Collections.EMPTY_LIST)
                .build();
    }

    public static Recording createTestRecordingWithPeriod(long startTime, long endTime) {
        return createTestRecordingWithIdAndPeriod(Recording.ID_NOT_SET, startTime, endTime);
    }

    public static Recording normalizePriority(Recording orig){
        return Recording.buildFrom(orig).setPriority(orig.getId()).build();
    }

    public static void assertRecordingEquals(Recording expected, Recording actual) {
        Assert.assertEquals("id", expected.getId(), actual.getId());
        Assert.assertEquals("uri", expected.getUri(), actual.getUri());
        Assert.assertEquals("channel", expected.getChannel(), actual.getChannel());
        Assert.assertEquals("programs", expected.getPrograms(), actual.getPrograms());
        Assert.assertEquals("start time", expected.getStartTimeMs(), actual.getStartTimeMs());
        Assert.assertEquals("end time", expected.getEndTimeMs(), actual.getEndTimeMs());
        Assert.assertEquals("media size", expected.getSize(), actual.getSize());
        Assert.assertEquals("state", expected.getState(), actual.getState());
        Assert.assertEquals("parent season recording", expected.getParentSeasonRecording(),
                actual.getParentSeasonRecording());
    }

    private RecordingTestUtils() { }
}

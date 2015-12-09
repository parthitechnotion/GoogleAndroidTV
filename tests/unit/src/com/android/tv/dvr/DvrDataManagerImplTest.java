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

package com.android.tv.dvr;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.tv.testing.dvr.RecordingTestUtils;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link DvrDataManagerImplTest}
 */
@SmallTest
public class DvrDataManagerImplTest extends TestCase {
    public void testGetNextScheduledStartTimeAfter() throws Exception {
        long id = 1;
        List<Recording> recordings = new ArrayList<>();
        assertNextStartTime(recordings, 0L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        recordings.add(RecordingTestUtils.createTestRecordingWithPeriod(id++, 10L, 20L));
        assertNextStartTime(recordings, 9L, 10L);
        assertNextStartTime(recordings, 10L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        recordings.add(RecordingTestUtils.createTestRecordingWithPeriod(id++, 20L, 30L));
        assertNextStartTime(recordings, 9L, 10L);
        assertNextStartTime(recordings, 10L, 20L);
        assertNextStartTime(recordings, 20L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        recordings.add(RecordingTestUtils.createTestRecordingWithPeriod(id++, 30L, 40L));
        assertNextStartTime(recordings, 9L, 10L);
        assertNextStartTime(recordings, 10L, 20L);
        assertNextStartTime(recordings, 20L, 30L);
        assertNextStartTime(recordings, 30L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        recordings.clear();
        recordings.add(RecordingTestUtils.createTestRecordingWithPeriod(id++, 10L, 20L));
        recordings.add(RecordingTestUtils.createTestRecordingWithPeriod(id++, 10L, 20L));
        recordings.add(RecordingTestUtils.createTestRecordingWithPeriod(id++, 10L, 20L));
        assertNextStartTime(recordings, 9L, 10L);
        assertNextStartTime(recordings, 10L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
    }

    private void assertNextStartTime(List<Recording> recordings, long startTime, long expected) {
        assertEquals("getNextScheduledStartTimeAfter()", expected,
                DvrDataManagerImpl.getNextStartTimeAfter(recordings, startTime));
    }
}
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


import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link Scheduler}.
 */
@SmallTest
public class SchedulerTest extends AndroidTestCase {
    private FakeClock mClock;
    private DvrDataManagerInMemoryImpl mDataManager;
    private Scheduler mScheduler;
    @Mock DvrSessionManager mSessionManager;
    @Mock AlarmManager mMockAlarmManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mClock = FakeClock.createWithCurrentTime();
        mDataManager = new DvrDataManagerInMemoryImpl(getContext());
        mScheduler = new Scheduler(mSessionManager, mDataManager, getContext(), mClock,
                mMockAlarmManager);
    }

    public void testUpdate_none() throws Exception {
        mScheduler.update();
        verifyZeroInteractions(mMockAlarmManager);
    }

    public void testUpdate_nextIn12Hours() throws Exception {
        long now = mClock.currentTimeMillis();
        long startTime = now + TimeUnit.HOURS.toMillis(12);
        Recording r = RecordingTestUtils.createTestRecordingWithPeriod(1, startTime,
                startTime + TimeUnit.HOURS.toMillis(1));
        mDataManager.addRecording(r);
        mScheduler.update();
        verify(mMockAlarmManager).set(
                eq(AlarmManager.RTC_WAKEUP),
                eq(startTime - Scheduler.MS_TO_WAKE_BEFORE_START),
                any(PendingIntent.class));
    }

    public void testStartsWithin() throws Exception {
        long now = mClock.currentTimeMillis();
        long startTime = now + 3;
        Recording r = RecordingTestUtils
                .createTestRecordingWithPeriod(1, startTime, startTime + 100);
        assertFalse(mScheduler.startsWithin(r, 2));
        assertTrue(mScheduler.startsWithin(r, 3));
    }
}
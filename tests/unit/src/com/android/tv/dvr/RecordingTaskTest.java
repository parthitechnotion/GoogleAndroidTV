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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.tv.common.dvr.DvrSessionClient;
import com.android.tv.data.Channel;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link RecordingTask}.
 */
@SmallTest
public class RecordingTaskTest extends AndroidTestCase {
    private FakeClock mFakeClock;
    private DvrDataManagerInMemoryImpl mDataManager;
    @Mock
    DvrSessionManager mMockSessionManager;
    @Mock
    DvrSessionClient mMockDvrSessionClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mFakeClock = FakeClock.createWithTimeOne();
        mDataManager = new DvrDataManagerInMemoryImpl(getContext());
    }

    public void testRun_sleepUntil() {
        long startTime = mFakeClock.currentTimeMillis();
        long endTime = startTime + 1;
        Recording r = RecordingTestUtils.createTestRecordingWithPeriod(1, startTime, endTime);
        RecordingTask task = new RecordingTask(r, mMockSessionManager, mDataManager,
                mFakeClock);

        Channel channel = r.getChannel();
        String inputId = channel.getInputId();
        when(mMockSessionManager.canAcquireDvrSession(inputId, channel))
                .thenReturn(true);
        when(mMockSessionManager.acquireDvrSession(inputId, channel))
                .thenReturn(mMockDvrSessionClient);
        task.run();
        assertEquals("Recording " + r + "finish time", endTime + RecordingTask.MS_AFTER_END,
                mFakeClock.currentTimeMillis());
    }

    public void testRun_connectAndRelease() {
        long startTime = mFakeClock.currentTimeMillis();
        long endTime = startTime + 1;
        Recording r = RecordingTestUtils.createTestRecordingWithPeriod(1, startTime, endTime);
        RecordingTask task = new RecordingTask(r, mMockSessionManager, mDataManager,
                mFakeClock);

        Channel channel = r.getChannel();
        String inputId = channel.getInputId();
        when(mMockSessionManager.canAcquireDvrSession(inputId, channel))
                .thenReturn(true);
        when(mMockSessionManager.acquireDvrSession(inputId, channel))
                .thenReturn(mMockDvrSessionClient);
        task.run();

        verify(mMockSessionManager).canAcquireDvrSession(inputId, channel);
        verify(mMockSessionManager).acquireDvrSession(inputId, channel);
        verify(mMockDvrSessionClient).connect(inputId, task);
        verify(mMockDvrSessionClient).startRecord(channel.getUri(),
                RecordingTask.getIdAsMediaUri(r));
        verify(mMockDvrSessionClient).stopRecord();
        verify(mMockSessionManager).releaseDvrSession(mMockDvrSessionClient);
        verifyNoMoreInteractions(mMockDvrSessionClient, mMockSessionManager);
     }


    public void testRun_cannotAcquireSession() {
        long startTime = mFakeClock.currentTimeMillis();
        long endTime = startTime + 1;
        Recording r = RecordingTestUtils.createTestRecordingWithPeriod(1, startTime, endTime);
        mDataManager.addRecording(r);
        RecordingTask task = new RecordingTask(r, mMockSessionManager, mDataManager,
                mFakeClock);

        when(mMockSessionManager.canAcquireDvrSession(r.getChannel().getInputId(), r.getChannel()))
                .thenReturn(false);
        task.run();
        Recording updatedRecording = mDataManager.getRecording(r.getId());
        assertEquals("status",Recording.STATE_RECORDING_FAILED, updatedRecording.getState() );
    }
}
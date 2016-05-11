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
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.test.filters.SdkSuppress;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.tv.common.recording.TvRecording;
import com.android.tv.data.Channel;
import com.android.tv.dvr.RecordingTask.State;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link RecordingTask}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = 23)
public class RecordingTaskTest extends AndroidTestCase {
    private static final long DURATION = TimeUnit.MINUTES.toMillis(30);
    private static final long START_OFFSET = Scheduler.MS_TO_WAKE_BEFORE_START;

    private FakeClock mFakeClock;
    private DvrDataManagerInMemoryImpl mDataManager;
    @Mock
    Handler mMockHandler;
    @Mock
    DvrSessionManager mMockSessionManager;
    @Mock
    TvRecording.TvRecordingClient mMockTvRecordingClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        MockitoAnnotations.initMocks(this);
        mFakeClock = FakeClock.createWithCurrentTime();
        mDataManager = new DvrDataManagerInMemoryImpl(getContext());
    }


    public void testHandle_init() {
        Recording r = createRecording();
        RecordingTask task = createRecordingTask(r);
        Channel channel = r.getChannel();
        String inputId = channel.getInputId();
        when(mMockSessionManager.canAcquireDvrSession(inputId, channel)).thenReturn(true);
        when(mMockSessionManager.acquireDvrSession(inputId, channel))
                .thenReturn(mMockTvRecordingClient);
        when(mMockHandler.sendEmptyMessageDelayed(anyInt(), anyLong())).thenReturn(true);

        long delay = START_OFFSET - RecordingTask.MS_BEFORE_START;
        long uptime = SystemClock.uptimeMillis();
        assertTrue(task.handleMessage(createMessage(RecordingTask.MESSAGE_INIT)));

        assertEquals(State.CONNECTION_PENDING, task.getState());
        verify(mMockSessionManager).canAcquireDvrSession(inputId, channel);
        verify(mMockSessionManager).acquireDvrSession(inputId, channel);
        verify(mMockTvRecordingClient).connect(eq(inputId), any(TvRecording.ClientCallback.class));

        verifySendMessageAt(RecordingTask.MESSAGE_START_RECORDING, uptime + delay);
        verifyNoMoreInteractions(mMockHandler, mMockTvRecordingClient, mMockSessionManager);
    }


    public void testHandle_init_cannotAcquireSession() {
        Recording r = createRecording();
        r = mDataManager.addRecordingInternal(r);
        RecordingTask task = createRecordingTask(r);

        when(mMockSessionManager.canAcquireDvrSession(r.getChannel().getInputId(), r.getChannel()))
                .thenReturn(false);

        assertTrue(task.handleMessage(createMessage(RecordingTask.MESSAGE_INIT)));

        assertEquals(State.ERROR, task.getState());
        verifySendMessage(Scheduler.HandlerWrapper.MESSAGE_REMOVE);
        Recording updatedRecording = mDataManager.getRecording(r.getId());
        assertEquals("status", Recording.STATE_RECORDING_FAILED, updatedRecording.getState());
    }

    public void testOnConnected() {
        Recording r = createRecording();
        mDataManager.addRecording(r);
        RecordingTask task = createRecordingTask(r);

        task.onConnected();

        assertEquals(State.CONNECTED, task.getState());
    }

    private Recording createRecording() {
        long startTime = mFakeClock.currentTimeMillis() + START_OFFSET;
        long endTime = startTime + DURATION;
        return RecordingTestUtils.createTestRecordingWithPeriod(startTime, endTime);
    }

    private RecordingTask createRecordingTask(Recording r) {
        RecordingTask recordingTask = new RecordingTask(r, mMockSessionManager, mDataManager,
                mFakeClock);
        recordingTask.setHandler(mMockHandler);
        return recordingTask;
    }

    private void verifySendMessage(int what) {
        verify(mMockHandler).sendMessageAtTime(argThat(messageMatchesWhat(what)), anyLong());
    }

    private void verifySendMessageAt(int what, long when) {
        verify(mMockHandler).sendMessageAtTime(argThat(messageMatchesWhat(what)), delta(when, 100));
    }

    private static long delta(final long value, final long delta) {
        return longThat(new BaseMatcher<Long>() {
            @Override
            public boolean matches(Object item) {
                Long other = (Long) item;
                return other >= value - delta && other <= value + delta;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("eq " + value + "±" + delta);

            }
        });
    }

    private Message createMessage(int what) {
        Message msg = new Message();
        msg.setTarget(mMockHandler);
        msg.what = what;
        return msg;
    }

    public static ArgumentMatcher<Message> messageMatchesWhat(final int what) {
        return new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Object argument) {
                Message message = (Message) argument;
                return message.what == what;
            }
        };
    }
}
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

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.tv.common.recording.TvRecording;
import com.android.tv.data.Channel;
import com.android.tv.util.Clock;
import com.android.tv.util.SoftPreconditions;
import com.android.tv.util.Utils;

import java.util.concurrent.TimeUnit;

/**
 * A Handler that actually starts and stop a recording at the right time.
 *
 * <p>This is run on the looper of thread named {@value DvrRecordingService#HANDLER_THREAD_NAME}.
 * There is only one looper so messages must be handled quickly or start a separate thread.
 */
@WorkerThread
class RecordingTask extends TvRecording.ClientCallback implements Handler.Callback {
    private static final String TAG = "RecordingTask";
    private static final boolean DEBUG = true;  //STOPSHIP(DVR)

    @VisibleForTesting
    static final int MESSAGE_INIT = 1;
    @VisibleForTesting
    static final int MESSAGE_START_RECORDING = 2;
    @VisibleForTesting
    static final int MESSAGE_STOP_RECORDING = 3;

    @VisibleForTesting
    static long MS_BEFORE_START = TimeUnit.SECONDS.toMillis(5);
    @VisibleForTesting
    static long MS_AFTER_END = TimeUnit.SECONDS.toMillis(5);

    //STOPSHIP(DVR)  don't use enums.
    @VisibleForTesting
    enum State {
        NOT_STARTED,
        SESSION_ACQUIRED,
        CONNECTION_PENDING,
        CONNECTED,
        RECORDING_START_REQUESTED,
        RECORDING_STARTED,
        ERROR,
        RELEASED,
    }
    private final DvrSessionManager mSessionManager;

    private final WritableDvrDataManager mDataManager;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private TvRecording.TvRecordingClient mSession;
    private Handler mHandler;
    private Recording mRecording;
    private State mState = State.NOT_STARTED;
    private final Clock mClock;

    RecordingTask(Recording recording, DvrSessionManager sessionManager,
            WritableDvrDataManager dataManager, Clock clock) {
        mRecording = recording;
        mSessionManager = sessionManager;
        mDataManager = dataManager;
        mClock = clock;

        if (DEBUG) Log.d(TAG, "created recording task " + mRecording);
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (DEBUG) Log.d(TAG, "handleMessage " + msg);
        SoftPreconditions
                .checkState(msg.what == Scheduler.HandlerWrapper.MESSAGE_REMOVE || mHandler != null,
                        TAG, "Null handler trying to handle " + msg);
        try {
            switch (msg.what) {
                case MESSAGE_INIT:
                    handleInit();
                    break;
                case MESSAGE_START_RECORDING:
                    handleStartRecording();
                    break;
                case MESSAGE_STOP_RECORDING:
                    handleStopRecording();
                    break;
                case Scheduler.HandlerWrapper.MESSAGE_REMOVE:
                    // Clear the handler
                    mHandler = null;
                    release();
                    return false;
                default:
                    SoftPreconditions.checkArgument(false, TAG, "unexpected message type " + msg);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error processing message " + msg + "  for " + mRecording, e);
            failAndQuit();
        }
        return false;
    }

    @Override
    public void onConnected() {
        if (DEBUG) Log.d(TAG, "onConnected");
        super.onConnected();
        mState = State.CONNECTED;
    }

    @Override
    public void onDisconnected() {
        if (DEBUG) Log.d(TAG, "onDisconnected");
        super.onDisconnected();
        //Do nothing
    }

    @Override
    public void onRecordDeleted(Uri mediaUri) {
        if (DEBUG) Log.d(TAG, "onRecordDeleted " + mediaUri);
        super.onRecordDeleted(mediaUri);
        SoftPreconditions.checkState(false, TAG, "unexpected onRecordDeleted");

    }

    @Override
    public void onRecordDeleteFailed(Uri mediaUri, int reason) {
        if (DEBUG) Log.d(TAG, "onRecordDeleteFailed " + mediaUri + ", " + reason);
        super.onRecordDeleteFailed(mediaUri, reason);
        SoftPreconditions.checkState(false, TAG, "unexpected onRecordDeleteFailed");
    }

    @Override
    public void onRecordStarted(Uri mediaUri) {
        if (DEBUG) Log.d(TAG, "onRecordStarted " + mediaUri);
        super.onRecordStarted(mediaUri);
        mState = State.RECORDING_STARTED;
        updateRecording(Recording.buildFrom(mRecording)
                .setState(Recording.STATE_RECORDING_IN_PROGRESS)
                .build());
    }

    @Override
    public void onRecordStopped(Uri mediaUri, @TvRecording.RecordStopReason int reason) {
        if (DEBUG) Log.d(TAG, "onRecordStopped " + mediaUri + " reason " + reason);
        super.onRecordStopped(mediaUri, reason);
        // TODO(dvr) handle success
        switch (reason) {
            default:
                updateRecording(Recording.buildFrom(mRecording)
                        .setState(Recording.STATE_RECORDING_FAILED)
                        .build());
        }
        release();
        sendRemove();
    }

    private void handleInit() {
        //TODO check recording preconditions
        Channel channel = mRecording.getChannel();
        if (channel == null) {
            Log.w(TAG, "Null channel for " + mRecording);
            failAndQuit();
            return;
        }

        String inputId = channel.getInputId();
        if (mSessionManager.canAcquireDvrSession(inputId, channel)) {
            mSession = mSessionManager.acquireDvrSession(inputId, channel);
            mState = State.SESSION_ACQUIRED;
        } else {
            Log.w(TAG, "Unable to acquire a session for " + mRecording);
            failAndQuit();
            return;
        }

        mSession.connect(inputId, this);
        mState = State.CONNECTION_PENDING;

        if (mHandler == null || !sendEmptyMessageAtAbsoluteTime(MESSAGE_START_RECORDING,
                mRecording.getStartTimeMs() - MS_BEFORE_START)) {
            mState = State.ERROR;
            return;
        }
    }

    private void failAndQuit() {
        updateRecordingState(Recording.STATE_RECORDING_FAILED);
        mState = State.ERROR;
        sendRemove();
    }

    private void sendRemove() {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(Scheduler.HandlerWrapper.MESSAGE_REMOVE);
        }
    }

    private void handleStartRecording() {
        if (DEBUG)Log.d(TAG, "handleStartRecording " + mRecording);
        // TODO(DVR) handle errors
        Channel channel = mRecording.getChannel();
        mSession.startRecord(channel.getUri(), getIdAsMediaUri(mRecording));
        mState= State.RECORDING_START_REQUESTED;
        if (mHandler == null || !sendEmptyMessageAtAbsoluteTime(MESSAGE_STOP_RECORDING,
                mRecording.getEndTimeMs() + MS_AFTER_END)) {
            mState = State.ERROR;
            return;
        }
    }

    private void handleStopRecording() {
        if (DEBUG)Log.d(TAG, "handleStopRecording " + mRecording);
        mSession.stopRecord();
        // TODO: once we add an API to notify successful completion of recording,
        // the following parts need to be moved to the listener implementation.
        updateRecording(Recording.buildFrom(mRecording)
                .setState(Recording.STATE_RECORDING_FINISHED).build());
        sendRemove();
    }

    @VisibleForTesting
    State getState() {
        return mState;
    }

    private void release() {
        if (mSession != null) {
            mSession.release();
            mSessionManager.releaseDvrSession(mSession);
        }
    }

    private boolean sendEmptyMessageAtAbsoluteTime(int what, long when) {
        long now = mClock.currentTimeMillis();
        long delay = Math.max(0L, when - now);
        if (DEBUG) {
            Log.d(TAG, "Sending message " + what + " with a delay of " + delay / 1000
                    + " seconds to arrive at " + Utils.toIsoDateTimeString(when));
        }
        return mHandler.sendEmptyMessageDelayed(what, delay);
    }

    private void updateRecordingState(@Recording.RecordingState int state) {
        updateRecording(Recording.buildFrom(mRecording).setState(state).build());
    }

    @VisibleForTesting static Uri getIdAsMediaUri(Recording recording) {
            // TODO define the URI format
            return new Uri.Builder().appendPath(String.valueOf(recording.getId())).build();
    }

    private void updateRecording(Recording updatedRecording) {
        if (DEBUG) Log.d(TAG, "updateRecording " + updatedRecording);
        mRecording = updatedRecording;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataManager.updateRecording(mRecording);
            }
        });
    }

    @Override
    public String toString() {
        return getClass().getName() + "(" + mRecording + ")";
    }
}

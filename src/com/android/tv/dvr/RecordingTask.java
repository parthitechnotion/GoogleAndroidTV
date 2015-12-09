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
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.tv.common.dvr.DvrSessionClient;
import com.android.tv.data.Channel;
import com.android.tv.util.Clock;

import java.util.concurrent.TimeUnit;

/**
 * A runnable that actually starts on stop a recording at the right time.
 */
class RecordingTask extends DvrSessionClient.Callback implements Runnable {
    private static final String TAG = "RecordingTask";
    private static final boolean DEBUG = false;

    @VisibleForTesting
    static long MS_BEFORE_START = TimeUnit.SECONDS.toMillis(5);
    @VisibleForTesting
    static long MS_AFTER_END = TimeUnit.SECONDS.toMillis(5);
    private final DvrSessionManager mSessionManager;
    private final WritableDvrDataManager mDataManager;
    private final Clock mClock;
    private Recording mRecording;

    RecordingTask(Recording recording, DvrSessionManager sessionManager,
            WritableDvrDataManager dataManager, Clock clock) {
        mRecording = recording;
        mSessionManager = sessionManager;
        mDataManager = dataManager;
        mClock = clock;
        if (DEBUG) Log.d(TAG, "created recording task " + mRecording);
    }

    @Override
    public void run() {
        if (DEBUG) Log.d(TAG, "running recording task " + mRecording);

        //TODO check recording preconditions
        Channel channel = mRecording.getChannel();
        String inputId = channel.getInputId();
        DvrSessionClient session;
        if (mSessionManager.canAcquireDvrSession(inputId, channel)) {
            session = mSessionManager.acquireDvrSession(inputId, channel);
        } else {
            Log.w(TAG, "Unable to acquire a session for " + mRecording);
            updateRecordingState(Recording.STATE_RECORDING_FAILED);
            return;
        }
        try {
            session.connect(inputId, this);

            // TODO: use handler instead of sleep to respond to events and interrupts
            mClock.sleep(Math.max(0,
                    (mRecording.getStartTimeMs() - mClock.currentTimeMillis()) - MS_BEFORE_START));
            if (DEBUG) Log.d(TAG, "Start recording " + mRecording);

            session.startRecord(channel.getUri(), getIdAsMediaUri(mRecording));

            mClock.sleep(Math.max(0,
                    (mRecording.getEndTimeMs() - mClock.currentTimeMillis()) + MS_AFTER_END));
            session.stopRecord();
            if (DEBUG) Log.d(TAG, "Finished recording " + mRecording);
        } finally {
            //TODO Don't release until after onRecordStopped etc.
            mSessionManager.releaseDvrSession(session);
        }
    }

    @Override
    public void onRecordStarted(Uri mediaUri) {
        if (DEBUG) Log.d(TAG, "onRecordStarted " + mediaUri);
        super.onRecordStarted(mediaUri);
        updateRecording(Recording.buildFrom(mRecording)
                .setState(Recording.STATE_RECORDING_IN_PROGRESS)
                .setUri(mediaUri)
                .build());
    }

    @Override
    public void onRecordStopped(Uri mediaUri, @DvrSessionClient.RecordStopReason int reason) {
        if (DEBUG) Log.d(TAG, "onRecordStopped " + mediaUri + " reason " + reason);
        super.onRecordStopped(mediaUri, reason);

        //TODO need a success reason.
        switch (reason){
            default:
                updateRecording(Recording.buildFrom(mRecording)
                        .setState(Recording.STATE_RECORDING_FAILED)
                        .build());
       }
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
        mDataManager.updateRecording(mRecording);
    }

    @Override
    public String toString() {
        return getClass().getName() + "(" + mRecording + ")";
    }
}

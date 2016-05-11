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

package com.android.usbtuner.tvinput;

import android.content.Context;
import android.net.Uri;

import com.android.tv.common.recording.RecordingCapability;
import com.android.tv.common.recording.TvRecording;

/**
 * Processes DVR recordings, and deletes the previously recorded contents.
 */
public class RecordingSessionImpl extends TvRecording.RecordingSession implements
        DvrSessionImplInternal.DvrEventListener {
    // TODO: recording request will be handled here
    private final DvrSessionImplInternal mSessionImplInternal;
    private final String mInputId;

    public RecordingSessionImpl(Context context, String inputId,
            ChannelDataManager channelDataManager) {
        super(context);
        mInputId = inputId;
        mSessionImplInternal = new DvrSessionImplInternal(context, inputId, channelDataManager);
        mSessionImplInternal.setDvrEventListener(this);
    }

    @Override
    public void onStopRecord() {
        mSessionImplInternal.stopRecording();
    }

    @Override
    public void onStartRecord(Uri channelUri, Uri mediaUri) {
        mSessionImplInternal.startRecording(channelUri, mediaUri);
    }

    @Override
    public void onDelete(Uri mediaUri) {
        mSessionImplInternal.deleteRecording(mediaUri);
        notifyDeleted(mediaUri);
    }

    @Override
    public RecordingCapability onGetCapability() {
        return mSessionImplInternal.getCapabilities();
    }

    @Override
    public void onRelease() {
    }

    // DvrSessionImplInternal.DvrEventListener
    @Override
    public void onRecordStarted(Uri mediaUri) {
        notifyRecordStarted(mediaUri);
    }

    @Override
    public void onRecordUnexpectedlyStopped(Uri mediaUri, int reason) {
        notifyRecordUnexpectedlyStopped(mediaUri, reason);
    }

    @Override
    public void onDeleted(Uri mediaUri) {
        notifyDeleted(mediaUri);
    }

    @Override
    public void onDeleteFailed(Uri mediaUri, int reason) {
        notifyDeleteFailed(mediaUri, reason);
    }
}

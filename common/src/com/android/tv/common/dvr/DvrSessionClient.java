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

package com.android.tv.common.dvr;

import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A session used for recording.
 */
public class DvrSessionClient {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({RECORD_STOP_REASON_DISKFULL, RECORD_STOP_REASON_CONFLICT,
        RECORD_STOP_REASON_CONNECT_FAILED, RECORD_STOP_REASON_DISCONNECTED,
        RECORD_STOP_REASON_UNKNOWN})
    public @interface RecordStopReason {}
    public static final int RECORD_STOP_REASON_DISKFULL = 1;
    public static final int RECORD_STOP_REASON_CONFLICT = 2;
    public static final int RECORD_STOP_REASON_CONNECT_FAILED = 3;
    public static final int RECORD_STOP_REASON_DISCONNECTED = 4;
    public static final int RECORD_STOP_REASON_UNKNOWN = 10;

    private boolean mRecordStarted;
    private Callback mCallback;
    private TvView mTvView;

    public DvrSessionClient(Context context) {
        mTvView = new TvView(context);
    }

    /**
     * Connects the session to a specific input {@code inputId}.
     */
    public void connect(String inputId, Callback callback) {
        mCallback = callback;
        Bundle bundle = new Bundle();
        bundle.putBoolean(DvrUtils.BUNDLE_IS_DVR, true);
        mTvView.tune(inputId, TvContract.buildChannelUri(0), bundle);
        mTvView.sendAppPrivateCommand(DvrUtils.APP_PRIV_CREATE_DVR_SESSION, null);
        mTvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onConnectionFailed(String inputId) {
                if (mCallback == null) {
                    return;
                }
                mCallback.onDisconnected();
            }

            @Override
            public void onDisconnected(String inputId) {
                if (mCallback == null) {
                    return;
                }
                mCallback.onDisconnected();
            }

            @Override
            public void onEvent(String inputId, String eventType, Bundle eventArgs) {
                if (mCallback == null) {
                    return;
                }
                String mediaUriString = eventArgs == null ? null :
                        eventArgs.getString(DvrUtils.BUNDLE_MEDIA_URI, null);
                Uri mediaUri = mediaUriString == null ? null : Uri.parse(mediaUriString);
                if (DvrUtils.EVENT_TYPE_CONNECTED.equals(eventType)) {
                    mCallback.onConnected();
                } else if (DvrUtils.EVENT_TYPE_RECORD_STARTED.equals(eventType)) {
                    mCallback.onRecordStarted(mediaUri);
                } else if (DvrUtils.EVENT_TYPE_RECORD_STOPPED.equals(eventType)) {
                    int reason = eventArgs.getInt(DvrUtils.BUNDLE_STOPPED_REASON);
                    mCallback.onRecordStopped(mediaUri, reason);
                } else if (DvrUtils.EVENT_TYPE_DELETED.equals(eventType)) {
                    mCallback.onRecordDeleted(mediaUri);
                }
            }

            // TODO: handle track select.
        });
    }

    /**
     * Releases the session.
     */
    public void release() {
        mTvView.reset();
        mCallback = null;
    }

    /**
     * Starts recording.
     */
    public void startRecord(Uri channelUri, Uri mediaUri) {
        if (mRecordStarted) {
            throw new IllegalStateException("Don't reuse the session for simple implementation");
        }
        mRecordStarted = true;
        Bundle params = DvrUtils.buildMediaUri(mediaUri);
        params.putString(DvrUtils.BUNDLE_CHANNEL_URI, channelUri.toString());
        mTvView.sendAppPrivateCommand(DvrUtils.APP_PRIV_START_RECORD, params);
    }

    /**
     * Stops recording.
     */
    public void stopRecord() {
        if (!mRecordStarted) {
            return;
        }
        mRecordStarted = false;
        mTvView.sendAppPrivateCommand(DvrUtils.APP_PRIV_STOP_RECORD, null);
    }

    /**
     * Deletes a recorded media.
     */
    public void delete(Uri mediaUri) {
        mTvView.sendAppPrivateCommand(DvrUtils.APP_PRIV_DELETE, DvrUtils.buildMediaUri(mediaUri));
    }

    public abstract static class Callback {
        public void onConnected() { }
        public void onDisconnected() { }
        public void onRecordStarted(Uri mediaUri) { }
        public void onRecordStopped(Uri mediaUri, @RecordStopReason int reason) { }
        public void onRecordDeleted(Uri mediaUri) { }
        public void onRecordDeleteFailed(Uri mediaUri, int reason) { }
    }
}

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

package com.android.tv.common.recording;

import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Surface;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * API for making TV Recordings.
 * This class holds both the API under development and the session app private command magic needed
 * to simulate the API.
 */
public final class TvRecording {
    private static final String TAG = "TvRecording";
    private static final boolean DEBUG = true;  // STOPSHIP(DVR)

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({RECORD_STOP_REASON_DISKFULL, RECORD_STOP_REASON_CONFLICT,
            RECORD_STOP_REASON_CONNECT_FAILED, RECORD_STOP_REASON_DISCONNECTED,
            RECORD_STOP_REASON_UNKNOWN})
    public @interface RecordStopReason {
    }

    private static final int FIRST_REASON = 1;
    public static final int RECORD_STOP_REASON_DISKFULL = 1;
    public static final int RECORD_STOP_REASON_CONFLICT = 2;
    public static final int RECORD_STOP_REASON_CONNECT_FAILED = 3;
    public static final int RECORD_STOP_REASON_DISCONNECTED = 4;
    public static final int RECORD_STOP_REASON_UNKNOWN = 5;
    private static final int LAST_REASON = 5;

    public abstract static class ClientCallback {
        public void onConnected() { }

        public void onDisconnected() { }

        public void onRecordStarted(Uri mediaUri) { }

        public void onRecordStopped(Uri mediaUri, @RecordStopReason int reason) { }

        public void onRecordDeleted(Uri mediaUri) { }

        public void onRecordDeleteFailed(Uri mediaUri, int reason) { }

        public void onCapabilityReceived(RecordingCapability capability) { }
    }

    public interface RecordingClientApi {
        void release();

        void startRecord(Uri channelUri, Uri mediaUri);

        void stopRecord();

        void delete(Uri mediaUri);

        void getCapability();
    }

    public interface RecordingSessionApi {
        /**
         * Start recording on {@code channelUri}.
         * <p>{@link RecordingSession#notifyRecordStarted(Uri)} should be called as soon as the
         * recording is started.
         */
        void onStartRecord(Uri channelUri, Uri mediaUri);

        /**
         * Called when it stops to record.
         */
        void onStopRecord();

        /**
         * Called when it is requested to delete {@code mediaUri}.
         */
        void onDelete(Uri mediaUri);

        /**
         * Called when the client request {@link RecordingCapability}.
         */
        RecordingCapability onGetCapability();
    }

    ///////////
    // BELOW IS IMPLEMENTATION DETAILS OFTEN SPECIFIC TO USING APP PRIVATE COMMANDS
    //////////

    private static final String PREFIX = "record_";

    private static final String APP_PRIV_DELETE = PREFIX + "app_priv_delete";
    private static final String APP_PRIV_GET_CAPABILITY = PREFIX + "app_priv_get_capability";
    private static final String APP_PRIV_START_RECORD = PREFIX + "app_priv_start_record";
    private static final String APP_PRIV_STOP_RECORD = PREFIX + "app_priv_stop_record";

    private static final String EVENT_TYPE_DELETED = PREFIX + "event_type_deleted";
    private static final String EVENT_TYPE_DELETE_FAILED = PREFIX + "event_type_delete_failed";
    private static final String EVENT_TYPE_CAPABILITY_RECEIVED = PREFIX
            + "event_type_capability_received";
    private static final String EVENT_TYPE_RECORD_STARTED = PREFIX + "event_type_record_started";
    private static final String EVENT_TYPE_RECORD_STOPPED = PREFIX + "event_type_record_stopped";

    // Type: int
    private static final String BUNDLE_STOPPED_REASON = PREFIX + "stopped_reason";
    // Type: int
    private static final String BUNDLE_DELETE_FAILED_REASON = PREFIX + "delete_failed_reason";
    // Type: RecordingCapability
    private static final String BUNDLE_CAPABILITY = PREFIX + "capability";


    /**
     * Session linked to {@link TvRecordingClient} to record contents.
     */
    public static abstract class RecordingSession extends RecordingTvInputService.BaseSession
            implements RecordingSessionApi {
        private final static String TAG = "RecordingSession";

        public RecordingSession(Context context) {
            super(context);
        }

        @Override
        public final boolean onTune(Uri channelUri) {
            // no-op
            return false;
        }

        @Override
        public final boolean onSetSurface(Surface surface) {
            // no-op
            return false;
        }

        @Override
        public final void onSetStreamVolume(float volume) {
            // no-op
        }

        @Override
        public final void onSetCaptionEnabled(boolean enabled) {
            // no-op
        }

        /**
         * Notifies when recording starts. It is an response of {@link #onStartRecord}.
         */
        public final void notifyRecordStarted(Uri mediaUri) {
            notifySessionEvent(EVENT_TYPE_RECORD_STARTED, RecordingUtils.buildMediaUri(mediaUri));
        }

        /**
         * Notifies when recording is unexpectedly stopped.
         */
        public final void notifyRecordUnexpectedlyStopped(Uri mediaUri, int reason) {
            Bundle params = RecordingUtils.buildMediaUri(mediaUri);
            params.putInt(BUNDLE_STOPPED_REASON, reason);
            notifySessionEvent(EVENT_TYPE_RECORD_STOPPED, params);
        }

        /**
         * Notifies when the recording {@code mediaUri} is deleted.
         */
        public final void notifyDeleted(Uri mediaUri) {
            notifySessionEvent(EVENT_TYPE_DELETED, RecordingUtils.buildMediaUri(mediaUri));
        }

        /**
         * Notifies when the deletion of the recording {@code mediaUri} is requested through
         * {@link #onDelete} but failed.
         */
        public final void notifyDeleteFailed(Uri mediaUri, int reason) {
            Bundle params = RecordingUtils.buildMediaUri(mediaUri);
            params.putInt(BUNDLE_DELETE_FAILED_REASON, reason);
            notifySessionEvent(EVENT_TYPE_DELETE_FAILED, params);
        }

        @Override
        public final void onAppPrivateCommand(String action, Bundle data) {
            if (DEBUG) Log.d(TAG, "onAppPrivateCommand(" + action + ", " + data + ")");
            switch (action) {
                case APP_PRIV_GET_CAPABILITY:
                    RecordingCapability capability = onGetCapability();
                    Bundle params = new Bundle();
                    params.putParcelable(BUNDLE_CAPABILITY, capability);
                    notifySessionEvent(EVENT_TYPE_CAPABILITY_RECEIVED, params);
                    break;
                case APP_PRIV_DELETE:
                    onDelete(Uri.parse(data.getString(RecordingUtils.BUNDLE_CHANNEL_URI)));
                    break;
                case APP_PRIV_START_RECORD:
                    onStartRecord(Uri.parse(data.getString(RecordingUtils.BUNDLE_CHANNEL_URI)),
                            Uri.parse(data.getString(RecordingUtils.BUNDLE_MEDIA_URI)));
                    break;
                case APP_PRIV_STOP_RECORD:
                    onStopRecord();
                    break;
            }
        }
    }

    /**
     * A session used for recording.
     */
    public static class TvRecordingClient implements RecordingClientApi {
        private static final String TAG = "DvrSessionClient";

        private ClientCallback mCallback;
        private TvView mTvView;

        public TvRecordingClient(Context context) {
            if (DEBUG) {
                Log.d(TAG, "creating client");
            }
            mTvView = new TvView(context);
        }

        /**
         * Connects the session to a specific input {@code inputId}.
         */
        public void connect(String inputId, ClientCallback callback) {
            if (DEBUG) {
                Log.d(TAG, "connect " + inputId + " with " + callback);
            }
            mCallback = callback;
            Bundle bundle = new Bundle();
            bundle.putBoolean(RecordingUtils.BUNDLE_IS_DVR, true);
            mTvView.tune(inputId, TvContract.buildChannelUri(0), bundle);
            mTvView.sendAppPrivateCommand(RecordingUtils.APP_PRIV_CREATE_DVR_SESSION, null);
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
                    String mediaUriString = eventArgs == null ? null
                            : eventArgs.getString(RecordingUtils.BUNDLE_MEDIA_URI, null);
                    Uri mediaUri = mediaUriString == null ? null : Uri.parse(mediaUriString);
                    switch (eventType) {
                        case RecordingUtils.EVENT_TYPE_CONNECTED:
                            mCallback.onConnected();
                            break;
                        case EVENT_TYPE_DELETED:
                            mCallback.onRecordDeleted(mediaUri);
                            break;
                        case EVENT_TYPE_DELETE_FAILED: {
                            // TODO(DVR) use reasons from API
                            int reason = eventArgs == null ? 0
                                    : eventArgs.getInt(BUNDLE_DELETE_FAILED_REASON);
                            mCallback.onRecordDeleteFailed(mediaUri, reason);
                            break;
                        }
                        case EVENT_TYPE_CAPABILITY_RECEIVED: {
                            RecordingCapability capability = eventArgs
                                    .getParcelable(BUNDLE_CAPABILITY);
                            mCallback.onCapabilityReceived(capability);
                            break;
                        }
                        case EVENT_TYPE_RECORD_STARTED:
                            mCallback.onRecordStarted(mediaUri);
                            break;
                        case EVENT_TYPE_RECORD_STOPPED: {
                            int reason = getRecordStopReason(eventArgs);
                            mCallback.onRecordStopped(mediaUri, reason);
                            break;
                        }
                    }
                }

                // TODO: handle track select.
            });
        }

        /**
         * Releases the session.
         */
        @Override
        public void release() {
            if (DEBUG) {
                Log.d(TAG, "release " + this);
            }
            mTvView.reset();
            mCallback = null;
        }

        /**
         * Starts recording.
         */
        @Override
        public void startRecord(Uri channelUri, Uri mediaUri) {
            if (DEBUG) {
                Log.d(TAG, "startRecord " + channelUri + ",  " + mediaUri);
            }
            Bundle params = RecordingUtils.buildMediaUri(mediaUri);
            params.putString(RecordingUtils.BUNDLE_CHANNEL_URI, channelUri.toString());
            mTvView.sendAppPrivateCommand(APP_PRIV_START_RECORD, params);
        }

        /**
         * Stops recording.
         */
        @Override
        public void stopRecord() {
            if (DEBUG) {
                Log.d(TAG, "stopRecord " + this);
            }
            mTvView.sendAppPrivateCommand(APP_PRIV_STOP_RECORD, null);
        }

        /**
         * Deletes a recorded media.
         */
        @Override
        public void delete(Uri mediaUri) {
            mTvView.sendAppPrivateCommand(APP_PRIV_DELETE,
                    RecordingUtils.buildMediaUri(mediaUri));
        }

        @Override
        public void getCapability() {
            mTvView.sendAppPrivateCommand(APP_PRIV_GET_CAPABILITY, null);
        }

        @Override
        public String toString() {
            return TvRecordingClient.class.getName() + "{" + "callBack=" + mCallback + "}";
        }
    }

    @SuppressWarnings("ResourceType")
    @RecordStopReason
    private static int getRecordStopReason(Bundle eventArgs) {
        if(eventArgs == null) {
            if (DEBUG) Log.d(TAG, "Null stop reason");
            return RECORD_STOP_REASON_UNKNOWN;
        }
        int reason = eventArgs.getInt(BUNDLE_STOPPED_REASON);
        if (reason < FIRST_REASON || reason > LAST_REASON) {
            if (DEBUG)  Log.d(TAG, "Unknown stop reason " + reason);
            reason = RECORD_STOP_REASON_UNKNOWN;
        }
        return reason;
    }

    private TvRecording() {
    }
}

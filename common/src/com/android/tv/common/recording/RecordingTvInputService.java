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

package com.android.tv.common.recording;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;

import com.android.tv.common.feature.CommonFeatures;

import java.util.List;

/**
 * {@link TvInputService} class that supports recording and playback.
 */
@TargetApi(Build.VERSION_CODES.M) // TODO(DVR): set to N
public abstract class RecordingTvInputService extends TvInputService {
    private static final String TAG = "DvrTvInputService";
    private static final boolean DEBUG = true;

    @Override
    public final Session onCreateSession(String inputId) {
        if (CommonFeatures.DVR.isEnabled(this)) {
            return new InternalSession(this, inputId);
        } else {
            return onCreatePlaybackSession(inputId);
        }
    }

    /**
     * Called when {@link com.android.tv.common.recording.DvrSession#connect} is called.
     */
    protected TvRecording.RecordingSession onCreateDvrSession(String inputId) {
        return null;
    }

    protected abstract PlaybackSession onCreatePlaybackSession(String inputId);

    private class InternalSession extends TvInputService.Session {
        final String mInputId;
        BaseSession mSessionImpl;

        public InternalSession(Context context, String inputId) {
            super(context);
            mInputId = inputId;
        }

        @Override
        public void onRelease() {
            if (mSessionImpl != null) {
                mSessionImpl.onRelease();
            }
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            return mSessionImpl.onSetSurface(surface);
        }

        @Override
        public void onSetStreamVolume(float volume) {
            mSessionImpl.onSetStreamVolume(volume);
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return mSessionImpl.onTune(channelUri);
        }

        @Override
        public void onAppPrivateCommand(String action, Bundle data) {
            if (action.equals(RecordingUtils.APP_PRIV_CREATE_DVR_SESSION)) {
                if (mSessionImpl == null) {
                    mSessionImpl = onCreateDvrSession(mInputId);
                    if (mSessionImpl != null) {
                        mSessionImpl.setPassthroughSession(this);
                        notifySessionEvent(RecordingUtils.EVENT_TYPE_CONNECTED, null);
                    }
                }
            } else if (action.equals(RecordingUtils.APP_PRIV_CREATE_PLAYBACK_SESSION)) {
                if (mSessionImpl == null) {
                    mSessionImpl = onCreatePlaybackSession(mInputId);
                    if (mSessionImpl != null) {
                        mSessionImpl.setPassthroughSession(this);
                    }
                }
            } else {
                if (mSessionImpl == null) {
                    throw new IllegalStateException();
                }
                mSessionImpl.onAppPrivateCommand(action, data);
            }
        }

        @Override
        public android.view.View onCreateOverlayView() {
            return mSessionImpl.onCreateOverlayView();
        }

        @Override
        public boolean onGenericMotionEvent(android.view.MotionEvent event) {
            return mSessionImpl.onGenericMotionEvent(event);
        }

        @Override
        public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
            return mSessionImpl.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyLongPress(int keyCode, android.view.KeyEvent event) {
            return mSessionImpl.onKeyLongPress(keyCode, event);
        }

        @Override
        public boolean onKeyMultiple(int keyCode, int count, android.view.KeyEvent event) {
            return mSessionImpl.onKeyMultiple(keyCode, count, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
            return mSessionImpl.onKeyUp(keyCode, event);
        }

        @Override
        public void onOverlayViewSizeChanged(int width, int height) {
            mSessionImpl.onOverlayViewSizeChanged(width, height);
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            return mSessionImpl.onSelectTrack(type, trackId);
        }

        @Override
        public void onSetMain(boolean isMain) {
            mSessionImpl.onSetMain(isMain);
        }

        @Override
        public void onSurfaceChanged(int format, int width, int height) {
            mSessionImpl.onSurfaceChanged(format, width, height);
        }

        @Override
        public long onTimeShiftGetCurrentPosition() {
            return mSessionImpl.onTimeShiftGetCurrentPosition();
        }

        @Override
        public long onTimeShiftGetStartPosition() {
            return mSessionImpl.onTimeShiftGetStartPosition();
        }

        @Override
        public void onTimeShiftPause() {
            mSessionImpl.onTimeShiftPause();
        }

        @Override
        public void onTimeShiftResume() {
            mSessionImpl.onTimeShiftResume();
        }

        @Override
        public void onTimeShiftSeekTo(long timeMs) {
            mSessionImpl.onTimeShiftSeekTo(timeMs);
        }

        @Override
        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            mSessionImpl.onTimeShiftSetPlaybackParams(params);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return mSessionImpl.onTouchEvent(event);
        }

        @Override
        public boolean onTrackballEvent(MotionEvent event) {
            return mSessionImpl.onTrackballEvent(event);
        }

        @Override
        public boolean onTune(Uri channelUri, Bundle params) {
            return mSessionImpl.onTune(channelUri, params);
        }

        @Override
        public void onUnblockContent(TvContentRating unblockedRating) {
            mSessionImpl.onUnblockContent(unblockedRating);
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            mSessionImpl.onSetCaptionEnabled(enabled);
        }
    }

    /**
     * Base class for {@link PlaybackSession} and {@link TvRecording.RecordingSession}. Do not use it directly
     * outside of this class.
     */
    public static abstract class BaseSession extends TvInputService.Session {
        private Session mPassthroughSession;

        public BaseSession(Context context) {
            super(context);
        }

        private void setPassthroughSession(Session passthroughSession) {
            mPassthroughSession = passthroughSession;
        }

        @Override
        public void setOverlayViewEnabled(boolean enable) {
            if (mPassthroughSession != null) {
                mPassthroughSession.setOverlayViewEnabled(enable);
            } else {
                super.setOverlayViewEnabled(enable);
            }
        }

        @Override
        public void notifyChannelRetuned(Uri channelUri) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyChannelRetuned(channelUri);
            } else {
                super.notifyChannelRetuned(channelUri);
            }
        }

        @Override
        public void notifyContentAllowed() {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyContentAllowed();
            } else {
                super.notifyContentAllowed();
            }
        }

        @Override
        public void notifyContentBlocked(TvContentRating rating) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyContentBlocked(rating);
            } else {
                super.notifyContentBlocked(rating);
            }
        }

        @Override
        public void notifySessionEvent(String eventType, Bundle eventArgs) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifySessionEvent(eventType, eventArgs);
            } else {
                super.notifySessionEvent(eventType, eventArgs);
            }
        }

        @Override
        public void notifyTimeShiftStatusChanged(int status) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyTimeShiftStatusChanged(status);
            } else {
                super.notifyTimeShiftStatusChanged(status);
            }
        }

        @Override
        public void notifyTracksChanged(List<TvTrackInfo> tracks) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyTracksChanged(tracks);
            } else {
                super.notifyTracksChanged(tracks);
            }
        }

        @Override
        public void notifyTrackSelected(int type, String trackId) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyTrackSelected(type, trackId);
            } else {
                super.notifyTrackSelected(type, trackId);
            }
        }

        @Override
        public void notifyVideoAvailable() {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyVideoAvailable();
            } else {
                super.notifyVideoAvailable();
            }
        }

        @Override
        public void notifyVideoUnavailable(int reason) {
            if (mPassthroughSession != null) {
                mPassthroughSession.notifyVideoUnavailable(reason);
            } else {
                super.notifyVideoUnavailable(reason);
            }
        }
    }

    /**
     * Session linked to {@link android.media.tv.TvView} to tune to a channel or play an recording.
     */
    public static abstract class PlaybackSession extends BaseSession {
        private boolean mIsRecordingPlayback;

        public PlaybackSession(Context context) {
            super(context);
        }

        /**
         * Returns {@code true}, if the current playback is for a recording.
         */
        public boolean isRecordingPlayback() {
            return mIsRecordingPlayback;
        }

        /**
         * Called when it is requested to play an recording {@code mediaUri}. When playback and
         * rendering starts, {@link #notifyVideoAvailable} should be called.
         */
        public void onPlayMedia(Uri mediaUri) { }

        /**
         * Notifies TimeShift end position. It should have the form like onTimeShiftEndPosition.
         * But, it's not trivial to add that in the prototyping. The method is recommended to be
         * called inside {@link #onTimeShiftGetStartPosition()}, when a recording is played.
         */
        public void notifyTimeShiftEndPosition(long endPosition) {
            Bundle params = new Bundle();
            params.putLong(RecordingUtils.BUNDLE_TIMESHIFT_END_POSITION, endPosition);
            notifySessionEvent(RecordingUtils.EVENT_TYPE_TIMESHIFT_END_POSITION, params);
        }

        @Override
        public final boolean onTune(Uri channelUri, Bundle params) {
            if (params != null && params.getBoolean(RecordingUtils.BUNDLE_IS_DVR, false)) {
                notifySessionEvent(RecordingUtils.EVENT_TYPE_CONNECTED, null);
                return true;
            } else if (params != null && params.containsKey(RecordingUtils.BUNDLE_MEDIA_URI)) {
                mIsRecordingPlayback = true;
                onPlayMedia(Uri.parse(params.getString(RecordingUtils.BUNDLE_MEDIA_URI)));
                return true;
            } else {
                mIsRecordingPlayback = false;
                return onTune(channelUri);
            }
        }
    }
}

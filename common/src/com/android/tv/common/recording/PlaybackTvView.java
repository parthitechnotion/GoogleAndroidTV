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
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;

import com.android.tv.common.feature.CommonFeatures;

import java.util.List;

/**
 * Extend {@link TvView} to support recording playback.
 */
@TargetApi(Build.VERSION_CODES.M) // TODO(DVR): set to N
public class PlaybackTvView extends TvView {

    final TvInputCallback mInternalCallback = new TvInputCallback() {
        @Override
        public void onChannelRetuned(String inputId, Uri channelUri) {
            if (mCallback != null) {
                mCallback.onChannelRetuned(inputId, channelUri);
            }
        }

        @Override
        public void onConnectionFailed(String inputId) {
            if (mCallback != null) {
                mCallback.onConnectionFailed(inputId);
            }
        }

        @Override
        public void onContentAllowed(String inputId) {
            if (mCallback != null) {
                mCallback.onContentAllowed(inputId);
            }
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            if (mCallback != null) {
                mCallback.onContentBlocked(inputId, rating);
            }
        }

        @Override
        public void onDisconnected(String inputId) {
            if (mCallback != null) {
                mCallback.onDisconnected(inputId);
            }
        }

        @Override
        public void onEvent(String inputId, String eventType, Bundle eventArgs) {
            if (mCallback != null) {
                if (eventType.equals(RecordingUtils.EVENT_TYPE_TIMESHIFT_END_POSITION)) {
                    if (mTimeshiftCallback != null) {
                        mTimeshiftCallback.onTimeShiftEndPositionChanged(inputId,
                                eventArgs.getLong(RecordingUtils.BUNDLE_TIMESHIFT_END_POSITION));
                    }
                    return;
                }
                mCallback.onEvent(inputId, eventType, eventArgs);
            }
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            if (mCallback != null) {
                mCallback.onTimeShiftStatusChanged(inputId, status);
            }
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
            if (mCallback != null) {
                mCallback.onTracksChanged(inputId, tracks);
            }
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            if (mCallback != null) {
                mCallback.onTrackSelected(inputId, type, trackId);
            }
        }

        @Override
        public void onVideoAvailable(String inputId) {
            if (mCallback != null) {
                mCallback.onVideoAvailable(inputId);
            }
        }

        @Override
        public void onVideoSizeChanged(String inputId, int width, int height) {
            if (mCallback != null) {
                mCallback.onVideoSizeChanged(inputId, width, height);
            }
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            if (mCallback != null) {
                mCallback.onVideoUnavailable(inputId, reason);
            }
        }
    };

    private TvInputCallback mCallback;
    private TimeShiftPositionCallback2 mTimeshiftCallback;

    public PlaybackTvView(Context context) {
        this(context, null, 0);
    }

    public PlaybackTvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlaybackTvView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Start playback of recording. Once TvInput is ready to play, onVideoAvailable will be called.
     * Playback control will be done with timeshift method for seek, play, pause.
     */
    public void playMedia(String inputId, Uri mediaUri) {
        tune(inputId, TvContract.buildChannelUri(0), RecordingUtils.buildMediaUri(mediaUri));
    }

    @Override
    public void tune(String inputId, Uri channelUri, Bundle params) {
        super.tune(inputId, channelUri, params);
        if (CommonFeatures.DVR.isEnabled(getContext())) {
            sendAppPrivateCommand(RecordingUtils.APP_PRIV_CREATE_PLAYBACK_SESSION, null);
        }
    }

    public void setTimeShiftPositionCallback(TimeShiftPositionCallback2 callback) {
        if (CommonFeatures.DVR.isEnabled(getContext())) {
            mTimeshiftCallback = callback;
        }
        super.setTimeShiftPositionCallback(callback);
    }

    @Override
    public void setCallback(TvInputCallback callback) {
        if (CommonFeatures.DVR.isEnabled(getContext())) {
            mCallback = callback;
            if (callback == null) {
                super.setCallback(null);
            } else {
                super.setCallback(mInternalCallback);
            }
        } else {
            super.setCallback(callback);
        }
    }

    /**
     * We need end position for recording playback.
     */
    public abstract static class TimeShiftPositionCallback2 extends TimeShiftPositionCallback {
        public void onTimeShiftEndPositionChanged(String inputId, long timeMs) { }
    }
}

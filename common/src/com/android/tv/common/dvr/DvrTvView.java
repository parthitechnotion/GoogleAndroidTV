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
import android.util.AttributeSet;

/**
 * Extend {@link TvView} to support recording playback.
 */
public class DvrTvView extends TvView {

    public DvrTvView(Context context) {
        this(context, null, 0);
    }

    public DvrTvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DvrTvView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Start playback of recording. Once TvInput is ready to play, onVideoAvailable will be called.
     * Playback control will be done with timeshift method for seek, play, pause.
     */
    public void playMedia(String inputId, Uri mediaUri) {
        tune(inputId, TvContract.buildChannelUri(0), DvrUtils.buildMediaUri(mediaUri));
    }

    @Override
    public void tune(String inputId, Uri channelUri, Bundle params) {
        super.tune(inputId, channelUri, params);
        sendAppPrivateCommand(DvrUtils.APP_PRIV_CREATE_PLAYBACK_SESSION, null);
    }

    public void setTimeShiftPositionCallback(TimeShiftPositionCallback2 callback) {
        // TODO: implement
    }

    /**
     * We need end position for recording playback.
     */
    public abstract static class TimeShiftPositionCallback2 extends TimeShiftPositionCallback {
        public void onTimeShiftEndPositionChanged(String inputId, long timeMs) { }
    }
}

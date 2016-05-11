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

import android.net.Uri;
import android.os.Bundle;

public class RecordingUtils {
    static final int ACTION_START_RECORD = 10055;
    static final int ACTION_STOP_RECORD = 10056;

    static final String EVENT_TYPE_CONNECTED = "event_type_connected";
    static final String EVENT_TYPE_TIMESHIFT_END_POSITION = "event_type_timeshift_end_position";

    static final String APP_PRIV_CREATE_PLAYBACK_SESSION = "app_priv_create_playback_session";
    static final String APP_PRIV_CREATE_DVR_SESSION = "app_priv_create_dvr_session";

    // Type: boolean
    static final String BUNDLE_IS_DVR = "bundle_is_dvr";
    // Type: String (Uri)
    static final String BUNDLE_MEDIA_URI = "bundle_media_uri";
    // Type: String
    static final String BUNDLE_CHANNEL_URI = "bundle_channel_uri";
    // Type: long
    static final String BUNDLE_TIMESHIFT_END_POSITION = "timeshift_end_position";

    /**
     * Builds a {@link Bundle} with {@code mediaUri}. If the bundle is sent with tune command,
     * the {@code mediaUri} will be played.
     */
    public static Bundle buildMediaUri(Uri mediaUri) {
        Bundle params = new Bundle();
        params.putString(RecordingUtils.BUNDLE_MEDIA_URI, mediaUri.toString());
        return params;
    }
}

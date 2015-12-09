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

import android.net.Uri;
import android.os.Bundle;

public class DvrUtils {
    static final int ACTION_START_RECORD = 10055;
    static final int ACTION_STOP_RECORD = 10056;

    static final String EVENT_TYPE_CONNECTED = "event_type_connected";
    static final String EVENT_TYPE_RECORD_STARTED = "event_type_record_started";
    static final String EVENT_TYPE_RECORD_STOPPED = "event_type_record_stopped";
    static final String EVENT_TYPE_DELETED = "event_type_deleted";
    static final String EVENT_TYPE_DELETE_FAILED = "event_type_delete_failed";

    static final String APP_PRIV_CREATE_PLAYBACK_SESSION = "app_priv_create_playback_session";
    static final String APP_PRIV_CREATE_DVR_SESSION = "app_priv_create_dvr_session";
    static final String APP_PRIV_START_RECORD = "app_priv_start_record";
    static final String APP_PRIV_STOP_RECORD = "app_priv_stop_record";
    static final String APP_PRIV_DELETE = "app_priv_delete";
    // Type: boolean
    static final String BUNDLE_IS_DVR = "bundle_is_dvr";
    // Type: String (Uri)
    static final String BUNDLE_MEDIA_URI = "bundle_media_uri";
    // Type: String
    static final String BUNDLE_CHANNEL_URI = "bundle_channel_uri";
    // Type: int
    static final String BUNDLE_STOPPED_REASON = "stopped_reason";
    // Type: int
    static final String BUNDLE_DELETE_FAILED_REASON = "delete_failed_reason";

    static Bundle buildMediaUri(Uri mediaUri) {
        Bundle params = new Bundle();
        params.putString(DvrUtils.BUNDLE_MEDIA_URI, mediaUri.toString());
        return params;
    }
}

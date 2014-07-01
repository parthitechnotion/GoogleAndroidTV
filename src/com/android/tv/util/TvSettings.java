/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.util;


/**
 * A class about the constants for TV settings.
 */
public final class TvSettings {
    private TvSettings() {}

    public static final String PREFS_FILE = "settings";
    public static final String PREF_TV_WATCH_LOGGING_ENABLED = "tv_watch_logging_enabled";
    public static final String PREF_DISPLAY_INPUT_NAME = "display_input_name_";
    public static final String PREF_CLOSED_CAPTION_ENABLED = "is_cc_enabled";  // boolean value
    public static final String PREF_DISPLAY_MODE = "display_mode";  // int value
    public static final String PREF_PIP_LOCATION = "pip_location";  // int value

    public static final int PIP_LOCATION_TOP_LEFT = 0;
    public static final int PIP_LOCATION_TOP_RIGHT = 1;
    public static final int PIP_LOCATION_BOTTOM_LEFT = 2;
    public static final int PIP_LOCATION_BOTTOM_RIGHT = 3;
}

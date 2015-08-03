/*
 * Copyright 2015 The Android Open Source Project
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

package com.android.tv.common;

import android.os.Build;

/**
 * Constants for common use in TV app and tests.
 */
public final class TvCommonConstants {
    /**
     * A constant for the key of the extra data for the app linking intent.
     */
    public static final String EXTRA_APP_LINK_CHANNEL_URI = "app_link_channel_uri";

    private static int sPreviewSdkInt;
    static {
        try {
            sPreviewSdkInt = Build.VERSION.PREVIEW_SDK_INT;
        } catch (java.lang.NoSuchFieldError e) {
            sPreviewSdkInt = 0;
        }
    }

    /**
     * A flag whether this platform is MNC Preview or not.
     */
    public static final boolean IS_MNC_PREVIEW =
            Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1 && sPreviewSdkInt > 0;

    /**
     * A flag whether this platform is after MNC Preview or not.
     */
    public static final boolean IS_MNC_OR_HIGHER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.MNC;

    /**
     * A flag whether this platform supports time shifting API or not.
     * TODO: Stop supporting mnc preview if possible.
     */
    public static final boolean HAS_TIME_SHIFT_API = IS_MNC_PREVIEW || IS_MNC_OR_HIGHER;

    private TvCommonConstants() {
    }
}

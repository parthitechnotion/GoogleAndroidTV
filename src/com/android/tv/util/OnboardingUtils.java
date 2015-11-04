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

package com.android.tv.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract.Channels;
import android.preference.PreferenceManager;
import android.support.annotation.UiThread;

import com.android.tv.TvApplication;
import com.android.tv.data.ChannelDataManager;

/**
 * A utility class related to onboarding experience.
 */
public final class OnboardingUtils {
    private static final String PREF_KEY_IS_FIRST_BOOT = "pref_onbaording_is_first_boot";
    private static final String PREF_KEY_IS_FIRST_RUN = "pref_onbaording_is_first_run";
    private static final String PREF_KEY_ARE_CHANNELS_AVAILABLE =
            "pref_onbaording_are_channels_available";

    /**
     * Checks if this is the first boot after the onboarding experience has been applied.
     */
    public static boolean isFirstBoot(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_KEY_IS_FIRST_BOOT, true);
    }

    /**
     * Marks that the first boot has been completed.
     */
    public static void setFirstBootCompleted(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(PREF_KEY_IS_FIRST_BOOT, false)
                .apply();
    }

    /**
     * Checks if this is the first run of {@link com.android.tv.MainActivity} after the
     * onboarding experience has been applied.
     */
    public static boolean isFirstRun(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_KEY_IS_FIRST_RUN, true);
    }

    /**
     * Marks that the first run of {@link com.android.tv.MainActivity} has been completed.
     */
    public static void setFirstRunCompleted(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(PREF_KEY_IS_FIRST_RUN, false)
                .apply();
    }

    /**
     * Checks whether the onboarding screen should be shown or not.
     */
    public static boolean needToShowOnboarding(Context context) {
        return isFirstRun(context) || !areChannelsAvailable(context);
    }

    /**
     * Checks if there are any available tuner channels.
     */
    @UiThread
    public static boolean areChannelsAvailable(Context context) {
        ChannelDataManager manager = ((TvApplication) context.getApplicationContext())
                .getChannelDataManager();
        if (manager.isDbLoadFinished()) {
            return manager.getChannelCount() != 0;
        }
        // This method should block the UI thread.
        ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(Channels.CONTENT_URI, new String[] {Channels._ID}, null,
                null, null)) {
            return c.getCount() != 0;
        }
    }
}

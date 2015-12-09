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

package com.android.usbtuner;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.android.usbtuner.UsbTunerPreferenceProvider.Preferences;

/**
 * A helper class for the USB tuner preferences.
 */
public class UsbTunerPreferences {
    private static final String PREFS_KEY_CHANNEL_DATA_VERSION = "channel_data_version";
    private static final String PREFS_KEY_SCANNED_CHANNEL_COUNT = "scanned_channel_count";
    private static final String PREFS_KEY_SCAN_DONE = "scan_done";
    private static final String PREFS_KEY_LAUNCH_SETUP = "launch_setup";

    public static int getChannelDataVersion(Context context) {
        return getPreferenceInt(context, PREFS_KEY_CHANNEL_DATA_VERSION);
    }

    public static void setChannelDataVersion(Context context, int version) {
        setPreference(context, PREFS_KEY_CHANNEL_DATA_VERSION, version);
    }

    public static int getScannedChannelCount(Context context) {
        return getPreferenceInt(context, PREFS_KEY_SCANNED_CHANNEL_COUNT);
    }

    public static void setScannedChannelCount(Context context, int channelCount) {
        setPreference(context, PREFS_KEY_SCANNED_CHANNEL_COUNT, channelCount);
    }

    public static boolean isScanDone(Context context) {
        return getPreferenceBoolean(context, PREFS_KEY_SCAN_DONE);
    }

    public static void setScanDone(Context context) {
        setPreference(context, PREFS_KEY_SCAN_DONE, true);
    }

    public static boolean shouldShowSetupActivity(Context context) {
        return getPreferenceBoolean(context, PREFS_KEY_LAUNCH_SETUP);
    }

    public static void setShouldShowSetupActivity(Context context, boolean need) {
        setPreference(context, PREFS_KEY_LAUNCH_SETUP, need);
    }

    // Content provider helpers
    private static String getPreference(Context context, String key) {
        ContentResolver resolver = context.getContentResolver();
        String[] projection = new String[] { Preferences.COLUMN_VALUE };
        String selection = Preferences.COLUMN_KEY + " like ?";
        String[] selectionArgs = new String[] { key };
        try (Cursor cursor = resolver.query(UsbTunerPreferenceProvider.buildPreferenceUri(key),
                projection, selection, selectionArgs, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    private static int getPreferenceInt(Context context, String key) {
        try {
            return Integer.parseInt(getPreference(context, key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean getPreferenceBoolean(Context context, String key) {
        return Boolean.valueOf(getPreference(context, key));
    }

    private static void setPreference(Context context, String key, String value) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Preferences.COLUMN_KEY, key);
        values.put(Preferences.COLUMN_VALUE, value);
        resolver.insert(Preferences.CONTENT_URI, values);
    }

    private static void setPreference(Context context, String key, int value) {
        setPreference(context, key, Integer.toString(value));
    }

    private static void setPreference(Context context, String key, boolean value) {
        setPreference(context, key, Boolean.toString(value));
    }
}

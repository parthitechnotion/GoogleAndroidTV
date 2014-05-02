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

package com.android.tv;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.TvContract;
import android.text.TextUtils;
import android.util.Base64;

/**
 * A class that includes convenience methods for accessing TvProvider database.
 */
public class TvInputUtils {

    public static final String SERVICE_INTERFACE = "android.tv.TvInputService";
    public static final String ACTION_SETTINGS = "android.tv.SettingsActivity";
    public static final String ACTION_SETUP = "android.tv.SetupActivity";
    public static final String EXTRA_SERVICE_NAME = "serviceName";

    // preferences stored in the default preference.
    private static final String PREF_KEY_LAST_SELECTED_TV_INPUT = "last_selected_tv_input";

    private static final String PREFIX_PREF_NAME = "com.android.tv.";
    // preferences stored in the preference of a specific tv input.
    private static final String PREF_KEY_LAST_WATCHED_CHANNEL = "last_watched_channel";

    public static ComponentName getInputNameForChannel(Context context, long channelId) {
        if (channelId == Channel.INVALID_ID) {
            return null;
        }
        Uri channelUri = ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId);
        return getInputNameForChannel(context, channelUri);
    }

    public static ComponentName getInputNameForChannel(Context context, Uri channelUri) {
        String[] projection = { TvContract.Channels.PACKAGE_NAME,
                TvContract.Channels.SERVICE_NAME };
        if (channelUri == null) {
            return null;
        }
        Cursor cursor = context.getContentResolver().query(
                channelUri, projection, null, null, null);
        if (cursor == null) {
            return null;
        }
        if (cursor.getCount() < 1) {
            cursor.close();
            return null;
        }
        cursor.moveToNext();
        ComponentName componentName = new ComponentName(cursor.getString(0), cursor.getString(1));
        cursor.close();
        return componentName;
    }

    public static void setLastWatchedChannel(Context context, String inputId, Uri channelUri) {
        if (TextUtils.isEmpty(inputId)) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        context.getSharedPreferences(getPreferenceName(inputId), Context.MODE_PRIVATE).edit()
                .putString(PREF_KEY_LAST_WATCHED_CHANNEL, channelUri.toString()).apply();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LAST_SELECTED_TV_INPUT, inputId).apply();
    }

    public static Uri getLastWatchedChannel(Context context) {
        String inputId = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_LAST_SELECTED_TV_INPUT, null);
        if (inputId == null) {
            return null;
        }
        return getLastWatchedChannel(context, inputId);
    }

    public static long getLastWatchedChannelId(Context context) {
        return getChannelId(getLastWatchedChannel(context));
    }

    public static Uri getLastWatchedChannel(Context context, String inputId) {
        if (TextUtils.isEmpty(inputId)) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        String channel = context.getSharedPreferences(getPreferenceName(inputId),
                Context.MODE_PRIVATE).getString(PREF_KEY_LAST_WATCHED_CHANNEL, null);
        return channel == null ? null : Uri.parse(channel);
    }

    public static long getLastWatchedChannelId(Context context, String inputId) {
        return getChannelId(getLastWatchedChannel(context, inputId));
    }

    public static Program getCurrentProgram(Context context, Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        long time = System.currentTimeMillis();
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri, time, time);
        String[] projection = { TvContract.Programs.TITLE };
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        String title = null;
        if (cursor.moveToNext()) {
            title = cursor.getString(0);
        }
        cursor.close();

        // TODO: Consider providing the entire data if needed.
        return new Program.Builder().setTitle(title).build();
    }

    private static long getChannelId(Uri channelUri) {
        if (channelUri == null) {
            return Channel.INVALID_ID;
        }
        return ContentUris.parseId(channelUri);
    }

    private static String getPreferenceName(String inputId) {
        return PREFIX_PREF_NAME + Base64.encodeToString(inputId.getBytes(), Base64.URL_SAFE);
    }
}

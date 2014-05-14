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

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.TvContract;
import android.text.TextUtils;
import android.tv.TvInputInfo;
import android.util.Base64;

import java.util.List;

/**
 * A class that includes convenience methods for accessing TvProvider database.
 */
public class Utils {

    public static final String SERVICE_INTERFACE = "android.tv.TvInputService";
    public static final String ACTION_SETTINGS = "android.tv.SettingsActivity";
    public static final String ACTION_SETUP = "android.tv.SetupActivity";
    public static final String EXTRA_SERVICE_NAME = "serviceName";

    public static final String CHANNEL_SORT_ORDER_BY_INPUT_NAME =
            TvContract.Channels.COLUMN_PACKAGE_NAME + ", "
            + TvContract.Channels.COLUMN_SERVICE_NAME;

    public static final String CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER =
            "CAST(" + TvContract.Channels.COLUMN_DISPLAY_NUMBER + " AS INTEGER), "
            + "CAST(SUBSTR(LTRIM(" + TvContract.Channels.COLUMN_DISPLAY_NUMBER
            + ",'0123456789'),2) AS INTEGER)";

    // preferences stored in the default preference.
    private static final String PREF_KEY_LAST_SELECTED_TV_INPUT = "last_selected_tv_input";

    private static final String PREFIX_PREF_NAME = "com.android.tv.";
    // preferences stored in the preference of a specific tv input.
    private static final String PREF_KEY_LAST_WATCHED_CHANNEL = "last_watched_channel";

    // TODO: Remove this and add inputId into TvProvider.
    public static String getInputIdForComponentName(ComponentName name) {
        return name.flattenToShortString();
    }

    public static String getInputIdForChannel(Context context, long channelId) {
        if (channelId == Channel.INVALID_ID) {
            return null;
        }
        Uri channelUri = ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId);
        return getInputIdForChannel(context, channelUri);
    }

    public static String getInputIdForChannel(Context context, Uri channelUri) {
        String[] projection = { TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_SERVICE_NAME };
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
        return getInputIdForComponentName(componentName);
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
        String[] projection = {
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS };
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        String title = null;
        long startTime = -1;
        long endTime = -1;
        if (cursor.moveToNext()) {
            title = cursor.getString(0);
            startTime = cursor.getLong(1);
            endTime = cursor.getLong(2);
        }
        cursor.close();

        // TODO: Consider providing the entire data if needed.
        return new Program.Builder()
                .setTitle(title)
                .setStartTimeUtcMillis(startTime)
                .setEndTimeUtcMillis(endTime).build();
    }

    public static boolean hasChannel(Context context, TvInputInfo name) {
        return hasChannel(context, name, true);
    }

    public static boolean hasChannel(Context context, TvInputInfo name, boolean browsableOnly) {
        Uri uri = TvContract.buildChannelsUriForInput(name.getComponent(), browsableOnly);
        String[] projection = { TvContract.Channels._ID };
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static String getDisplayNameForInput(Context context, TvInputInfo info) {
        SharedPreferences preferences = context.getSharedPreferences(TvSettings.PREFS_FILE,
                Context.MODE_PRIVATE);
        PackageManager pm = context.getPackageManager();
        return preferences.getString(TvSettings.PREF_DISPLAY_INPUT_NAME + info.getId(),
                info.loadLabel(pm).toString());
    }

    public static boolean hasActivity(Context context, TvInputInfo input, String action) {
        return getActivityInfo(context, input, action) != null;
    }

    public static boolean startActivity(Context context, TvInputInfo input, String action) {
        ActivityInfo activityInfo = getActivityInfo(context, input, action);
        if (activityInfo == null) {
            return false;
        }

        Intent intent = new Intent(action);
        intent.setClassName(activityInfo.packageName, activityInfo.name);
        intent.putExtra(Utils.EXTRA_SERVICE_NAME, input.getServiceName());
        context.startActivity(intent);
        return true;
    }

    public static boolean startActivityForResult(Activity activity, TvInputInfo input,
            String action, int requestCode) {
        ActivityInfo activityInfo = getActivityInfo(activity, input, action);
        if (activityInfo == null) {
            return false;
        }

        Intent intent = new Intent(Utils.ACTION_SETUP);
        intent.setClassName(activityInfo.packageName, activityInfo.name);
        activity.startActivityForResult(intent, requestCode);
        return true;
    }

    private static ActivityInfo getActivityInfo(Context context, TvInputInfo input, String action) {
        if (input == null) {
            return null;
        }

        List<ResolveInfo> infos = context.getPackageManager().queryIntentActivities(
                new Intent(action), PackageManager.GET_ACTIVITIES);
        if (infos == null) {
            return null;
        }

        for (ResolveInfo info : infos) {
            if (info.activityInfo.packageName.equals(input.getPackageName())) {
                return info.activityInfo;
            }
        }
        return null;
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

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

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Base64;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.data.StreamInfo;

import java.util.List;

/**
 * A class that includes convenience methods for accessing TvProvider database.
 */
public class Utils {
    public static final String SERVICE_INTERFACE = "android.media.tv.TvInputService";
    public static final String EXTRA_SERVICE_NAME = "serviceName";
    public static final String EXTRA_KEYCODE = "keycode";

    public static final String CHANNEL_SORT_ORDER_BY_INPUT_NAME =
            TvContract.Channels.COLUMN_PACKAGE_NAME + ", "
            + TvContract.Channels.COLUMN_SERVICE_NAME;

    public static final String CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER =
            "CAST(" + TvContract.Channels.COLUMN_DISPLAY_NUMBER + " AS INTEGER), "
            + "CAST(SUBSTR(LTRIM(" + TvContract.Channels.COLUMN_DISPLAY_NUMBER
            + ",'0123456789'),2) AS INTEGER)";

    // preferences stored in the default preference.
    private static final String PREF_KEY_LAST_SELECTED_TV_INPUT = "last_selected_tv_input";
    private static final String PREF_KEY_LAST_SELECTED_PHYS_TV_INPUT =
            "last_selected_phys_tv_input";

    private static final String PREFIX_PREF_NAME = "com.android.tv.";
    // preferences stored in the preference of a specific tv input.
    private static final String PREF_KEY_LAST_WATCHED_CHANNEL_ID = "last_watched_channel_id";

    // STOPSHIP: Use the one defined in the contract class instead.
    private static final String TvContract_Programs_COLUMN_VIDEO_RESOLUTION = "video_resolution";

    private static int VIDEO_SD_WIDTH = 704;
    private static int VIDEO_SD_HEIGHT = 480;
    private static int VIDEO_HD_WIDTH = 1280;
    private static int VIDEO_HD_HEIGHT = 720;
    private static int VIDEO_FULL_HD_WIDTH = 1920;
    private static int VIDEO_FULL_HD_HEIGHT = 1080;
    private static int VIDEO_ULTRA_HD_WIDTH = 2048;
    private static int VIDEO_ULTRA_HD_HEIGHT = 1536;

    private enum AspectRatio {
        ASPECT_RATIO_4_3(4, 3),
        ASPECT_RATIO_16_9(16, 9),
        ASPECT_RATIO_21_9(21, 9);

        final int width;
        final int height;

        AspectRatio(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("%d:%d", width, height);
        }
    }

    private Utils() { /* cannot be instantiated */ }

    // TODO: Remove this and add inputId into TvProvider.
    public static String getInputIdForComponentName(ComponentName name) {
        return name.flattenToShortString();
    }

    public static Uri getChannelUri(long channelId) {
        return ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId);
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

    public static void setLastWatchedChannelId(Context context, String inputId, String physInputId,
            long channelId) {
        if (TextUtils.isEmpty(inputId)) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        context.getSharedPreferences(getPreferenceName(inputId), Context.MODE_PRIVATE).edit()
                .putLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID, channelId).apply();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LAST_SELECTED_TV_INPUT, inputId).apply();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LAST_SELECTED_PHYS_TV_INPUT, physInputId).apply();
    }

    public static long getLastWatchedChannelId(Context context) {
        String inputId = getLastSelectedInputId(context);
        if (inputId == null) {
            return Channel.INVALID_ID;
        }
        return getLastWatchedChannelId(context, inputId);
    }

    public static long getLastWatchedChannelId(Context context, String inputId) {
        if (TextUtils.isEmpty(inputId)) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        return context.getSharedPreferences(getPreferenceName(inputId),
                Context.MODE_PRIVATE).getLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID, Channel.INVALID_ID);
    }

    public static String getLastSelectedInputId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_LAST_SELECTED_TV_INPUT, null);
    }

    public static String getLastSelectedPhysInputId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_LAST_SELECTED_PHYS_TV_INPUT, null);
    }

    public static Program getCurrentProgram(Context context, Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        long time = System.currentTimeMillis();
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri, time, time);
        String[] projection = {
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
                TvContract_Programs_COLUMN_VIDEO_RESOLUTION,
                TvContract.Programs.COLUMN_POSTER_ART_URI,
                TvContract.Programs.COLUMN_THUMBNAIL_URI };
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        String title = null;
        String description = null;
        long startTime = -1;
        long endTime = -1;
        String posterArtUri = null;
        String thumbnailUri = null;
        String videoDefinitionLevel = "";
        if (cursor.moveToNext()) {
            title = cursor.getString(0);
            description = cursor.getString(1);
            startTime = cursor.getLong(2);
            endTime = cursor.getLong(3);
            videoDefinitionLevel = cursor.getString(4);
            posterArtUri = cursor.getString(5);
            thumbnailUri = cursor.getString(6);
        }
        cursor.close();

        // TODO: Consider providing the entire data if needed.
        return new Program.Builder()
                .setTitle(title)
                .setDescription(description)
                .setStartTimeUtcMillis(startTime)
                .setEndTimeUtcMillis(endTime)
                .setVideoDefinitionLevel(videoDefinitionLevel)
                .setPosterArtUri(posterArtUri)
                .setThumbnailUri(thumbnailUri).build();
    }

    public static void updateCurrentVideoResolution(Context context, Long channelId, int format) {
        if (channelId == Channel.INVALID_ID) {
            return;
        }
        Uri channelUri = TvContract.buildChannelUri(channelId);
        long time = System.currentTimeMillis();
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri, time, time);
        Cursor cursor = null;
        String[] projection = { TvContract.Programs._ID };
        long programId = Program.INVALID_ID;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor == null || !cursor.moveToNext()) {
                return;
            }
            programId = cursor.getLong(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String videoResolution = getVideoDefinitionLevelString(format);
        if (TextUtils.isEmpty(videoResolution)) {
            return;
        }
        Uri programUri = TvContract.buildProgramUri(programId);
        ContentValues values = new ContentValues();
        values.put(TvContract_Programs_COLUMN_VIDEO_RESOLUTION, videoResolution);
        context.getContentResolver().update(programUri, values, null, null);
    }

    public static boolean hasChannel(Context context, TvInputInfo name) {
        return hasChannel(context, name, true);
    }

    public static boolean hasChannel(Context context, TvInputInfo name, boolean browsableOnly) {
        ServiceInfo info = name.getServiceInfo();
        ComponentName componentName = new ComponentName(info.packageName, info.name);
        Uri uri = TvContract.buildChannelsUriForInput(componentName, browsableOnly);
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

    public static SharedPreferences getSharedPreferencesOfDisplayNameForInput(Context context) {
        return context.getSharedPreferences(TvSettings.PREFS_FILE, Context.MODE_PRIVATE);
    }

    public static String getDisplayNameForInput(Context context, TvInputInfo info) {
        SharedPreferences preferences = getSharedPreferencesOfDisplayNameForInput(context);
        PackageManager pm = context.getPackageManager();
        return preferences.getString(TvSettings.PREF_DISPLAY_INPUT_NAME + info.getId(),
                info.loadLabel(pm).toString());
    }

    public static boolean hasActivity(Context context, TvInputInfo input, String action) {
        return getActivityInfo(context, input, action) != null;
    }

    public static String getAspectRatioString(int width, int height) {
        if (width == 0 || height == 0) {
            return "";
        }

        for (AspectRatio ratio: AspectRatio.values()) {
            if (Math.abs((float) ratio.height / ratio.width - (float) height / width) < 0.05f) {
                return ratio.toString();
            }
        }
        return "";
    }

    public static int getVideoDefinitionLevelFromSize(int width, int height) {
        if (width >= VIDEO_ULTRA_HD_WIDTH && height >= VIDEO_ULTRA_HD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD;
        } else if (width >= VIDEO_FULL_HD_WIDTH && height >= VIDEO_FULL_HD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD;
        } else if (width >= VIDEO_HD_WIDTH && height >= VIDEO_HD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_HD;
        } else if (width >= VIDEO_SD_WIDTH && height >= VIDEO_SD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_SD;
        }
        return StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
    }

    public static String getVideoDefinitionLevelString(int videoFormat) {
        switch (videoFormat) {
            case StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD:
                return "Ultra HD";
            case StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD:
                return "Full HD";
            case StreamInfo.VIDEO_DEFINITION_LEVEL_HD:
                return "HD";
            case StreamInfo.VIDEO_DEFINITION_LEVEL_SD:
                return "SD";
        }
        return "";
    }

    public static String getAudioChannelString(int channelCount) {
        switch (channelCount) {
            case 1:
                return "MONO";
            case 2:
                return "STEREO";
            case 6:
                return "5.1";
            case 8:
                return "7.1";
        }
        return "";
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
            if (info.activityInfo.packageName.equals(input.getServiceInfo().packageName)) {
                return info.activityInfo;
            }
        }
        return null;
    }

    private static String getPreferenceName(String inputId) {
        return PREFIX_PREF_NAME + Base64.encodeToString(inputId.getBytes(), Base64.URL_SAFE);
    }
}

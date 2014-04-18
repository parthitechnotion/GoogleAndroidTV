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
import android.provider.TvContract;

/**
 * A class that includes convenience methods for accessing TvProvider database.
 */
public class TvInputUtils {

    public static final String SERVICE_INTERFACE = "android.tv.TvInputService";
    public static final String ACTION_SETTINGS = "android.tv.SettingsActivity";
    public static final String ACTION_SETUP = "android.tv.SetupActivity";
    public static final String EXTRA_SERVICE_NAME = "serviceName";

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

    public static Channel getLastWatchedChannel(Context context) {
        return getLastWatchedChannel(context, null);
    }

    public static void setLastWatchedChannel(Context context, Uri channelUri) {
        // TODO: implement this
    }

    public static Channel getLastWatchedChannel(Context context, ComponentName inputName) {
        // TODO: implement this
        return null;
    }

    public static Program getCurrentProgram(Context context, Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        String[] projection = { TvContract.Programs.TITLE };
        String selection = TvContract.Programs.CHANNEL_ID + " = ? AND "
                + TvContract.Programs.START_TIME_UTC_MILLIS + " <= ? AND "
                + TvContract.Programs.END_TIME_UTC_MILLIS + " > ?";
        String channelId = String.valueOf(ContentUris.parseId(channelUri));
        String currentTime = String.valueOf(System.currentTimeMillis());
        String[] selectionArgs = { channelId, currentTime, currentTime };
        Cursor cursor = context.getContentResolver().query(TvContract.Programs.CONTENT_URI,
                projection, selection, selectionArgs, null);
        String title = null;
        if (cursor.moveToNext()) {
            title = cursor.getString(0);
        }
        cursor.close();

        // TODO: Consider providing the entire data if needed.
        return new Program.Builder().setTitle(title).build();
    }
}

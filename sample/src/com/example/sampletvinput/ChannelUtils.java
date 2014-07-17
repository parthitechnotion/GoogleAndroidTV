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

package com.example.sampletvinput;

import com.example.sampletvinput.BaseTvInputService.ChannelInfo;

import android.content.ContentValues;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ChannelUtils {
    public static void populateChannels(
            Context context, String inputId, List<ChannelInfo> channels) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, inputId);
        Map<Uri, String> logos = new HashMap<Uri, String>();
        for (ChannelInfo channel : channels) {
            values.put(Channels.COLUMN_DISPLAY_NUMBER, channel.mNumber);
            values.put(Channels.COLUMN_DISPLAY_NAME, channel.mName);
            Uri uri = context.getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
            if (!TextUtils.isEmpty(channel.mLogoUrl)) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.mLogoUrl);
            }
        }

        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }
    }

    public static class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
        private final Context mContext;

        InsertLogosTask(Context context) {
            mContext = context;
        }

        @Override
        public Void doInBackground(Map<Uri, String>... logosList) {
            for (Map<Uri, String> logos : logosList) {
                for (Uri uri : logos.keySet()) {
                    // For now, support only local file.
                    Utils.insertFile(mContext, uri, new File(logos.get(uri)));
                }
            }
            return null;
        }
    }

    private ChannelUtils() {}
}

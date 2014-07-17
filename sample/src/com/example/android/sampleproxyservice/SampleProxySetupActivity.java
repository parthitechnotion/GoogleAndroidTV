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

package com.example.android.sampleproxyservice;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;

public class SampleProxySetupActivity extends Activity {
    private static final String TAG = SampleProxySetupActivity.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final String CHANNEL_NUMBER_1 = "1";
    private static final String CHANNEL_NAME_1 = "BIPBOP";
    private static final String CHANNEL_URL_1 =
            "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8";

    private static final String CHANNEL_NUMBER_2 = "2";
    private static final String CHANNEL_NAME_2 = "BUNNY";
    private static final String CHANNEL_URL_2 =
            "http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String inputId = getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        createSampleChannels(inputId);
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void createSampleChannels(String inputId) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = { TvContract.Channels._ID };

        Cursor cursor = null;
        try {
            while (true) {
                cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    return;
                }
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
                ContentValues values = new ContentValues();
                values.put(Channels.COLUMN_INPUT_ID, inputId);

                values.put(Channels.COLUMN_DISPLAY_NUMBER, CHANNEL_NUMBER_1);
                values.put(Channels.COLUMN_DISPLAY_NAME, CHANNEL_NAME_1);
                values.put(Channels.COLUMN_INTERNAL_PROVIDER_DATA, CHANNEL_URL_1);
                getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);

                values.put(Channels.COLUMN_DISPLAY_NUMBER, CHANNEL_NUMBER_2);
                values.put(Channels.COLUMN_DISPLAY_NAME, CHANNEL_NAME_2);
                values.put(Channels.COLUMN_INTERNAL_PROVIDER_DATA, CHANNEL_URL_2);
                getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}

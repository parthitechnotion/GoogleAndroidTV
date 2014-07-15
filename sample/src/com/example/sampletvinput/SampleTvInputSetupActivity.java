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

import android.app.Activity;
import android.content.ComponentName;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The setup activity for demonstrating TvInput app.
 */
public class SampleTvInputSetupActivity extends Activity {
    private static final String TAG = SampleTvInputSetupActivity.class.getSimpleName();
    private static final boolean DEBUG = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String serviceName = getIntent().getStringExtra(TvInputInfo.EXTRA_SERVICE_NAME);
        createSampleChannels(serviceName);
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void createSampleChannels(String serviceName) {
        List<ChannelInfo> channels = null;
        Class clazz = null;

        if (serviceName.equals(LocalTvInputService.class.getName())) {
            channels = LocalTvInputService.createSampleChannelsStatic();
            clazz = LocalTvInputService.class;
        } else if (serviceName.equals(HlsTvInputService.class.getName())) {
            channels = HlsTvInputService.createSampleChannelsStatic(this);
            clazz = HlsTvInputService.class;
        }

        if (channels == null || clazz == null) {
            return;
        }

        Uri uri = TvContract.buildChannelsUriForInput(new ComponentName(this, clazz), false);
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
                }
                if (DEBUG) Log.d(TAG, "Couldn't find the channel list. Inserting new channels...");
                // Insert channels into the database. This needs to be done only for the first time.
                ChannelUtils.populateChannels(this, serviceName, channels);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}

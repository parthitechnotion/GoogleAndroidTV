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

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;

import java.util.List;

/**
 * Demonstrates how to implement a TV input service which represents a external device connected to
 * a hardware TV input.
 */
public class SampleProxyService extends TvInputService {
    private static final String TAG = SampleProxyService.class.getSimpleName();
    private static final boolean DEBUG = true;

    private String mHardwareTvInputId;
    private TvInputManager mTvInputManager;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate();
        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
    }

    @Override
    public Session onCreateSession(String inputId) {
        return new SampleSession();
    }

    private class SampleSession extends HardwareSession {
        private ExternalSettopBox mExternalSettopBox = ExternalSettopBox.getInstance();

        @Override
        public boolean onTune(Uri channel) {
            if (DEBUG) Log.d(TAG, "onTune(" + channel + ")");
            String[] projection = { TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA };

            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(channel, projection, null, null, null);
                if (cursor == null || cursor.getCount() == 0) {
                    if (DEBUG) Log.d(TAG, "Can't find channel in TvProvider.");
                    return false;
                }
                cursor.moveToNext();
                String url = cursor.getString(0);
                if (DEBUG) Log.d(TAG, "Tuning to: " + url);
                mExternalSettopBox.tune(url);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return true;
        }

        @Override
        public void onRelease() {
            // Do nothing.
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            // Do nothing.
        }

        @Override
        public void onSetStreamVolume(float volume) {
            // Do Nothing.
        }

        @Override
        public String getHardwareInputId() {
            if (mHardwareTvInputId != null) {
                return mHardwareTvInputId;
            }
            List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
            // TODO: This should be set in the setup phase.
            String hdmiServiceName = FakeHdmiTvInputService.class.getSimpleName();
            for (TvInputInfo info : inputs) {
                if (info.getComponent().toString().contains(hdmiServiceName)) {
                    mHardwareTvInputId = info.getId();
                    return mHardwareTvInputId;
                }
            }
            if (DEBUG) Log.e(TAG, "Cannot find input for: " + hdmiServiceName);
            return null;
        }
    }
}

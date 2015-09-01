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
package com.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;

import com.android.tv.analytics.Tracker;

import java.util.Arrays;

/**
 * Creates HDMI plug broadcast receiver, and reports AC3 passthrough capabilities
 * to Google Analytics. Call {@link #register} to start receiving notifications,
 * and {@link #unregister} to stop.
 */
public final class AudioCapabilitiesReceiver {
    private static final String PREFS_NAME = "com.android.tv.audio_capabilities";
    private static final String SETTINGS_KEY_AC3_PASSTHRU_REPORTED = "ac3_passthrough_reported";
    private static final String SETTINGS_KEY_AC3_PASSTHRU_CAPABILITIES = "ac3_passthrough";
    private static final String SETTINGS_KEY_AC3_REPORT_REVISION = "ac3_report_revision";

    // AC3 capabilities stat is sent to Google Analytics just once in order to avoid
    // duplicated stat reports since it doesn't change over time in most cases.
    // Increase this revision when we should force the stat to be sent again.
    // TODO: Consier using custom metrics.
    private static final int REPORT_REVISION = 1;

    private final Context mContext;
    private final Tracker mTracker;
    private final BroadcastReceiver mReceiver = new HdmiAudioPlugBroadcastReceiver();

    /**
     * Constructs a new audio capabilities receiver.
     *
     * @param context context for registering to receive broadcasts
     * @param tracker tracker object used to upload capabilities info to Google Analytics
     */
    public AudioCapabilitiesReceiver(Context context, Tracker tracker) {
        mContext = context;
        mTracker = tracker;
    }

    public void register() {
        mContext.registerReceiver(mReceiver, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    }

    public void unregister() {
        mContext.unregisterReceiver(mReceiver);
    }

    private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                return;
            }
            reportAudioCapabilities(intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS));
        }
    }

    private void reportAudioCapabilities(int[] supportedEncodings) {
        boolean newVal = supportedEncodings != null
                && Arrays.binarySearch(supportedEncodings, AudioFormat.ENCODING_AC3) >= 0;
        boolean oldVal = getBoolean(SETTINGS_KEY_AC3_PASSTHRU_CAPABILITIES, false);
        boolean reported = getBoolean(SETTINGS_KEY_AC3_PASSTHRU_REPORTED, false);
        int revision = getInt(SETTINGS_KEY_AC3_REPORT_REVISION, 0);

        // Send the value just once. But we send it again if the value changed, to include
        // the case where users have switched TV device with different AC3 passthrough capabilities.
        if (!reported || oldVal != newVal || REPORT_REVISION > revision) {
            mTracker.sendAc3PassthroughCapabilities(newVal);
            setBoolean(SETTINGS_KEY_AC3_PASSTHRU_REPORTED, true);
            setBoolean(SETTINGS_KEY_AC3_PASSTHRU_CAPABILITIES, newVal);
            if (REPORT_REVISION > revision) {
                setInt(SETTINGS_KEY_AC3_REPORT_REVISION, REPORT_REVISION);
            }
        }
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private boolean getBoolean(String key, boolean def) {
        return getSharedPreferences().getBoolean(key, def);
    }

    private void setBoolean(String key, boolean val) {
        getSharedPreferences().edit().putBoolean(key, val).apply();
    }

    private int getInt(String key, int def) {
        return getSharedPreferences().getInt(key, def);
    }

    private void setInt(String key, int val) {
        getSharedPreferences().edit().putInt(key, val).apply();
    }
}

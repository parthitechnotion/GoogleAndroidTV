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

package com.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;

import com.android.tv.TvActivity;
import com.android.tv.util.TvSettings;
import com.android.tv.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class for handling the broadcast intents from PackageManager.
 */
public class PackageIntentsReceiver extends BroadcastReceiver {

    // Delay before checking TvInputManager's input list.
    // Sometimes TvInputManager's input list isn't updated yet when this receiver is called.
    // So we should check the list after some delay.
    private static final long TV_INPUT_UPDATE_DELAY_MS = 500;

    private TvInputManager mTvInputManager;
    private SharedPreferences mPreferences;
    private Handler mHandler = new Handler();
    private Runnable mTvActivityUpdater;
    private Runnable mDisplayInputNameCleaner;

    private void init(Context context) {
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        mPreferences = Utils.getSharedPreferencesOfDisplayNameForInput(context);

        final Context applicationContext = context.getApplicationContext();
        mTvActivityUpdater = new Runnable() {
            @Override
            public void run() {
                enableTvActivityWithinPackageManager(applicationContext,
                        !mTvInputManager.getTvInputList().isEmpty());
            }
        };

        mDisplayInputNameCleaner = new Runnable() {
            @Override
            public void run() {
                cleanupUnusedDisplayInputName();
            }
        };
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mTvInputManager == null) {
            init(context);
        }

        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            Uri uri = intent.getData();
            onPackageFullyRemoved(uri != null ? uri.getSchemeSpecificPart() : null);
        }

        mHandler.removeCallbacks(mTvActivityUpdater);
        mHandler.postDelayed(mTvActivityUpdater, TV_INPUT_UPDATE_DELAY_MS);
    }

    private void onPackageFullyRemoved(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        mHandler.removeCallbacks(mDisplayInputNameCleaner);
        mHandler.postDelayed(mDisplayInputNameCleaner, TV_INPUT_UPDATE_DELAY_MS);
    }

    private void cleanupUnusedDisplayInputName() {
        Set<String> keys = mPreferences.getAll().keySet();
        HashSet<String> unusedKeys = new HashSet<String>(keys);
        for (String key : keys) {
            if (!key.startsWith(TvSettings.PREF_DISPLAY_INPUT_NAME)) {
                unusedKeys.remove(key);
            }
        }
        List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
        for (TvInputInfo input : inputs) {
            unusedKeys.remove(TvSettings.PREF_DISPLAY_INPUT_NAME + input.getId());
        }
        if (!unusedKeys.isEmpty()) {
            SharedPreferences.Editor editor = mPreferences.edit();
            for (String key : unusedKeys) {
                editor.remove(key);
            }
            editor.commit();
        }
    }

    private void enableTvActivityWithinPackageManager(Context context, boolean enable) {
        PackageManager pm = context.getPackageManager();
        ComponentName name = new ComponentName(context, TvActivity.class);

        int newState = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(name) != newState) {
            pm.setComponentEnabledSetting(name, newState, PackageManager.DONT_KILL_APP);
        }
    }
}

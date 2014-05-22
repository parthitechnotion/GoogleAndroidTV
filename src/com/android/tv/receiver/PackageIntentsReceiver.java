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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;

import com.android.tv.util.TvSettings;
import com.android.tv.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class for handling the broadcast intents from PackageManager.
 */
public class PackageIntentsReceiver extends BroadcastReceiver {
    private TvInputManager mTvInputManager;
    private SharedPreferences mPreferences;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mPreferences == null) {
            mPreferences = Utils.getSharedPreferencesOfDisplayNameForInput(context);
        }
        if (mTvInputManager == null) {
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            Uri uri = intent.getData();
            onPackageFullyRemoved(uri != null ? uri.getSchemeSpecificPart() : null);
        }
    }

    private void onPackageFullyRemoved(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        cleanupUnusedDisplayInputName();
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
}

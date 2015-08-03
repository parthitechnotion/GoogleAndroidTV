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

package com.android.tv.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * A utility class related to input setup.
 */
public class SetupUtils {
    private static final String TAG = "SetupUtils";
    private static final boolean DEBUG = false;

    // Known inputs are inputs which are shown in SetupView before. When a new input is installed,
    // the input will not be included in "PREF_KEY_KNOWN_INPUTS".
    private static final String PREF_KEY_KNOWN_INPUTS = "known_inputs";
    // Set up inputs are inputs whose setup activity has been launched from Live channels app.
    private static final String PREF_KEY_SET_UP_INPUTS = "set_up_inputs";
    private static final String PREF_KEY_IS_FIRST_TUNE = "is_first_tune";
    private static SetupUtils sSetupUtils;

    private final SharedPreferences mSharedPreferences;
    private final Set<String> mKnownInputs;
    private final Set<String> mSetUpInputs;
    private boolean mIsFirstTune;

    private SetupUtils(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mSetUpInputs = new HashSet<>(mSharedPreferences.getStringSet(PREF_KEY_SET_UP_INPUTS,
                new HashSet<String>()));
        mKnownInputs = new HashSet<>(mSharedPreferences.getStringSet(PREF_KEY_KNOWN_INPUTS,
                new HashSet<String>()));
        mIsFirstTune = mSharedPreferences.getBoolean(PREF_KEY_IS_FIRST_TUNE, true);
    }

    /**
     * Gets an instance of {@link SetupUtils}.
     */
    public static SetupUtils getInstance(Context context) {
        if (sSetupUtils != null) {
            return sSetupUtils;
        }
        sSetupUtils = new SetupUtils(context.getApplicationContext());
        return sSetupUtils;
    }

    public boolean isFirstTune() {
        return mIsFirstTune;
    }

    /**
     * Returns true, if the input with {@code inputId} is newly installed.
     */
    public boolean isNewInput(String inputId) {
        return !mKnownInputs.contains(inputId);
    }

    /**
     * Marks an input with {@code inputId} as a known input. Once it is marked, {@link #isNewInput}
     * will return false.
     */
    public void markAsKnownInput(String inputId) {
        mKnownInputs.add(inputId);
        mSharedPreferences.edit().putStringSet(PREF_KEY_KNOWN_INPUTS, mKnownInputs).apply();
    }

    /**
     * Returns true, if {@code inputId}'s setup activity has been launched.
     */
    public boolean hasSetupLaunched(String inputId) {
        boolean launched = mSetUpInputs.contains(inputId);
        if (DEBUG) {
            Log.d(TAG, "hasSetupLaunched: (input=" + inputId + ", result= " + launched + ")");
        }
        return launched;
    }

    /**
     * Returns true, if there is any newly installed input.
     */
    public boolean hasNewInput(TvInputManagerHelper inputManager) {
        for (TvInputInfo input : inputManager.getTvInputInfos(true, true)) {
            if (isNewInput(input.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grants permission for writing EPG data to all verified packages.
     *
     * @param context The Context used for granting permission.
     */
    public static void grantEpgPermissionToSetUpPackages(Context context) {
        // TvProvider allows granting of Uri permissions starting from MNC.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> setUpInputs = new HashSet<>(sharedPreferences.getStringSet(
                    PREF_KEY_SET_UP_INPUTS, new HashSet<String>()));
            Set<String> setUpPackages = new HashSet<>();
            for (String input : setUpInputs) {
                setUpPackages.add(ComponentName.unflattenFromString(input).getPackageName());
            }
            for (String packageName : setUpPackages) {
                grantEpgPermission(context, packageName);
            }
        }
    }

    /**
     * Grants permission for writing EPG data to a given package.
     *
     * @param context The Context used for granting permission.
     * @param packageName The name of the package to give permission.
     */
    public static void grantEpgPermission(Context context, String packageName) {
        // TvProvider allows granting of Uri permissions starting from MNC.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (DEBUG) {
                Log.d(TAG, "grantEpgPermission(context=" + context + ", packageName=" + packageName
                        + ")");
            }
            try {
                int modeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
                context.grantUriPermission(packageName, TvContract.Channels.CONTENT_URI, modeFlags);
                context.grantUriPermission(packageName, TvContract.Programs.CONTENT_URI, modeFlags);
            } catch (SecurityException e) {
                Log.e(TAG, "Either TvProvider does not allow granting of Uri permissions or the app"
                        + " does not have permission" + e);
            }
        }
    }

    /**
     * Called when Live channels app is launched. Once it is called, {@link
     * #isFirstTune} will return false.
     */
    public void onTuned() {
        if (!mIsFirstTune) {
            return;
        }
        mIsFirstTune = false;
        mSharedPreferences.edit().putBoolean(PREF_KEY_IS_FIRST_TUNE, false).apply();
    }

    /**
     * Called when input list is changed. It mainly handles input removals.
     */
    public void onInputListUpdated(TvInputManager manager) {
        // mKnownInputs is a super set of mSetUpInputs.
        Set<String> removedInputList = new HashSet<>(mKnownInputs);
        for (TvInputInfo input : manager.getTvInputList()) {
            removedInputList.remove(input.getId());
        }

        if (!removedInputList.isEmpty()) {
            for (String input : removedInputList) {
                mSetUpInputs.remove(input);
                mKnownInputs.remove(input);
            }
            mSharedPreferences.edit()
                    .putStringSet(PREF_KEY_SET_UP_INPUTS, mSetUpInputs).apply();
            mSharedPreferences.edit().putStringSet(PREF_KEY_KNOWN_INPUTS, mKnownInputs).apply();
        }
    }

    /**
     * Called when an setup activity is launched. Once it is called, {@link #hasSetupLaunched}
     * will return true for {@code inputId}.
     */
    public void onSetupLaunched(String inputId) {
        if (DEBUG) Log.d(TAG, "onSetupLaunched: input=" + inputId);
        mSetUpInputs.add(inputId);
        mSharedPreferences.edit()
                .putStringSet(PREF_KEY_SET_UP_INPUTS, mSetUpInputs).apply();
    }
}

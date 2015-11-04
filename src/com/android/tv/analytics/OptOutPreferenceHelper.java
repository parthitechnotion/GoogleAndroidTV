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
 * limitations under the License
 */

package com.android.tv.analytics;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

/**
 * Handles the opt out preference for analytics, including updating {@link Analytics} with the
 * preference changes.
 */
public final class OptOutPreferenceHelper {
    /**
     * The {@link SharedPreferences SharedPreferences} key
     * "{@value #ANALYTICS_OPT_OUT_KEY}",  true means the user has chosen NOT to send
     * analytics.
     */
    public static final String ANALYTICS_OPT_OUT_KEY = "analytics_opt_out";

    /**
     * The default value for the {@link SharedPreferences SharedPreferences} key
     * "{@value #ANALYTICS_OPT_OUT_KEY}" is
     * {@value #ANALYTICS_OPT_OUT_DEFAULT_VALUE}
     */
    public static final boolean ANALYTICS_OPT_OUT_DEFAULT_VALUE = false;

    private final SharedPreferences userPrefs;

    public OptOutPreferenceHelper(Context context) {
        userPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Creates and registers a change listener that will update analytics.
     *
     * @param analytics    the analytics to update when opt out settings change.
     * @param defaultValue the default opt out values
     * @return the newly created OptOutChangeListener, keep this pass to
     *         {@link #unRegisterChangeListener(OptOutChangeListener)}
     */
    public OptOutChangeListener registerChangeListener(Analytics analytics, boolean defaultValue) {
        OptOutChangeListener changeListener = new OptOutChangeListener(analytics, defaultValue);
        userPrefs.registerOnSharedPreferenceChangeListener(changeListener);
        return changeListener;
    }

    /**
     * Unregister a {@link OptOutChangeListener} created by
     * {@link #registerChangeListener(Analytics, boolean)}
     */
    public void unRegisterChangeListener(OptOutChangeListener changeListener) {
        userPrefs.registerOnSharedPreferenceChangeListener(changeListener);
    }

    /**
     * Returns the saved opt out preference or {@code defaultValue} if it has been set.
     */
    public boolean getOptOutPreference(boolean defaultValue) {
        return userPrefs.getBoolean(ANALYTICS_OPT_OUT_KEY, defaultValue);
    }

    /**
     * Sets the opt out preference.
     */
    public void setOptOutPreference(boolean optOut) {
        userPrefs.edit().putBoolean(ANALYTICS_OPT_OUT_KEY, optOut).apply();
    }

    /**
     * Updates Analytics when opt out preference is changed.
     *
     * <p>{@link OnSharedPreferenceChangeListener} is used so the {@code analytics} object is
     * updated even if the preference are modified directly and not by
     * {@link OptOutPreferenceHelper}.
     */
    public static final class OptOutChangeListener implements OnSharedPreferenceChangeListener {
        private final Analytics mAnalytics;
        private final boolean mDefaultValue;

        private OptOutChangeListener(Analytics analytics, boolean defaultValue) {
            mAnalytics = analytics;
            mDefaultValue = defaultValue;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case ANALYTICS_OPT_OUT_KEY:
                    mAnalytics.setAppOptOut(
                            sharedPreferences.getBoolean(ANALYTICS_OPT_OUT_KEY, mDefaultValue));
                    break;
                default:
            }
        }
    }
}

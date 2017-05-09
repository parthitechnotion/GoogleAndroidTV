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

package com.android.tv.tuner;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.MainThread;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.TunerPreferenceProvider.Preferences;
import com.android.tv.tuner.util.TisConfiguration;

/**
 * A helper class for the USB tuner preferences.
 */
public class TunerPreferences {
    private static final String TAG = "TunerPreferences";

    private static final String PREFS_KEY_CHANNEL_DATA_VERSION = "channel_data_version";
    private static final String PREFS_KEY_SCANNED_CHANNEL_COUNT = "scanned_channel_count";
    private static final String PREFS_KEY_LAST_POSTAL_CODE = "last_postal_code";
    private static final String PREFS_KEY_SCAN_DONE = "scan_done";
    private static final String PREFS_KEY_LAUNCH_SETUP = "launch_setup";
    private static final String PREFS_KEY_STORE_TS_STREAM = "store_ts_stream";

    private static final String SHARED_PREFS_NAME = "com.android.tv.tuner.preferences";

    public static final int CHANNEL_DATA_VERSION_NOT_SET = -1;

    private static final Bundle sPreferenceValues = new Bundle();
    private static LoadPreferencesTask sLoadPreferencesTask;
    private static ContentObserver sContentObserver;

    private static boolean sInitialized;

    /**
     * Initializes the USB tuner preferences.
     */
    @MainThread
    public static void initialize(final Context context) {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        if (useContentProvider(context)) {
            loadPreferences(context);
            sContentObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    loadPreferences(context);
                }
            };
            context.getContentResolver().registerContentObserver(
                    TunerPreferenceProvider.Preferences.CONTENT_URI, true, sContentObserver);
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    getSharedPreferences(context);
                    return null;
                }
            }.execute();
        }
    }

    /**
     * Releases the resources.
     */
    public static synchronized void release(Context context) {
        if (useContentProvider(context) && sContentObserver != null) {
            context.getContentResolver().unregisterContentObserver(sContentObserver);
        }
    }

    /**
     * Loads the preferences from database.
     * <p>
     * This preferences is used across processes, so the preferences should be loaded again when the
     * databases changes.
     */
    @MainThread
    public static void loadPreferences(Context context) {
        if (sLoadPreferencesTask != null
                && sLoadPreferencesTask.getStatus() != AsyncTask.Status.FINISHED) {
            sLoadPreferencesTask.cancel(true);
        }
        sLoadPreferencesTask = new LoadPreferencesTask(context);
        sLoadPreferencesTask.execute();
    }

    private static boolean useContentProvider(Context context) {
        // If TIS is a part of LC, it should use ContentProvider to resolve multiple process access.
        return TisConfiguration.isPackagedWithLiveChannels(context);
    }

    public static synchronized int getChannelDataVersion(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getInt(PREFS_KEY_CHANNEL_DATA_VERSION,
                    CHANNEL_DATA_VERSION_NOT_SET);
        } else {
            return getSharedPreferences(context)
                    .getInt(TunerPreferences.PREFS_KEY_CHANNEL_DATA_VERSION,
                            CHANNEL_DATA_VERSION_NOT_SET);
        }
    }

    public static synchronized void setChannelDataVersion(Context context, int version) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_CHANNEL_DATA_VERSION, version);
        } else {
            getSharedPreferences(context).edit()
                    .putInt(TunerPreferences.PREFS_KEY_CHANNEL_DATA_VERSION, version)
                    .apply();
        }
    }

    public static synchronized int getScannedChannelCount(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getInt(PREFS_KEY_SCANNED_CHANNEL_COUNT);
        } else {
            return getSharedPreferences(context)
                    .getInt(TunerPreferences.PREFS_KEY_SCANNED_CHANNEL_COUNT, 0);
        }
    }

    public static synchronized void setScannedChannelCount(Context context, int channelCount) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_SCANNED_CHANNEL_COUNT, channelCount);
        } else {
            getSharedPreferences(context).edit()
                    .putInt(TunerPreferences.PREFS_KEY_SCANNED_CHANNEL_COUNT, channelCount)
                    .apply();
        }
    }

    public static synchronized String getLastPostalCode(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getString(PREFS_KEY_LAST_POSTAL_CODE);
        } else {
            return getSharedPreferences(context).getString(PREFS_KEY_LAST_POSTAL_CODE, null);
        }
    }

    public static synchronized void setLastPostalCode(Context context, String postalCode) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_LAST_POSTAL_CODE, postalCode);
        } else {
            getSharedPreferences(context).edit()
                    .putString(PREFS_KEY_LAST_POSTAL_CODE, postalCode).apply();
        }
    }

    public static synchronized boolean isScanDone(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getBoolean(PREFS_KEY_SCAN_DONE);
        } else {
            return getSharedPreferences(context)
                    .getBoolean(TunerPreferences.PREFS_KEY_SCAN_DONE, false);
        }
    }

    public static synchronized void setScanDone(Context context) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_SCAN_DONE, true);
        } else {
            getSharedPreferences(context).edit()
                    .putBoolean(TunerPreferences.PREFS_KEY_SCAN_DONE, true)
                    .apply();
        }
    }

    public static synchronized boolean shouldShowSetupActivity(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getBoolean(PREFS_KEY_LAUNCH_SETUP);
        } else {
            return getSharedPreferences(context)
                    .getBoolean(TunerPreferences.PREFS_KEY_LAUNCH_SETUP, false);
        }
    }

    public static synchronized void setShouldShowSetupActivity(Context context, boolean need) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_LAUNCH_SETUP, need);
        } else {
            getSharedPreferences(context).edit()
                    .putBoolean(TunerPreferences.PREFS_KEY_LAUNCH_SETUP, need)
                    .apply();
        }
    }

    public static synchronized boolean getStoreTsStream(Context context) {
        SoftPreconditions.checkState(sInitialized);
        if (useContentProvider(context)) {
            return sPreferenceValues.getBoolean(PREFS_KEY_STORE_TS_STREAM, false);
        } else {
            return getSharedPreferences(context)
                    .getBoolean(TunerPreferences.PREFS_KEY_STORE_TS_STREAM, false);
        }
    }

    public static synchronized void setStoreTsStream(Context context, boolean shouldStore) {
        if (useContentProvider(context)) {
            setPreference(context, PREFS_KEY_STORE_TS_STREAM, shouldStore);
        } else {
            getSharedPreferences(context).edit()
                    .putBoolean(TunerPreferences.PREFS_KEY_STORE_TS_STREAM, shouldStore)
                    .apply();
        }
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static synchronized void setPreference(Context context, String key, String value) {
        sPreferenceValues.putString(key, value);
        savePreference(context, key, value);
    }

    private static synchronized void setPreference(Context context, String key, int value) {
        sPreferenceValues.putInt(key, value);
        savePreference(context, key, Integer.toString(value));
    }

    private static synchronized void setPreference(Context context, String key, boolean value) {
        sPreferenceValues.putBoolean(key, value);
        savePreference(context, key, Boolean.toString(value));
    }

    private static void savePreference(final Context context, final String key,
            final String value) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(Preferences.COLUMN_KEY, key);
                values.put(Preferences.COLUMN_VALUE, value);
                try {
                    resolver.insert(Preferences.CONTENT_URI, values);
                } catch (Exception e) {
                    SoftPreconditions.warn(TAG, "setPreference", "Error writing preference values",
                            e);
                }
                return null;
            }
        }.execute();
    }

    private static class LoadPreferencesTask extends AsyncTask<Void, Void, Bundle> {
        private final Context mContext;
        private LoadPreferencesTask(Context context) {
            mContext = context;
        }

        @Override
        protected Bundle doInBackground(Void... params) {
            Bundle bundle = new Bundle();
            ContentResolver resolver = mContext.getContentResolver();
            String[] projection = new String[] { Preferences.COLUMN_KEY, Preferences.COLUMN_VALUE };
            try (Cursor cursor = resolver.query(Preferences.CONTENT_URI, projection, null, null,
                    null)) {
                if (cursor != null) {
                    while (!isCancelled() && cursor.moveToNext()) {
                        String key = cursor.getString(0);
                        String value = cursor.getString(1);
                        switch (key) {
                            case PREFS_KEY_CHANNEL_DATA_VERSION:
                            case PREFS_KEY_SCANNED_CHANNEL_COUNT:
                                try {
                                    bundle.putInt(key, Integer.parseInt(value));
                                } catch (NumberFormatException e) {
                                    // Does nothing.
                                }
                                break;
                            case PREFS_KEY_SCAN_DONE:
                            case PREFS_KEY_LAUNCH_SETUP:
                            case PREFS_KEY_STORE_TS_STREAM:
                                bundle.putBoolean(key, Boolean.parseBoolean(value));
                                break;
                            case PREFS_KEY_LAST_POSTAL_CODE:
                                bundle.putString(key, value);
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                SoftPreconditions.warn(TAG, "getPreference", "Error querying preference values", e);
                return null;
            }
            return bundle;
        }

        @Override
        protected synchronized void onPostExecute(Bundle bundle) {
            if (bundle != null) {
                sPreferenceValues.putAll(bundle);
            }
        }
    }
}
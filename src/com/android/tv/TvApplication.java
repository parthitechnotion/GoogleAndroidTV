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

package com.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;

import com.android.tv.analytics.Analytics;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.OptOutPreferenceHelper;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.Tracker;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.util.SystemProperties;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.List;

public class TvApplication extends Application {
    private static final String TAG = "TvApplication";
    private static final boolean DEBUG = false;
    private static String versionName = "";

    private MainActivity mMainActivity;
    private SelectInputActivity mSelectInputActivity;
    private Analytics mAnalytics;
    private Tracker mTracker;
    private TvInputManagerHelper mTvInputManagerHelper;
    private ChannelDataManager mChannelDataManager;
    private ProgramDataManager mProgramDataManager;
    private OptOutPreferenceHelper mOptPreferenceHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        // Only set StrictMode for ENG builds because the build server only produces userdebug
        // builds.
        if (BuildConfig.ENG && SystemProperties.ALLOW_STRICT_MODE.getValue()) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
            StrictMode.VmPolicy.Builder vmPolicyBuilder = new StrictMode.VmPolicy.Builder()
                    .detectAll().penaltyLog();
            if (BuildConfig.ENG && SystemProperties.ALLOW_DEATH_PENALTY.getValue() &&
                    !Utils.isRunningInTest()) {
                // TODO turn on death penalty for tests when they stop leaking MainActivity
            }
            StrictMode.setVmPolicy(vmPolicyBuilder.build());
        }

        if (BuildConfig.ENG && !SystemProperties.ALLOW_ANALYTICS_IN_ENG.getValue()) {
            mAnalytics = StubAnalytics.getInstance(this);
        } else {
            mAnalytics = StubAnalytics.getInstance(this);
        }
        mTracker = mAnalytics.getDefaultTracker();
        if(Features.ANALYTICS_OPT_OUT.isEnabled(this)) {
            mOptPreferenceHelper = new OptOutPreferenceHelper(this);
            mOptPreferenceHelper.registerChangeListener(mAnalytics,
                    OptOutPreferenceHelper.ANALYTICS_OPT_OUT_DEFAULT_VALUE);
            // always start with analytics off
            mAnalytics.setAppOptOut(true);
            // then update with the saved preference in an AsyncTask.
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... voids) {
                    return mOptPreferenceHelper.getOptOutPreference(
                            OptOutPreferenceHelper.ANALYTICS_OPT_OUT_DEFAULT_VALUE);
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    mAnalytics.setAppOptOut(result);
                }
            }.execute();
        }
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to get version name.", e);
            versionName = "";
        }
        mTvInputManagerHelper = new TvInputManagerHelper(this);
        mTvInputManagerHelper.start();
        if (DEBUG) Log.i(TAG, "Started Live TV " + versionName);
    }

    /**
     * Returns the {@link Analytics}.
     */
    public Analytics getAnalytics() {
        return mAnalytics;
    }

    /**
     * Returns the default tracker.
     */
    public Tracker getTracker() {
        return mTracker;
    }

    public OptOutPreferenceHelper getOptPreferenceHelper(){
        return mOptPreferenceHelper;
    }

    /**
     * Returns {@link ChannelDataManager}.
     */
    public ChannelDataManager getChannelDataManager() {
        if (mChannelDataManager == null) {
            mChannelDataManager = new ChannelDataManager(this, mTvInputManagerHelper, mTracker);
            mChannelDataManager.start();
        }
        return mChannelDataManager;
    }

    /**
     * Returns {@link ProgramDataManager}.
     */
    public ProgramDataManager getProgramDataManager() {
        if (mProgramDataManager == null) {
            mProgramDataManager = new ProgramDataManager(this);
            mProgramDataManager.start();
        }
        return mProgramDataManager;
    }

    /**
     * Returns {@link TvInputManagerHelper}.
     */
    public TvInputManagerHelper getTvInputManagerHelper() {
        return mTvInputManagerHelper;
    }

    /**
     * MainActivity is set in {@link MainActivity#onCreate} and cleared in
     * {@link MainActivity#onDestroy}.
     */
    public void setMainActivity(MainActivity activity) {
        mMainActivity = activity;
    }

    /**
     * SelectInputActivity is set in {@link SelectInputActivity#onCreate} and cleared in
     * {@link SelectInputActivity#onDestroy}.
     */
    public void setSelectInputActivity(SelectInputActivity activity) {
        mSelectInputActivity = activity;
    }

    /**
     * Checks if MainActivity is set or not.
     */
    public boolean hasMainActivity() {
        return (mMainActivity != null);
    }

    /**
     * Returns true, if {@code activity} is the current activity.
     *
     * Note: MainActivity can start while another MainActivity destroys. In this case, the current
     * activity is the newly created activity.
     */
    public boolean isCurrentMainActivity(MainActivity activity) {
        return mMainActivity == activity;
    }

    /**
     * Handles the global key KEYCODE_TV.
     */
    public void handleTvKey() {
        if (mMainActivity == null || !mMainActivity.isActivityResumed()) {
            startMainActivity(null);
        }
    }

    /**
     * Handles the global key KEYCODE_TV_INPUT.
     */
    public void handleTvInputKey() {
        TvInputManager tvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> tvInputs = tvInputManager.getTvInputList();
        int inputCount = 0;
        boolean hasTunerInput = false;
        for (TvInputInfo input : tvInputs) {
            if (input.isPassthroughInput()) {
                ++inputCount;
            } else if (!hasTunerInput) {
                hasTunerInput = true;
                ++inputCount;
            }
        }
        if (inputCount < 2) {
            return;
        }
        Activity activityToHandle = mMainActivity != null && mMainActivity.isActivityResumed()
                ? mMainActivity : mSelectInputActivity;
        if (activityToHandle != null) {
            // If startActivity is called, MainActivity.onPause is unnecessarily called. To
            // prevent it, MainActivity.dispatchKeyEvent is directly called.
            activityToHandle.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TV_INPUT));
            activityToHandle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_TV_INPUT));
        } else if (mMainActivity != null && mMainActivity.isActivityStarted()) {
            Bundle extras = new Bundle();
            extras.putString(Utils.EXTRA_KEY_ACTION, Utils.EXTRA_ACTION_SHOW_TV_INPUT);
            startMainActivity(extras);
        } else {
            startActivity(new Intent(this, SelectInputActivity.class).setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void startMainActivity(Bundle extras) {
        // The use of FLAG_ACTIVITY_NEW_TASK enables arbitrary applications to access the intent
        // sent to the root activity. Having said that, we should be fine here since such an intent
        // does not carry any important user data.
        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
    }

    public static String getVersionName() {
       return versionName;
    }

}

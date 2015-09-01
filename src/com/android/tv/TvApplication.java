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

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;

import com.android.tv.analytics.Analytics;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.Tracker;
import com.android.tv.util.RecurringRunner;
import com.android.tv.util.SystemProperties;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TvApplication extends Application {
    private static final String TAG = "TvApplication";
    private static final boolean DEBUG = false;
    private static String versionName = "";

    private MainActivity mActivity;
    private Tracker mTracker;
    private TvInputManagerHelper mTvInputManagerHelper;
    private RecurringRunner mSendConfigInfoRecurringRunner;

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
        Analytics analytics;
        if (BuildConfig.ENG && !SystemProperties.ALLOW_ANALYTICS_IN_ENG.getValue()) {
            analytics = StubAnalytics.getInstance(this);
        } else {
            analytics = StubAnalytics.getInstance(this);
        }
        mTracker = analytics.getDefaultTracker();
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to get version name.", e);
            versionName = "";
        }
        mTvInputManagerHelper = new TvInputManagerHelper(this);
        mTvInputManagerHelper.start();
        mSendConfigInfoRecurringRunner = new RecurringRunner(this, TimeUnit.DAYS.toMillis(1),
                new SendConfigInfoRunnable());
        mSendConfigInfoRecurringRunner.start();
        if (DEBUG) Log.i(TAG, "Started Live TV " + versionName);
    }

    public Tracker getTracker() {
        return mTracker;
    }

    public TvInputManagerHelper getTvInputManagerHelper() {
        return mTvInputManagerHelper;
    }

    /**
     * MainActivity is set in {@link MainActivity#onCreate} and cleared in
     * {@link MainActivity#onDestroy}.
     */
    public void setMainActivity(MainActivity activity) {
        mActivity = activity;
    }

    /**
     * Checks if MainActivity is set or not.
     */
    public boolean hasMainActivity() {
        return (mActivity != null);
    }

    /**
     * Handles the global key KEYCODE_TV.
     */
    public void handleTvKey() {
        if (mActivity == null || !mActivity.isActivityResumed()) {
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
        if (mActivity != null && mActivity.isActivityResumed()) {
            // If startActivity is called, MainActivity.onPause is unnecessarily called. To
            // prevent it, MainActivity.dispatchKeyEvent is directly called.
            mActivity.dispatchKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TV_INPUT));
            mActivity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TV_INPUT));
        } else {
            Bundle extras = new Bundle();
            extras.putString(Utils.EXTRA_KEY_ACTION, Utils.EXTRA_ACTION_SHOW_TV_INPUT);
            startMainActivity(extras);
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

    /**
     * Data useful for tracking that doesn't change often.
     */
    public static class ConfigurationInfo {
        public final int systemInputCount;
        public final int nonSystemInputCount;

        public ConfigurationInfo(int systemInputCount, int nonSystemInputCount) {
            this.systemInputCount = systemInputCount;
            this.nonSystemInputCount = nonSystemInputCount;
        }
    }

    private class SendConfigInfoRunnable implements Runnable {
        @Override
        public void run() {
            List<TvInputInfo> infoList = mTvInputManagerHelper.getTvInputInfos(false, false);
            int systemInputCount = 0;
            int nonSystemInputCount = 0;
            for (TvInputInfo info : infoList) {
                if (mTvInputManagerHelper.isSystemInput(info)) {
                    systemInputCount++;
                } else {
                    nonSystemInputCount++;
                }
            }
            ConfigurationInfo configurationInfo = new ConfigurationInfo(systemInputCount,
                    nonSystemInputCount);
            mTracker.sendConfigurationInfo(configurationInfo);
        }
    }
}

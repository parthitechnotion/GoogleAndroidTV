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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;

import com.android.tv.Features;
import com.android.tv.TvActivity;
import com.android.tv.util.SetupUtils;

import java.util.List;

/**
 * A class for handling the broadcast intents from PackageManager.
 */
public class PackageIntentsReceiver extends BroadcastReceiver {
    // Delay before checking TvInputManager's input list.
    // Sometimes TvInputManager's input list isn't updated yet when this receiver is called.
    // So we should check the list after some delay.
    private static final long TV_INPUT_UPDATE_DELAY_MS = 500;

    private TvInputManager mTvInputManager;
    private final Handler mHandler = new Handler();
    private Runnable mOnPackageUpdatedRunnable;
    private PackageManager mPackageManager;
    private ComponentName mTvActivityComponentName;

    private void init(Context context) {
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);

        final Context applicationContext = context.getApplicationContext();
        mOnPackageUpdatedRunnable = new Runnable() {
            @Override
            public void run() {
                if (!Features.ONBOARDING_EXPERIENCE.isEnabled(applicationContext)) {
                    List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
                    // Enable the MainActivity only if there is at least one tuner type input.
                    boolean enable = false;
                    for (TvInputInfo input : inputs) {
                        if (input.getType() == TvInputInfo.TYPE_TUNER) {
                            enable = true;
                            break;
                        }
                    }
                    enableTvActivityWithinPackageManager(applicationContext, enable);
                }

                SetupUtils.getInstance(applicationContext).onInputListUpdated(mTvInputManager);
            }
        };

        mPackageManager = applicationContext.getPackageManager();
        mTvActivityComponentName = new ComponentName(applicationContext, TvActivity.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mTvInputManager == null) {
            init(context);
        }

        mHandler.removeCallbacks(mOnPackageUpdatedRunnable);
        mHandler.postDelayed(mOnPackageUpdatedRunnable, TV_INPUT_UPDATE_DELAY_MS);

    }


    /**
     * Enables/Disables {@link TvActivity}.
     */
    private void enableTvActivityWithinPackageManager(Context context, boolean enable) {
        PackageManager pm = context.getPackageManager();
        ComponentName name = new ComponentName(context, TvActivity.class);

        int newState = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(name) != newState) {
            pm.setComponentEnabledSetting(name, newState, 0);
        }
    }
}

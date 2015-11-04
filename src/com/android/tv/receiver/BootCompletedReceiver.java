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

import com.android.tv.Features;
import com.android.tv.TvActivity;
import com.android.tv.recommendation.NotificationService;
import com.android.tv.util.OnboardingUtils;
import com.android.tv.util.SetupUtils;

/**
 * Boot completed receiver. It's used to start the {@code NotificationService} for recommendation,
 * grant permission to the TIS's and enable {@code TvActivity} if necessary.
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Start {@link NotificationService}.
        Intent notificationIntent = new Intent(context, NotificationService.class);
        notificationIntent.setAction(NotificationService.ACTION_SHOW_RECOMMENDATION);
        context.startService(notificationIntent);

        // Grant permission to already set up packages after the system has finished booting.
        SetupUtils.grantEpgPermissionToSetUpPackages(context);

        // On-boarding experience.
        if (Features.ONBOARDING_EXPERIENCE.isEnabled(context)) {
            if (OnboardingUtils.isFirstBoot(context)) {
                // Enable the application if this is the first run after the on-boarding experience
                // is applied just in case when the app is disabled before.
                PackageManager pm = context.getPackageManager();
                ComponentName name = new ComponentName(context, TvActivity.class);
                if (pm.getComponentEnabledSetting(name)
                        != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(name,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
                }
                OnboardingUtils.setFirstBootCompleted(context);
            }
        }
    }
}

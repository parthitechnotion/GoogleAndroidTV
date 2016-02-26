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

import com.android.tv.TvActivity;
import com.android.tv.TvApplication;
import com.android.usbtuner.setup.TunerSetupActivity;
import com.android.usbtuner.UsbTunerPreferences;
import com.android.usbtuner.tvinput.UsbTunerTvInputService;

/**
 * A class for handling the broadcast intents from PackageManager.
 */
public class PackageIntentsReceiver extends BroadcastReceiver {
    private PackageManager mPackageManager;
    private ComponentName mTvActivityComponentName;
    private ComponentName mUsbTunerComponentName;

    private void init(Context context) {
        mPackageManager = context.getPackageManager();
        mTvActivityComponentName = new ComponentName(context, TvActivity.class);
        mUsbTunerComponentName = new ComponentName(context, UsbTunerTvInputService.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mPackageManager == null) {
            init(context);
        }
        ((TvApplication) context.getApplicationContext()).handleInputCountChanged();
        // Check the component status of UsbTunerTvInputService and TvActivity here to make sure
        // start the setup activity of USB tuner TV input service only when those components are
        // enabled.
        if (UsbTunerPreferences.shouldShowSetupActivity(context)
                && Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction())
                && mPackageManager.getComponentEnabledSetting(mTvActivityComponentName)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                && mPackageManager.getComponentEnabledSetting(mUsbTunerComponentName)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            startUsbTunerSetupActivity(context);
            UsbTunerPreferences.setShouldShowSetupActivity(context, false);
        }
    }

    /**
     * Launches the setup activity of USB tuner TV input service.
     *
     * @param context {@link Context} instance
     */
    private static void startUsbTunerSetupActivity(Context context) {
        Intent intent = TunerSetupActivity.createSetupActivity(context);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}

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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.util.Log;

import com.android.tv.common.TvCommonConstants;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;

/**
 * An activity to launch a TV input setup activity.
 *
 * <p> After setup activity is finished, all channels will be browsable.
 */
public class SetupPassthroughActivity extends Activity {
    private static final String TAG = "SetupPassthroughActivity";
    private static final boolean DEBUG = false;

    private static final int REQUEST_START_SETUP_ACTIVITY = 200;

    private TvInputInfo mTvInputInfo;
    private Intent mActivityAfterCompletion;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TvApplication tvApplication = (TvApplication) getApplication();
        TvInputManagerHelper inputManager = tvApplication.getTvInputManagerHelper();
        // It is not only for setting the variable but also for early initialization of
        // ChannelDataManager.
        String inputId = getIntent().getStringExtra(TvCommonConstants.EXTRA_INPUT_ID);
        mTvInputInfo = inputManager.getTvInputInfo(inputId);
        if (DEBUG) Log.d(TAG, "TvInputId " + inputId + " / TvInputInfo " + mTvInputInfo);
        if (mTvInputInfo == null) {
            Log.w(TAG, "There is no input with the ID " + inputId + ".");
            finish();
            return;
        }
        Intent setupIntent = mTvInputInfo.createSetupIntent();
        if (setupIntent == null) {
            Log.w(TAG, "The input (" + mTvInputInfo.getId() + ") doesn't have setup.");
            finish();
            return;
        }
        SetupUtils.grantEpgPermission(this, mTvInputInfo.getServiceInfo().packageName);
        mActivityAfterCompletion = getIntent().getParcelableExtra(
                TvCommonConstants.EXTRA_ACTIVITY_AFTER_COMPLETION);
        if (DEBUG) Log.d(TAG, "Activity after completion " + mActivityAfterCompletion);
        setupIntent.putExtras(getIntent().getExtras());
        startActivityForResult(setupIntent, REQUEST_START_SETUP_ACTIVITY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_START_SETUP_ACTIVITY || resultCode != Activity.RESULT_OK) {
            finish();
            return;
        }
        SetupUtils.getInstance(this).onTvInputSetupFinished(mTvInputInfo.getId(), new Runnable() {
            @Override
            public void run() {
                if (mActivityAfterCompletion != null) {
                    try {
                        startActivity(mActivityAfterCompletion);
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, "Activity launch failed", e);
                    }
                }
                finish();
            }
        });
    }
}

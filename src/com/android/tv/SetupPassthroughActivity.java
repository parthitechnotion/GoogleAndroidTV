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

import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.TvCommonConstants;
import com.android.tv.data.epg.EpgFetcher;
import com.android.tv.experiments.Experiments;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

/**
 * An activity to launch a TV input setup activity.
 *
 * <p> After setup activity is finished, all channels will be browsable.
 */
public class SetupPassthroughActivity extends Activity {
    private static final String TAG = "SetupPassthroughAct";
    private static final boolean DEBUG = false;

    private static final int REQUEST_START_SETUP_ACTIVITY = 200;

    private TvInputInfo mTvInputInfo;
    private Intent mActivityAfterCompletion;
    private boolean mEpgFetcherDuringScan;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        ApplicationSingletons appSingletons = TvApplication.getSingletons(this);
        TvInputManagerHelper inputManager = appSingletons.getTvInputManagerHelper();
        Intent intent = getIntent();
        String inputId = intent.getStringExtra(TvCommonConstants.EXTRA_INPUT_ID);
        mTvInputInfo = inputManager.getTvInputInfo(inputId);
        mActivityAfterCompletion = intent.getParcelableExtra(
                TvCommonConstants.EXTRA_ACTIVITY_AFTER_COMPLETION);
        boolean needToFetchEpg = Utils.isInternalTvInput(this, mTvInputInfo.getId())
                && Experiments.CLOUD_EPG.get();
        if (needToFetchEpg) {
            // In case when the activity is restored, this flag should be restored as well.
            mEpgFetcherDuringScan = true;
        }
        if (savedInstanceState == null) {
            SoftPreconditions.checkState(
                    intent.getAction().equals(TvCommonConstants.INTENT_ACTION_INPUT_SETUP));
            if (DEBUG) Log.d(TAG, "TvInputId " + inputId + " / TvInputInfo " + mTvInputInfo);
            if (mTvInputInfo == null) {
                Log.w(TAG, "There is no input with the ID " + inputId + ".");
                finish();
                return;
            }
            Intent setupIntent =
                    intent.getExtras().getParcelable(TvCommonConstants.EXTRA_SETUP_INTENT);
            if (DEBUG) Log.d(TAG, "Setup activity launch intent: " + setupIntent);
            if (setupIntent == null) {
                Log.w(TAG, "The input (" + mTvInputInfo.getId() + ") doesn't have setup.");
                finish();
                return;
            }
            SetupUtils.grantEpgPermission(this, mTvInputInfo.getServiceInfo().packageName);
            if (DEBUG) Log.d(TAG, "Activity after completion " + mActivityAfterCompletion);
            // If EXTRA_SETUP_INTENT is not removed, an infinite recursion happens during
            // setupIntent.putExtras(intent.getExtras()).
            Bundle extras = intent.getExtras();
            extras.remove(TvCommonConstants.EXTRA_SETUP_INTENT);
            setupIntent.putExtras(extras);
            try {
                startActivityForResult(setupIntent, REQUEST_START_SETUP_ACTIVITY);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Can't find activity: " + setupIntent.getComponent());
                finish();
                return;
            }
            if (needToFetchEpg) {
                EpgFetcher.getInstance(this).onChannelScanStarted();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, final int resultCode, final Intent data) {
        if (DEBUG) Log.d(TAG, "onActivityResult");
        boolean setupComplete = requestCode == REQUEST_START_SETUP_ACTIVITY
                && resultCode == Activity.RESULT_OK;
        // Tells EpgFetcher that channel source setup is finished.
        if (mEpgFetcherDuringScan) {
            EpgFetcher.getInstance(this).onChannelScanFinished();
            mEpgFetcherDuringScan = false;
        }
        if (!setupComplete) {
            setResult(resultCode, data);
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
                setResult(resultCode, data);
                finish();
            }
        });
    }
}
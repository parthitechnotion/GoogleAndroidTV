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

package com.android.tv.onboarding;

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.common.WeakHandler;
import com.android.tv.common.ui.setup.SetupStep;
import com.android.tv.common.ui.setup.SteppedSetupActivity;
import com.android.tv.receiver.AudioCapabilitiesReceiver;
import com.android.tv.util.OnboardingUtils;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.SoftPreconditions;
import com.android.tv.util.Utils;

import java.util.concurrent.TimeUnit;

public class OnboardingActivity extends SteppedSetupActivity {
    private static final String TAG = "OnboardingActivity";

    private static final String KEY_INTENT_AFTER_COMPLETION = "key_intent_after_completion";

    private static final int MSG_CHECK_RECEIVED_AC3_CAPABILITY_NOTIFICATION = 1;
    private static final long AC3_CHECK_WAIT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    private static final int REQUEST_CODE_SETUP_USB_TUNER = 1;

    private Handler mHandler = new OnboardingActivityHandler(this);
    private AudioCapabilitiesReceiver mAudioCapabilitiesReceiver;
    private Boolean mAc3Supported;

    /**
     * Returns an intent to start {@link OnboardingActivity}.
     *
     * @param context context to create an intent. Should not be {@code null}.
     * @param intentAfterCompletion intent which will be used to start a new activity when this
     * activity finishes. Should not be {@code null}.
     */
    public static Intent buildIntent(@NonNull Context context,
            @NonNull Intent intentAfterCompletion) {
        return new Intent(context, OnboardingActivity.class)
                .putExtra(OnboardingActivity.KEY_INTENT_AFTER_COMPLETION, intentAfterCompletion);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register a receiver for HDMI audio plug and wait for the response.
        mAudioCapabilitiesReceiver = new AudioCapabilitiesReceiver(this,
                new AudioCapabilitiesReceiver.OnAc3PassthroughCapabilityChangeListener() {
                    @Override
                    public void onAc3PassthroughCapabilityChange(boolean capability) {
                        mAudioCapabilitiesReceiver.unregister();
                        mAudioCapabilitiesReceiver = null;
                        mHandler.removeMessages(MSG_CHECK_RECEIVED_AC3_CAPABILITY_NOTIFICATION);
                        mAc3Supported = capability;
                        startFirstStep();
                    }
        });
        mAudioCapabilitiesReceiver.register();
        mHandler.sendEmptyMessageDelayed(MSG_CHECK_RECEIVED_AC3_CAPABILITY_NOTIFICATION,
                AC3_CHECK_WAIT_TIMEOUT);
    }

    @Override
    protected SetupStep onCreateInitialStep() {
        if (mAc3Supported == null) {
            return null;
        }
        if (OnboardingUtils.isFirstRun(this)) {
            return new WelcomeStep(null);
        }
        return new AppOverviewStep(null);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mAudioCapabilitiesReceiver != null) {
            mAudioCapabilitiesReceiver.unregister();
            mAudioCapabilitiesReceiver = null;
        }
        super.onDestroy();
    }

    void startFirstStep() {
        SoftPreconditions.checkNotNull(mAc3Supported, TAG,
                "AC3 passthrough support check hasn't been completed yet.");
        startInitialStep();
    }


    private static class OnboardingActivityHandler extends WeakHandler<OnboardingActivity> {
        OnboardingActivityHandler(OnboardingActivity activity) {
            // Should run on main thread because onAc3SupportChanged will be called on main thread.
            super(Looper.getMainLooper(), activity);
        }

        @Override
        protected void handleMessage(Message msg, OnboardingActivity activity) {
            if (msg.what == MSG_CHECK_RECEIVED_AC3_CAPABILITY_NOTIFICATION) {
                activity.mAudioCapabilitiesReceiver.unregister();
                activity.mAudioCapabilitiesReceiver = null;
                activity.startFirstStep();
            }
        }
    }
    
    void finishActivity() {
        Intent intentForNextActivity = (Intent) getIntent().getParcelableExtra(
                KEY_INTENT_AFTER_COMPLETION);
        if (intentForNextActivity != null) {
            startActivity(intentForNextActivity);
        }
        finish();
    }

    private class WelcomeStep extends SetupStep {
        public WelcomeStep(@Nullable SetupStep previousStep) {
            super(getFragmentManager(), previousStep);
        }

        @Override
        public Fragment onCreateFragment() {
            return new WelcomeFragment();
        }

        @Override
        protected boolean needsToBeAddedToBackStack() {
            return false;
        }

        @Override
        protected boolean needsFragmentTransitionAnimation() {
            return false;
        }

        @Override
        public void executeAction(int actionId) {
            switch (actionId) {
                case WelcomeFragment.ACTION_NEXT:
                    OnboardingUtils.setFirstRunCompleted(OnboardingActivity.this);
                    if (!OnboardingUtils.areChannelsAvailable(OnboardingActivity.this)) {
                        startStep(new AppOverviewStep(this));
                    } else {
                        // TODO: Go to the correct step.
                        finishActivity();
                    }
                    break;
            }
        }
    }

    private class AppOverviewStep extends SetupStep {
        public AppOverviewStep(@Nullable SetupStep previousStep) {
            super(getFragmentManager(), previousStep);
        }

        @Override
        public Fragment onCreateFragment() {
            Fragment fragment = new AppOverviewFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean(AppOverviewFragment.KEY_AC3_SUPPORT, mAc3Supported);
            fragment.setArguments(bundle);
            return fragment;
        }

        @Override
        protected boolean needsToBeAddedToBackStack() {
            return false;
        }

        @Override
        public void executeAction(int actionId) {
            switch (actionId) {
                case AppOverviewFragment.ACTION_SETUP_SOURCE:
                    startStep(new SetupSourcesStep(this));
                    break;
                case AppOverviewFragment.ACTION_GET_MORE_CHANNELS:
                    // TODO: Implement this.
                    Toast.makeText(OnboardingActivity.this, "Not implemented yet.",
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    private class SetupSourcesStep extends SetupStep {
        public SetupSourcesStep(@Nullable SetupStep previousStep) {
            super(getFragmentManager(), previousStep);
        }

        @Override
        public Fragment onCreateFragment() {
            return new SetupSourcesFragment();
        }

        @Override
        public void executeAction(int actionId) {
            switch (actionId) {
                case SetupSourcesFragment.ACTION_DONE:
                    finishActivity();
                    break;
            }
        }
    }
}

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

package com.android.tv.common.ui.setup;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager.OnBackStackChangedListener;
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.android.tv.common.R;

/**
 * Stepped setup activity for onboarding screens or setup activity for TIS.
 */
public abstract class SteppedSetupActivity extends Activity implements OnActionClickListener {
    private boolean mStartedInitialStep = false;
    private SetupStep mStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Setup_GuidedStep);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stepped_setup);
        startInitialStep();
        getFragmentManager().addOnBackStackChangedListener(new OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                if (mStep != null) {
                    // Need to change step to the previous one if the current step is popped from
                    // the back stack.
                    if (mStep.needsToBeAddedToBackStack()
                            && getFragmentManager().getBackStackEntryCount()
                                    <= mStep.getPreviousBackStackRecordCount()) {
                        mStep = mStep.getPreviousStep();
                    }
                }
            }
        });
    }

    /**
     * Returns the current step.
     */
    public SetupStep getCurrentStep() {
        return mStep;
    }

    /**
     * The inherited class should provide the initial step.
     *
     * <p>If this method returns {@code null} during {@link #onCreate}, then call
     * {@link #startInitialStep} explicitly later with non null initial setup step.
     *
     * @see SetupStep
     */
    protected abstract SetupStep onCreateInitialStep();

    /**
     * Starts the initial step.
     *
     * <p>The inherited class can call this method later explicitly if it doesn't want the initial
     * step to be started in onCreate().
     *
     * @see SetupStep
     */
    protected void startInitialStep() {
        if (mStartedInitialStep) {
            return;
        }
        SetupStep step = onCreateInitialStep();
        if (step != null) {
            startStep(step);
            mStartedInitialStep = true;
        }
    }

    /**
     * Starts next step.
     */
    protected void startStep(SetupStep step) {
        mStep = step;
        Fragment fragment = step.onCreateFragment();
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (step.needsFragmentTransitionAnimation()) {
            ft.setCustomAnimations(SetupFragment.ANIM_ENTER, SetupFragment.ANIM_EXIT,
                    SetupFragment.ANIM_POP_ENTER, SetupFragment.ANIM_POP_EXIT);
        }
        if (step.needsToBeAddedToBackStack()) {
            ft.addToBackStack(null);
        }
        ft.replace(R.id.fragment_container, fragment).commit();
    }

    @Override
    public void onActionClick(int actionId) {
        mStep.executeAction(actionId);
    }
}

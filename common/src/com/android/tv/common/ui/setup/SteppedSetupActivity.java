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
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.View;

import com.android.tv.common.R;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;

/**
 * Stepped setup activity for onboarding screens or setup activity for TIS.
 *
 * <p>The inherited class should add theme {@code Theme.Setup.GuidedStep} to its definition in
 * AndroidManifest.xml.
 */
public abstract class SteppedSetupActivity extends Activity implements OnActionClickListener {
    private boolean mStartedInitialStep = false;
    private SetupStep mStep;
    private long mFragmentTransitionDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stepped_setup);
        startInitialStep();
        getFragmentManager().addOnBackStackChangedListener(new OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                if (mStep != null) {
                    // Need to change step to the previous one if the current step is popped from
                    // the back stack.
                    if (getFragmentManager().getBackStackEntryCount()
                                <= mStep.getPreviousBackStackRecordCount()) {
                        mStep = mStep.getPreviousStep();
                    }
                }
            }
        });
        mFragmentTransitionDuration = getResources().getInteger(
                R.integer.setup_fragment_transition_duration);
        SetupAnimationHelper.setFragmentTransitionDuration(mFragmentTransitionDuration);
        SetupAnimationHelper.setFragmentTransitionDistance(getResources().getDimensionPixelOffset(
                R.dimen.setup_fragment_transition_distance));
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
            startStep(step, false);
            mStartedInitialStep = true;
        }
    }

    /**
     * Starts next step.
     */
    protected FragmentTransaction startStep(SetupStep step, boolean addToBackStack) {
        mStep = step;
        Fragment fragment = step.createFragment();
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (fragment instanceof SetupFragment) {
            int[] sharedElements = ((SetupFragment) fragment).getSharedElementIds();
            if (sharedElements != null && sharedElements.length > 0) {
                Transition sharedTransition = TransitionInflater.from(this)
                        .inflateTransition(R.transition.transition_action_background);
                sharedTransition.setDuration(getSharedElementTransitionDuration());
                SetupAnimationHelper.applyAnimationTimeScale(sharedTransition);
                fragment.setSharedElementEnterTransition(sharedTransition);
                fragment.setSharedElementReturnTransition(sharedTransition);
                for (int id : sharedElements) {
                    View sharedView = findViewById(id);
                    if (sharedView != null) {
                        ft.addSharedElement(sharedView, sharedView.getTransitionName());
                    }
                }
            }
        }
        if (addToBackStack) {
            ft.addToBackStack(null);
        }
        ft.replace(R.id.fragment_container, fragment).commit();

        return ft;
    }

    @Override
    public void onActionClick(int actionId) {
        mStep.executeAction(actionId);
    }

    /**
     * Returns the duration of the shared element transition.
     *
     * <p>It's (exit transition) + (delayed animation) + (enter transition).
     */
    private long getSharedElementTransitionDuration() {
        return (mFragmentTransitionDuration + SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS) * 2;
    }
}

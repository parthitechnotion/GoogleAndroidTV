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

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.annotation.Nullable;

/**
 * An interface for the setup step.
 */
public abstract class SetupStep {
    private final SetupStep mPreviousStep;
    private final int mPreviousBackStackRecordCount;

    public SetupStep(FragmentManager fragmentManager, @Nullable SetupStep previousStep) {
        mPreviousStep = previousStep;
        mPreviousBackStackRecordCount = fragmentManager.getBackStackEntryCount();
    }

    /**
     * Returns fragment to represent this step.
     */
    protected abstract Fragment onCreateFragment();

    /**
     * Returns whether this step needs to be added to the back stack or not.
     *
     * <p>The default behavior is to add the fragment to the back stack.
     */
    protected boolean needsToBeAddedToBackStack() {
        return true;
    }

    /**
     * Returns whether this step needs fragment transition animations or not.
     *
     * <p>The default value is {@code} true.
     */
    protected boolean needsFragmentTransitionAnimation() {
        return true;
    }

    /**
     * Executes the given action.
     */
    public abstract void executeAction(int actionId);

    /**
     * Returns the back stack record count at the moment when this step starts.
     */
    public int getPreviousBackStackRecordCount() {
        return mPreviousBackStackRecordCount;
    }

    /**
     * Returns the previous step.
     */
    @Nullable
    public SetupStep getPreviousStep() {
        return mPreviousStep;
    }
}

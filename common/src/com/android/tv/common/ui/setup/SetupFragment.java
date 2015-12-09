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
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.transition.Transition;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.tv.common.ui.setup.animation.FadeAndShortSlide;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A fragment which slides when it is entering/exiting.
 */
public abstract class SetupFragment extends Fragment {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {FRAGMENT_ENTER_TRANSITION, FRAGMENT_EXIT_TRANSITION,
                    FRAGMENT_REENTER_TRANSITION, FRAGMENT_RETURN_TRANSITION})
    public @interface FragmentTransitionType {}
    protected static final int FRAGMENT_ENTER_TRANSITION = 0x01;
    protected static final int FRAGMENT_EXIT_TRANSITION = FRAGMENT_ENTER_TRANSITION << 1;
    protected static final int FRAGMENT_REENTER_TRANSITION = FRAGMENT_ENTER_TRANSITION << 2;
    protected static final int FRAGMENT_RETURN_TRANSITION = FRAGMENT_ENTER_TRANSITION << 3;

    public SetupFragment() {
        setAllowEnterTransitionOverlap(false);
        setAllowReturnTransitionOverlap(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResourceId(), container, false);
        // After the transition animation, we need to request the focus. If not, this fragment
        // doesn't have the focus.
        view.requestFocus();
        return view;
    }

    /**
     * Returns the layout resource ID for this fragment.
     */
    protected abstract int getLayoutResourceId();

    protected void setOnClickAction(View view, final int actionId) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onActionClick(actionId);
            }
        });
    }

    protected void onActionClick(int actionId) {
        SetupActionHelper.onActionClick(this, actionId);
    }

    /**
     * Enables fragment transition according to the given {@code mask}.
     *
     * @param mask This value is the combination of {@link #FRAGMENT_ENTER_TRANSITION},
     * {@link #FRAGMENT_EXIT_TRANSITION}, {@link #FRAGMENT_REENTER_TRANSITION}, and
     * {@link #FRAGMENT_RETURN_TRANSITION}.
     */
    public void enableFragmentTransition(@FragmentTransitionType int mask) {
        setEnterTransition((mask & FRAGMENT_ENTER_TRANSITION) == 0 ? null
                : createTransition(Gravity.END));
        setExitTransition((mask & FRAGMENT_EXIT_TRANSITION) == 0 ? null
                : createTransition(Gravity.START));
        setReenterTransition((mask & FRAGMENT_REENTER_TRANSITION) == 0 ? null
                : createTransition(Gravity.START));
        setReturnTransition((mask & FRAGMENT_RETURN_TRANSITION) == 0 ? null
                : createTransition(Gravity.END));
    }

    /**
     * Sets the transition with the given {@code slidEdge}.
     */
    protected void setFragmentTransition(@FragmentTransitionType int transitionType,
            int slideEdge) {
        switch (transitionType) {
            case FRAGMENT_ENTER_TRANSITION:
                setEnterTransition(createTransition(slideEdge));
                break;
            case FRAGMENT_EXIT_TRANSITION:
                setExitTransition(createTransition(slideEdge));
                break;
            case FRAGMENT_REENTER_TRANSITION:
                setReenterTransition(createTransition(slideEdge));
                break;
            case FRAGMENT_RETURN_TRANSITION:
                setReturnTransition(createTransition(slideEdge));
                break;
        }
    }

    private Transition createTransition(int slideEdge) {
        return new SetupAnimationHelper.TransitionBuilder()
                .setSlideEdge(slideEdge)
                .setParentIdsForDelay(getParentIdsForDelay())
                .setExcludeIds(getExcludedTargetIds())
                .build();
    }

    /**
     * Sets the distance of the fragment transition.
     */
    public void setTransitionDistance(int distance) {
        Transition transition = getEnterTransition();
        if (transition instanceof FadeAndShortSlide) {
            ((FadeAndShortSlide) transition).setDistance(distance);
        }
        transition = getExitTransition();
        if (transition instanceof FadeAndShortSlide) {
            ((FadeAndShortSlide) transition).setDistance(distance);
        }
        transition = getReenterTransition();
        if (transition instanceof FadeAndShortSlide) {
            ((FadeAndShortSlide) transition).setDistance(distance);
        }
        transition = getReturnTransition();
        if (transition instanceof FadeAndShortSlide) {
            ((FadeAndShortSlide) transition).setDistance(distance);
        }
    }

    /**
     * Sets the duration of the fragment transition.
     */
    public void setTransitionDuration(long duration) {
        Transition transition = getEnterTransition();
        if (transition != null) {
            transition.setDuration(duration);
        }
        transition = getExitTransition();
        if (transition != null) {
            transition.setDuration(duration);
        }
        transition = getReenterTransition();
        if (transition != null) {
            transition.setDuration(duration);
        }
        transition = getReturnTransition();
        if (transition != null) {
            transition.setDuration(duration);
        }
    }

    /**
     * Returns the ID's of the view's whose descendants will perform delayed move.
     *
     * @see com.android.tv.common.ui.setup.animation.SetupAnimationHelper.TransitionBuilder
     * #setParentIdsForDelay
     */
    protected int[] getParentIdsForDelay() {
        return null;
    }

    /**
     * Sets the ID's of the views which will not be included in the transition.
     *
     * @see com.android.tv.common.ui.setup.animation.SetupAnimationHelper.TransitionBuilder
     * #setExcludeIds
     */
    protected int[] getExcludedTargetIds() {
        return null;
    }

    /**
     * Returns the ID's of the shared elements.
     *
     * <p>Note that the shared elements should have their own transition names.
     */
    public int[] getSharedElementIds() {
        return null;
    }
}

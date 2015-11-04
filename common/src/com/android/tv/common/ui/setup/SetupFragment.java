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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.tv.common.R;

/**
 * A fragment which slides when it is entering/exiting.
 */
public abstract class SetupFragment extends Fragment {
    public static final int ANIM_ENTER = 1;
    public static final int ANIM_EXIT = 2;
    public static final int ANIM_POP_ENTER = 3;
    public static final int ANIM_POP_EXIT = 4;

    private static int sScreenWidth;

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

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (sScreenWidth == 0) {
            DisplayManager displayManager =
                    (DisplayManager) getActivity().getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            Point size = new Point();
            display.getSize(size);
            sScreenWidth = size.x;
        }

        switch (nextAnim) {
            case ANIM_ENTER:
                return createTranslateAnimator(sScreenWidth, 0);
            case ANIM_EXIT:
                return createTranslateAnimator(0, -sScreenWidth);
            case ANIM_POP_ENTER:
                return createTranslateAnimator(-sScreenWidth, 0);
            case ANIM_POP_EXIT:
                return createTranslateAnimator(0, sScreenWidth);
        }
        return super.onCreateAnimator(transit, enter, nextAnim);
    }

    private Animator createTranslateAnimator(int start, int end) {
        ObjectAnimator animator = new ObjectAnimator();
        animator.setProperty(View.TRANSLATION_X);
        animator.setFloatValues(start, end);
        animator.setDuration(getResources().getInteger(R.integer.setup_slide_anim_duration));
        animator.setInterpolator(new LinearOutSlowInInterpolator());
        return animator;
    }

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
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.tv.R;

public class ShadowContainer extends FrameLayout {

    private static final float UNSELECTED_SCALE = 1.0f;
    private static final int SCALE_ANIM_DURATION = 100;

    private float mSelectedScale;

    private final View mShadowViewNormal;
    private final View mShadowViewFocused;

    public ShadowContainer(Context context) {
        this(context, null);
    }

    public ShadowContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShadowContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mShadowViewNormal = inflater.inflate(R.layout.shadow_normal, this, false);
        mShadowViewFocused = inflater.inflate(R.layout.shadow_focused, this, false);
        addView(mShadowViewNormal, 0);
        addView(mShadowViewFocused, 1);
    }

    @Override
    public void onFinishInflate() {
        final Context ctx = getContext();
        final Resources res = ctx.getResources();

        ViewGroup.LayoutParams lp = getLayoutParams();
        int width = lp.width;
        int height = lp.height;
        if (width < 0 && height < 0) {
            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            height = getMeasuredHeight();
        }
        mSelectedScale = UNSELECTED_SCALE * getScalingFactor(res, width, height);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (isAttachedToWindow() && getVisibility() == View.VISIBLE) {
            if (gainFocus) {
                mShadowViewNormal.animate().alpha(0f).setDuration(SCALE_ANIM_DURATION);
                mShadowViewFocused.animate().alpha(1f).setDuration(SCALE_ANIM_DURATION);

                animate().scaleX(mSelectedScale)
                        .scaleY(mSelectedScale)
                        .setDuration(SCALE_ANIM_DURATION);
            } else {
                setScaleX(mSelectedScale);
                setScaleY(mSelectedScale);
                mShadowViewNormal.animate().alpha(1f).setDuration(SCALE_ANIM_DURATION);
                mShadowViewFocused.animate().alpha(0f).setDuration(SCALE_ANIM_DURATION);

                animate().scaleX(UNSELECTED_SCALE)
                        .scaleY(UNSELECTED_SCALE)
                        .setDuration(SCALE_ANIM_DURATION);
            }
        } else {
            clearAnimation();
            if (gainFocus) {
                setScaleX(mSelectedScale);
                setScaleY(mSelectedScale);
            } else {
                setScaleX(UNSELECTED_SCALE);
                setScaleY(UNSELECTED_SCALE);
            }
        }
    }

    private static float getScalingFactor(Resources res, float width, float height) {
        // for now, just return the appropriate zoom level based on the item size.
        // Ideally, we'll eventually just move to using the leanback focus selector
        // which will take care of this.
        float sizeMedium = res.getDimension(R.dimen.item_size_medium_threshold);
        float sizeLarge = res.getDimension(R.dimen.item_size_large_threshold);

        if (height < sizeMedium) {
            return res.getFraction(R.fraction.lb_focus_zoom_factor_large, 1, 1);
        } else if (height < sizeLarge) {
            return res.getFraction(R.fraction.lb_focus_zoom_factor_medium, 1, 1);
        } else {
            return res.getFraction(R.fraction.lb_focus_zoom_factor_small, 1, 1);
        }
    }
}

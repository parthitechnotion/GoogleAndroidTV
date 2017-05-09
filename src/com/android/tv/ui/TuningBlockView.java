/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;

/**
 * A view to block the screen while tuning channels.
 */
public class TuningBlockView extends FrameLayout{
    private final static String TAG = "TuningBlockView";

    private ImageView mImageView;
    private Animator mFadeOut;

    public TuningBlockView(Context context) {
        this(context, null, 0);
    }

    public TuningBlockView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TuningBlockView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = (ImageView) findViewById(R.id.image);
        mFadeOut = AnimatorInflater.loadAnimator(
                getContext(), R.animator.tuning_block_view_fade_out);
        mFadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(GONE);
            }
        });
        mFadeOut.setTarget(mImageView);
    }

    /**
     * Sets image to the image view. This method should be called after finishing inflate the view.
     */
    public void setImage(Drawable imageDrawable) {
        SoftPreconditions.checkState(mImageView != null, TAG, "imageView is null");
        mImageView.setImageDrawable(imageDrawable);
    }

    /**
     * Sets the visibility of image view.
     * This method should be called after finishing inflate the view.
     */
    public void setImageVisibility(boolean visible) {
        SoftPreconditions.checkState(mImageView != null, TAG, "imageView is null");
        mImageView.setAlpha(1.0f);
        mImageView.setVisibility(visible ? VISIBLE: GONE);
    }

    /**
     * Returns if the image view is visible.
     * This method should be called after finishing inflate the view.
     */
    public boolean isImageArtVisible() {
        SoftPreconditions.checkState(mImageView != null, TAG, "imageView is null");
        return mImageView.getVisibility() == VISIBLE;
    }

    /**
     * Hides the view with animation if needed.
     */
    public void hideWithAnimationIfNeeded() {
        if (getVisibility() == VISIBLE && isImageArtVisible()) {
            mFadeOut.start();
        } else {
            setVisibility(GONE);
        }
    }

    /**
     * Ends the fade out animator.
     */
    public void endFadeOutAnimator() {
        if (mFadeOut != null && mFadeOut.isRunning()) {
            mFadeOut.end();
        }
    }
}

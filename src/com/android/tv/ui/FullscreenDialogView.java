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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.dialog.FullscreenDialogFragment;

public class FullscreenDialogView extends FrameLayout
        implements FullscreenDialogFragment.DialogView {
    private static final int FADE_IN_DURATION_MS = 400;
    private static final int FADE_OUT_DURATION_MS = 300;
    private static final int TRANSITION_INTERVAL_MS = 300;

    private MainActivity mActivity;
    private Dialog mDialog;
    private boolean mSkipEnterAlphaAnimation;
    private boolean mSkipExitAlphaAnimation;
    private boolean mUseTranslationAnimation;

    private final int mEnterTranslationX;
    private final int mExitTranslationX;
    private final TimeInterpolator mLinearOutSlowIn;
    private final TimeInterpolator mFastOutLinearIn;

    public FullscreenDialogView(Context context) {
        this(context, null, 0);
    }

    public FullscreenDialogView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FullscreenDialogView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mEnterTranslationX = context.getResources().getInteger(
                R.integer.fullscreen_dialog_enter_translation_x);
        mExitTranslationX = context.getResources().getInteger(
                R.integer.fullscreen_dialog_exit_translation_x);
        mLinearOutSlowIn = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.linear_out_slow_in);
        mFastOutLinearIn = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.fast_out_linear_in);
    }

    public void setTransitionAnimationEnabled(boolean enable) {
        mUseTranslationAnimation = enable;
    }

    protected MainActivity getActivity() {
        return mActivity;
    }

    /**
     * Gets the host {@link Dialog}.
     */
    protected Dialog getDialog() {
        return mDialog;
    }

    /**
     * Dismisses the host {@link Dialog}.
     */
    protected void dismiss() {
        startExitAnimation(new Runnable() {
            @Override
            public void run() {
                mDialog.dismiss();
            }
        });
    }

    @Override
    public void initialize(MainActivity activity, Dialog dialog) {
        mActivity = activity;
        mDialog = dialog;
    }

    @Override
    public void onBackPressed() { }

    @Override
    public void onDestroy() { }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startEnterAnimation();
    }

    /**
     * Transitions to another view inside the host {@link Dialog}.
     */
    public void transitionTo(final FullscreenDialogView v) {
        mSkipExitAlphaAnimation = true;
        v.mSkipEnterAlphaAnimation = true;
        v.initialize(mActivity, mDialog);
        startExitAnimation(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        v.initialize(getActivity(), getDialog());
                        getDialog().setContentView(v);
                    }
                }, TRANSITION_INTERVAL_MS);
            }
        });
    }

    private void startEnterAnimation() {
        View v = findViewById(R.id.container);
        if (mUseTranslationAnimation) {
            v.setTranslationX(mEnterTranslationX);
            v.animate()
                    .translationX(0)
                    .setInterpolator(mLinearOutSlowIn)
                    .setDuration(FADE_IN_DURATION_MS)
                    .setListener(new HardwareLayerAnimatorListenerAdapter(this))
                    .start();
        }
        if (!mSkipEnterAlphaAnimation) {
            setAlpha(0);
            animate()
                    .alpha(1.0f)
                    .setInterpolator(mLinearOutSlowIn)
                    .setDuration(FADE_IN_DURATION_MS)
                    .setListener(new HardwareLayerAnimatorListenerAdapter(this))
                    .start();
        } else {
            v.setAlpha(0);
            v.animate()
                    .alpha(1.0f)
                    .setInterpolator(mLinearOutSlowIn)
                    .setDuration(FADE_IN_DURATION_MS)
                    .setListener(new HardwareLayerAnimatorListenerAdapter(this))
                    .start();
        }
    }

    private void startExitAnimation(final Runnable onAnimationEnded) {
        View v = findViewById(R.id.container);
        if (mUseTranslationAnimation) {
            v.animate()
                    .translationX(mExitTranslationX)
                    .setInterpolator(mFastOutLinearIn)
                    .setDuration(FADE_OUT_DURATION_MS)
                    .setListener(new HardwareLayerAnimatorListenerAdapter(this))
                    .start();
        }
        if (!mSkipExitAlphaAnimation) {
            animate()
                    .alpha(0.0f)
                    .setInterpolator(mFastOutLinearIn)
                    .setDuration(FADE_OUT_DURATION_MS)
                    .setListener(new HardwareLayerAnimatorListenerAdapter(this) {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            onAnimationEnded.run();
                        }
                    })
                    .start();
        } else {
            v.animate()
                    .alpha(0.0f)
                    .setInterpolator(mFastOutLinearIn)
                    .setDuration(FADE_OUT_DURATION_MS)
                    .setListener(new HardwareLayerAnimatorListenerAdapter(this) {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            onAnimationEnded.run();
                        }
                    })
                    .start();
        }
    }
}

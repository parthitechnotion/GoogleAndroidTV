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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.android.tv.R;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A page indicator with dots.
 */
public class PagingIndicator extends View {
    // attribute
    private final int mDotDiameter;
    private final int mDotRadius;
    private final int mDotGap;
    private int[] mDotCenterX;
    private int mDotCenterY;

    // state
    private int mPageCount;
    private int mCurrentPage;
    private int mPreviousPage;

    // drawing
    private final Paint mUnselectedPaint;
    private final Paint mSelectedPaint;
    private final Paint mUnselectingPaint;
    private final Paint mSelectingPaint;
    private final AnimatorSet mAnimator = new AnimatorSet();

    public PagingIndicator(Context context) {
        this(context, null, 0);
    }

    public PagingIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagingIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Resources res = getResources();
        mDotRadius = res.getDimensionPixelSize(R.dimen.onboarding_dot_radius);
        mDotDiameter = mDotRadius * 2;
        mDotGap = res.getDimensionPixelSize(R.dimen.onboarding_dot_gap);
        mUnselectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Deprecated method is used because this code should run on L platform.
        int unselectedColor = Utils.getColor(res, R.color.onboarding_dot_unselected);
        int selectedColor = Utils.getColor(res, R.color.onboarding_dot_selected);
        mUnselectedPaint.setColor(unselectedColor);
        mSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSelectedPaint.setColor(selectedColor);
        mUnselectingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSelectingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Initialize animations.
        int duration = res.getInteger(R.integer.setup_slide_anim_duration);
        List<Animator> animators = new ArrayList<>();
        animators.add(createColorAnimator(selectedColor, unselectedColor, duration,
                mUnselectingPaint));
        animators.add(createColorAnimator(unselectedColor, selectedColor, duration,
                mSelectingPaint));
        mAnimator.playTogether(animators);
    }

    private Animator createColorAnimator(int fromColor, int toColor, int duration,
            final Paint paint) {
        ValueAnimator animator = ValueAnimator.ofArgb(fromColor, toColor);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                invalidate();
            }
        });
        animator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                paint.setColor((int) animator.getAnimatedValue());
                invalidate();
            }
        });
        return animator;
    }

    /**
     * Sets the page count.
     */
    public void setPageCount(int pages) {
        mPageCount = pages;
        calculateDotPositions();
        setSelectedPage(0);
    }

    /**
     * Called when the page has been selected.
     */
    public void onPageSelected(int pageIndex, boolean withAnimation) {
        if (mAnimator.isStarted()) {
            mAnimator.end();
        }
        if (withAnimation) {
            mPreviousPage = mCurrentPage;
            mAnimator.start();
        }
        setSelectedPage(pageIndex);
    }

    private void calculateDotPositions() {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getWidth() - getPaddingRight();
        int requiredWidth = getRequiredWidth();
        int startLeft = left + ((right - left - requiredWidth) / 2) + mDotRadius;
        mDotCenterX = new int[mPageCount];
        for (int i = 0; i < mPageCount; i++) {
            mDotCenterX[i] = startLeft + i * (mDotDiameter + mDotGap);
        }
        mDotCenterY = top + mDotRadius;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = getDesiredHeight();
        int height;
        switch (MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.EXACTLY:
                height = MeasureSpec.getSize(heightMeasureSpec);
                break;
            case MeasureSpec.AT_MOST:
                height = Math.min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec));
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                height = desiredHeight;
                break;
        }
        int desiredWidth = getDesiredWidth();
        int width;
        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.EXACTLY:
                width = MeasureSpec.getSize(widthMeasureSpec);
                break;
            case MeasureSpec.AT_MOST:
                width = Math.min(desiredWidth, MeasureSpec.getSize(widthMeasureSpec));
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                width = desiredWidth;
                break;
        }
        setMeasuredDimension(width, height);
        calculateDotPositions();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        setMeasuredDimension(width, height);
        calculateDotPositions();
    }

    private int getDesiredHeight() {
        return getPaddingTop() + mDotDiameter + getPaddingBottom();
    }

    private int getRequiredWidth() {
        return mPageCount * mDotDiameter + (mPageCount - 1) * mDotGap;
    }

    private int getDesiredWidth() {
        return getPaddingLeft() + getRequiredWidth() + getPaddingRight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawUnselected(canvas);
        if (mAnimator.isStarted()) {
            drawAnimator(canvas);
        } else {
            drawSelected(canvas);
        }
    }

    private void drawUnselected(Canvas canvas) {
        for (int page = 0; page < mPageCount; page++) {
            canvas.drawCircle(mDotCenterX[page], mDotCenterY, mDotRadius, mUnselectedPaint);
        }
    }

    private void drawSelected(Canvas canvas) {
        canvas.drawCircle(mDotCenterX[mCurrentPage], mDotCenterY, mDotRadius, mSelectedPaint);
    }

    private void drawAnimator(Canvas canvas) {
        canvas.drawCircle(mDotCenterX[mPreviousPage], mDotCenterY, mDotRadius, mUnselectingPaint);
        canvas.drawCircle(mDotCenterX[mCurrentPage], mDotCenterY, mDotRadius, mSelectingPaint);
    }

    private void setSelectedPage(int now) {
        if (now == mCurrentPage) {
            return;
        }
        mCurrentPage = now;
        invalidate();
    }
}

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

package com.android.tv.menu;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.menu.MenuView.MenuShowReason;

public abstract class MenuRowView extends LinearLayout {
    private static final String TAG = MenuRowView.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * For setting ListView visible, and TitleView visible with the selected text size and color
     * without animation.
     */
    public static final int ANIM_NONE_SELECTED = 1;
    /**
     * For setting ListView gone, and TitleView visible with the deselected text size and color
     * without animation.
     */
    public static final int ANIM_NONE_DESELECTED = 2;
    /**
     * An animation for the selected item list view.
     */
    public static final int ANIM_SELECTED = 3;
    /**
     * An animation for the deselected item list view.
     */
    public static final int ANIM_DESELECTED = 4;

    private TextView mTitleView;
    private View mContentsView;
    private MenuView mMenuView;
    private VerticalGridView mParentView;
    private boolean mIsSelected;

    private final float mTitleScaleSelected;
    private final float mTitleAlphaSelected;
    private final float mTitleAlphaDeselected;

    /**
     * The lastly focused view. It is used to keep the focus while navigating the menu rows and
     * reset when the menu is popped up.
     */
    private View mLastFocusView;
    private MenuRow mRow;

    private final OnFocusChangeListener mOnFocusChangeListener = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            onChildFocusChange(v, hasFocus);
        }
    };

    public MenuRowView(Context context) {
        this(context, null);
    }

    public MenuRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MenuRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MenuRowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mTitleScaleSelected = getTitleScaleSelected();
        mTitleAlphaSelected = getTitleAlphaSelected();
        TypedValue outValue = new TypedValue();
        context.getResources().getValue(
                R.dimen.menu_row_title_alpha_deselected, outValue, true);
        mTitleAlphaDeselected = outValue.getFloat();
    }

    protected float getTitleScaleSelected() {
        Resources res = getContext().getResources();
        int textSizeSelected =
                res.getDimensionPixelSize(R.dimen.menu_row_title_text_size_selected);
        int textSizeDeselected =
                res.getDimensionPixelSize(R.dimen.menu_row_title_text_size_deselected);
        return (float) textSizeSelected / textSizeDeselected;
    }

    protected float getTitleAlphaSelected() {
        return 1.0f;
    }

    @Override
    protected void onFinishInflate() {
        mTitleView = (TextView) findViewById(R.id.title);
        mContentsView = findViewById(getContentsViewId());
        if (mContentsView.isFocusable()) {
            mContentsView.setOnFocusChangeListener(mOnFocusChangeListener);
        }
        if (mContentsView instanceof ViewGroup) {
            setOnFocusChangeListenerToChildren((ViewGroup) mContentsView);
        }
    }

    private void setOnFocusChangeListenerToChildren(ViewGroup parent) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View child = parent.getChildAt(i);
            if (child.isFocusable()) {
                child.setOnFocusChangeListener(mOnFocusChangeListener);
            }
            if (child instanceof ViewGroup) {
                setOnFocusChangeListenerToChildren((ViewGroup) child);
            }
        }
    }

    abstract protected int getContentsViewId();

    protected View getContentsView() {
        return mContentsView;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateView(mParentView.getChildAdapterPosition(this) == mParentView.getSelectedPosition()
                ? ANIM_NONE_SELECTED : ANIM_NONE_DESELECTED);
    }

    /**
     * Initialize this view. e.g. Set the initial selection.
     * This method is called when the main menu is visible.
     * Subclass of {@link MenuRowView} should override this to set correct mLastFocusView.
     *
     * @param reason A reason why this is initialized. See {@link MenuShowReason}
     */
    public void initialize(@MenuShowReason int reason) {
        mLastFocusView = null;
    }

    private void updateView(int animationType) {
        boolean isSelected = animationType == ANIM_SELECTED || animationType == ANIM_NONE_SELECTED;
        if (mIsSelected && isSelected) {
            // Prevent from selected again so later calls to {@link updateView} cancels animation.
            return;
        }
        mIsSelected = isSelected;
        updateRowView(animationType);
        updateTitleView(animationType);
    }

    private void updateRowView(int animationType) {
        mContentsView.animate().cancel();
        mContentsView.setAlpha(1f);
        switch (animationType) {
            case ANIM_NONE_SELECTED: {
                mContentsView.setVisibility(View.VISIBLE);
                break;
            }
            case ANIM_NONE_DESELECTED: {
                mContentsView.setVisibility(View.GONE);
                break;
            }
            case ANIM_SELECTED: {
                mContentsView.setVisibility(View.VISIBLE);
                mContentsView.setAlpha(0f);
                mContentsView.animate()
                        .alpha(1f)
                        .setDuration(getMenuView().getRowSelectionAnimationDurationMs())
                        .withLayer();
                break;
            }
            case ANIM_DESELECTED: {
                mContentsView.setVisibility(View.GONE);
                break;
            }
        }
    }

    private void updateTitleView(int animationType) {
        boolean withAnimation = animationType == ANIM_SELECTED || animationType == ANIM_DESELECTED;
        int duration = withAnimation ? getMenuView().getRowSelectionAnimationDurationMs() : 0;

        mTitleView.animate().cancel();
        switch (animationType) {
            case ANIM_SELECTED:
                mTitleView.animate()
                        .alpha(mTitleAlphaSelected)
                        .scaleX(mTitleScaleSelected)
                        .scaleY(mTitleScaleSelected)
                        .setDuration(duration)
                        .withLayer();
                break;
            case ANIM_NONE_SELECTED:
                mTitleView.setAlpha(mTitleAlphaSelected);
                mTitleView.setScaleX(mTitleScaleSelected);
                mTitleView.setScaleY(mTitleScaleSelected);
                break;
            case ANIM_DESELECTED:
                mTitleView.animate()
                        .alpha(mTitleAlphaDeselected)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .withLayer();
                break;
            case ANIM_NONE_DESELECTED:
                mTitleView.setAlpha(mTitleAlphaDeselected);
                mTitleView.setScaleX(1f);
                mTitleView.setScaleY(1f);
                break;
        }
    }

    /**
     * Updates the view contents.
     * This method is called when the row is selected.
     */
    public void updateView(boolean withAnimation) {
        int position = mParentView.getChildAdapterPosition(this);
        int selectedPosition = mParentView.getSelectedPosition();
        int animationType = ANIM_NONE_DESELECTED;
        if (withAnimation) {
            boolean scrollUp = mMenuView.getPreviousSelectedPosition() > selectedPosition;
            switch (position - selectedPosition) {
                case -2:
                    animationType = ANIM_NONE_DESELECTED;
                    break;
                case -1:
                    animationType = scrollUp ? ANIM_NONE_DESELECTED : ANIM_DESELECTED;
                    break;
                case 0:
                    animationType = ANIM_SELECTED;
                    break;
                case 1:
                    animationType = scrollUp ? ANIM_DESELECTED : ANIM_NONE_DESELECTED;
                    break;
                case 2:
                    animationType = ANIM_NONE_DESELECTED;
                    break;
            }
        } else {
            animationType = (position == selectedPosition)
                    ? ANIM_NONE_SELECTED : ANIM_NONE_DESELECTED;
        }
        updateView(animationType);
    }

    protected MenuView getMenuView() {
        return mMenuView;
    }

    public void setMenuView(MenuView view) {
        mMenuView = view;
    }

    public void setParentView(VerticalGridView view) {
        mParentView = view;
    }

    public void onBind(MenuRow row) {
        if (DEBUG) Log.d(TAG, "onBind: row=" + row);
        mRow = row;
        mTitleView.setText(row.getTitle());

        // mListView includes paddings to avoid an artifact while alpha animation.
        // See res/layout/item_list.xml for more information.
        ViewGroup.LayoutParams lp = mContentsView.getLayoutParams();
        lp.height = row.getHeight() + mMenuView.getItemPaddingHeight()
                - getContext().getResources().getDimensionPixelSize(
                        R.dimen.menu_list_margin_bottom);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // Expand view here so initial focused item can be shown.
        updateView(ANIM_SELECTED);
        return getInitialFocusView().requestFocus();
    }

    @NonNull
    private View getInitialFocusView() {
        if (mLastFocusView == null) {
            return mContentsView;
        }
        return mLastFocusView;
    }

    /**
     * Sets the view which needs to have focus when this row appears.
     * Subclasses should call this in {@link #initialize} if needed.
     */
    protected void setInitialFocusView(@NonNull View v) {
        mLastFocusView = v;
    }

    /**
     * Called when the focus of a child view is changed.
     * The inherited class should override this method instead of calling
     * {@link android.view.View#setOnFocusChangeListener(android.view.View.OnFocusChangeListener)}.
     */
    protected void onChildFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            mLastFocusView = v;
        }
    }

    /**
     * Returns the ID of row object bound to this view.
     */
    public String getRowId() {
        return mRow == null ? null : mRow.getId();
    }
}

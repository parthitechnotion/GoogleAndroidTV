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
import android.graphics.Rect;
import android.support.v17.leanback.widget.BaseCardView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;

/**
 * A view to render an item of TV options.
 */
public class ActionTileView extends BaseCardView implements ItemListView.TileView {
    private static final String TAG = "ActionTileView";
    private static final boolean DEBUG = false;

    private View mCircle;
    private int mAnimDuration;
    private TextView mLabelView;
    private ImageView mIconView;

    public ActionTileView(Context context) {
        super(context);
    }

    public ActionTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionTileView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAnimDuration = context.getResources().getInteger(R.integer.action_item_anim_duration);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCircle = findViewById(R.id.action_tile_selection_circle);
    }

    @Override
    public void loadViews() {
        mLabelView = (TextView) findViewById(R.id.action_tile_label);
        mIconView = (ImageView) findViewById(R.id.action_tile_icon);
    }

    @Override
    public void populateViews(View.OnClickListener listener, Object item) {
        Preconditions.checkNotNull(item);

        MenuAction action = (MenuAction) item;
        setOnClickListener(listener);
        setTag(MainMenuView.MenuTag.buildTag(action));

        mLabelView.setText(action.getActionName(getContext()));
        mIconView.setImageDrawable(getContext().getDrawable(action.getDrawableResId()));
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        setSelected(gainFocus);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        if (mCircle != null) {
            mCircle.animate()
                    .alpha(selected ? 1.0f : 0.0f)
                    .setDuration(mAnimDuration);
        }
    }
}

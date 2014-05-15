// Copyright 2014 Google Inc. All Rights Reserved.

package com.android.tv.ui;

import com.android.internal.util.Preconditions;
import com.android.tv.Channel;
import com.android.tv.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A view to render channel banner.
 */
public class ActionTileView extends LinearLayout implements ItemListView.TileView {

    private static final String TAG = "ChannelTileView";

    private TextView mActionNameView;

    public ActionTileView(Context context) {
        super(context);
    }

    public ActionTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionTileView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void loadViews() {
        mActionNameView = (TextView) findViewById(R.id.action_name);
    }

    @Override
    public void populateViews(View.OnClickListener listener, Object item) {
        Preconditions.checkNotNull(item);

        MenuAction action = (MenuAction) item;
        setOnClickListener(listener);
        setTag(MainMenuView.MenuTag.buildTag(action));

        mActionNameView.setText(action.getActionName(getContext()));
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        mActionNameView.setBackgroundColor(gainFocus
                ? getContext().getResources().getColor(R.color.selected_item_background)
                : Color.WHITE);
    }
}
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
 * A view to render channel tile.
 */
public class ChannelTileView extends LinearLayout implements ItemListView.TileView {

    private static final String TAG = "ChannelTileView";

    private TextView mChannelNameView;
    private TextView mChannelNumberView;

    public ChannelTileView(Context context) {
        super(context);
    }

    public ChannelTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChannelTileView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void loadViews() {
        mChannelNameView = (TextView) findViewById(R.id.channel_name);
        mChannelNumberView = (TextView) findViewById(R.id.channel_number);
    }

    @Override
    public void populateViews(View.OnClickListener listener, Object item) {
        Preconditions.checkNotNull(item);

        Channel channel = (Channel) item;
        setOnClickListener(listener);
        setTag(MainMenuView.MenuTag.buildTag(channel));

        mChannelNameView.setText(channel.getDisplayName());
        mChannelNumberView.setText(channel.getDisplayNumber());
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        mChannelNameView.setBackgroundColor(gainFocus
                ? getContext().getResources().getColor(R.color.selected_item_background)
                : Color.WHITE);
    }
}
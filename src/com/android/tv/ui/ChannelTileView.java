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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;
import com.android.tv.data.Channel;

/**
 * A view to render channel tile.
 */
public class ChannelTileView extends ShadowContainer implements ItemListView.TileView {
    private ImageView mChannelLogoView;
    private TextView mChannelNameView;
    private TextView mChannelNumberView;
    private TextView mProgramNameView;

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
        mChannelLogoView = (ImageView) findViewById(R.id.channel_logo);
        mChannelNameView = (TextView) findViewById(R.id.channel_name);
        mChannelNumberView = (TextView) findViewById(R.id.channel_number);
        mProgramNameView = (TextView) findViewById(R.id.program_name);
        mChannelNameView.setVisibility(INVISIBLE);
    }

    @Override
    public void populateViews(View.OnClickListener listener, Object item) {
        Preconditions.checkNotNull(item);

        Channel channel = (Channel) item;
        setOnClickListener(listener);
        setTag(MainMenuView.MenuTag.buildTag(channel));

        mChannelNameView.setText(channel.getDisplayName());
        // TODO: setVisibility of mChannelNameView and mChannelLogoView properly.
        if (mChannelNameView.getVisibility() == INVISIBLE) {
            mChannelLogoView.setVisibility(INVISIBLE);
            mChannelNameView.setVisibility(VISIBLE);
        } else {
            mChannelNameView.setVisibility(INVISIBLE);
            mChannelLogoView.setVisibility(VISIBLE);
        }

        mChannelNumberView.setText(channel.getDisplayNumber());
        // TODO: mProgramNameView should be shown with a program title.
        if (Math.random() < 0.5) {
            mProgramNameView.setVisibility(VISIBLE);
            mProgramNameView.setText(channel.getDisplayName());
        } else {
            mProgramNameView.setVisibility(GONE);
        }
    }
}
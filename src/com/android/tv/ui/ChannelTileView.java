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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.util.Utils;

/**
 * A view to render channel tile.
 */
public class ChannelTileView extends ShadowContainer implements ItemListView.TileView {
    private ImageView mChannelLogoView;
    private TextView mChannelNameView;
    private TextView mChannelNumberView;
    private TextView mProgramNameView;
    private Channel mChannel;

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

        mChannel = (Channel) item;
        setOnClickListener(listener);
        setTag(MainMenuView.MenuTag.buildTag(mChannel));

        if (mChannel.getType() == R.integer.channel_type_guide) {
            mChannelNumberView.setVisibility(INVISIBLE);
            mChannelNameView.setVisibility(INVISIBLE);
            mChannelLogoView.setImageResource(R.drawable.ic_channel_guide);
            mChannelLogoView.setVisibility(VISIBLE);
            mProgramNameView.setText(R.string.menu_program_guide);
        } else {
            mChannelNumberView.setText(mChannel.getDisplayNumber());
            mChannelNumberView.setVisibility(VISIBLE);
            mChannelNameView.setText(mChannel.getDisplayName());
            mChannelNameView.setVisibility(VISIBLE);
            // TODO: need to set up mChannelLogoView when log image is available.
            mChannelLogoView.setVisibility(INVISIBLE);

            updateProgramInformation();
        }
    }

    public void updateProgramInformation() {
        if (mProgramNameView == null || mChannel == null
                || mChannel.getType() == R.integer.channel_type_guide) {
            return;
        }

        Program program = Utils.getCurrentProgram(getContext(),
                Utils.getChannelUri(mChannel.getId()));
        if (program == null || TextUtils.isEmpty(program.getTitle())) {
            mProgramNameView.setText(getContext().getText(R.string.no_program_information));
        } else {
            mProgramNameView.setText(program.getTitle());
        }
    }
}

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

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Rect;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.AspectRatio;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.data.Program;
import com.android.tv.util.Utils;

public class SimpleGuideFragment extends BaseSideFragment {
    private static final String TAG = "SimpleGuideFragment";
    private static final boolean DEBUG = true;

    private final TvActivity mTvActivity;
    private final ChannelMap mChannelMap;
    private int mCurPosition;
    private boolean mClosingByItemSelected;
    private int mFocusedBgColor;
    private int mBgColor;

    public SimpleGuideFragment(TvActivity tvActivity, ChannelMap channelMap) {
        super();
        mTvActivity = tvActivity;
        mChannelMap = channelMap;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mBgColor = getActivity().getResources().getColor(R.color.simple_guide_fragment_background);
        mFocusedBgColor = getActivity().getResources().getColor(
                R.color.simple_guide_fragment_focused_background);

        // TODO: add 'Show only' menu.
        initialize(getString(R.string.simple_guide_title), mChannelMap.getChannelList(true),
                R.layout.simple_guide_fragment, R.layout.simple_guide_item);
        View fragView = super.onCreateView(inflater, container, savedInstanceState);
        mCurPosition = getCurrentChannelPosition();
        setPrevSelectedItemPosition(mCurPosition);
        return fragView;
    }

    @Override
    public void onResume() {
        super.onResume();
        setSelectedPosition(mCurPosition);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (!mClosingByItemSelected) {
            ((TvActivity) getActivity()).onSideFragmentCanceled(getInitiator());
        }
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemFocusChanged " + focusGained + ": position=" + position
                + ", label=" + ((Channel) tag).getDisplayName());
        mCurPosition = position;
        v.setBackgroundColor(focusGained ? mFocusedBgColor : mBgColor);
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemSelected: position=" + position + ", label="
                + ((Channel) tag).getDisplayName());
        if (tag instanceof Channel) {
            mTvActivity.moveToChannel(((Channel) tag).getId());
        } else {
            // TODO: implement this ('Show only' menu).
        }
        mClosingByItemSelected = true;
        getFragmentManager().popBackStack();
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        TextView programTitleView = (TextView) v.findViewById(R.id.program_title);
        ImageView channelLogoView = (ImageView) v.findViewById(R.id.channel_logo);
        TextView channelNumberView = (TextView) v.findViewById(R.id.channel_number);
        String text = "";
        if (tag instanceof Channel) {
            Channel channel = (Channel) tag;
            channelNumberView.setText(channel.getDisplayNumber());
            channelNumberView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelOffset(
                            R.dimen.simple_guide_item_large_text_size));
            // TODO: show channel logo and adjust the text size of channelNumberView.
            channelLogoView.setVisibility(View.GONE);
            Program program = Utils.getCurrentProgram(mTvActivity,
                    ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channel.getId()));
            text = program.getTitle();
            if (TextUtils.isEmpty(text)) {
                text = "[" + getResources().getString(R.string.no_program_information) + "]";
            }
        } else {
            // TODO: implement this ('Show only' menu).
        }
        if (prevSelected) {
            text += " (Selected)";
        }
        programTitleView.setText(text);
    }

    private int getCurrentChannelPosition() {
        Channel[] channels = mChannelMap.getChannelList(true);
        long curChannelId = mChannelMap.getCurrentChannelId();
        int curChannelPos = 0;
        for (int i = 0; i < channels.length; ++i) {
            if (channels[i].getId() == curChannelId) {
                curChannelPos = i;
                break;
            }
        }
        return curChannelPos;
    }
}

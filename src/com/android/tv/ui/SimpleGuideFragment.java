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

import android.app.FragmentTransaction;
import android.content.ContentUris;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.data.Program;
import com.android.tv.data.ShowOnlyItems;
import com.android.tv.util.Utils;

public class SimpleGuideFragment extends BaseSideFragment {
    private static final String TAG = "SimpleGuideFragment";
    private static final boolean DEBUG = true;
    private static final int SHOW_ONLY_ITEM_DEFAULT_POSITION = ShowOnlyItems.POSITION_ALL_CHANNELS;

    private final TvActivity mTvActivity;
    private final ChannelMap mChannelMap;
    private int mCurPosition;
    private boolean mClosingByItemSelected;
    private int mBgColor;
    private int mFocusedBgColor;
    // TODO: user shared preference for this.
    private final int mSelectedShowOnlyItemPosition;

    public SimpleGuideFragment(TvActivity tvActivity, ChannelMap channelMap) {
        super();
        mTvActivity = tvActivity;
        mChannelMap = channelMap;
        mSelectedShowOnlyItemPosition = SHOW_ONLY_ITEM_DEFAULT_POSITION;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mBgColor = getActivity().getResources().getColor(R.color.simple_guide_fragment_background);
        mFocusedBgColor = getActivity().getResources().getColor(
                R.color.simple_guide_fragment_focused_background);

        Channel[] channels = mChannelMap.getChannelList(true);
        Object[] itemTags = new Object[channels.length + 1];
        itemTags[0] = new Object(); // a dummy object for the show only menu.
        for (int i = 0; i < channels.length; ++i) {
            itemTags[i + 1] = channels[i];
        }

        initialize(getString(R.string.simple_guide_title), itemTags,
                R.layout.simple_guide_fragment, R.layout.simple_guide_item, false);
        mCurPosition = getCurrentChannelPosition();
        setPrevSelectedItemPosition(mCurPosition);
        return super.onCreateView(inflater, container, savedInstanceState);
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
                + ", label=" + tag);
        mCurPosition = position;
        v.setBackgroundColor(focusGained ? mFocusedBgColor : mBgColor);
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemSelected: position=" + position + ", label=" + tag);
        if (tag instanceof Channel) {
            mTvActivity.moveToChannel(((Channel) tag).getId());
            mClosingByItemSelected = true;
            getFragmentManager().popBackStack();
        } else {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(R.id.right_panel, new SimpleGuideShowOnlyFragment());
            ft.addToBackStack(null);
            // TODO: add an animation.
            ft.commit();
        }
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        TextView programTitleView = (TextView) v.findViewById(R.id.program_title);
        TextView programInfoView = (TextView) v.findViewById(R.id.program_info);
        ImageView channelLogoView = (ImageView) v.findViewById(R.id.channel_logo);
        TextView channelNumberView = (TextView) v.findViewById(R.id.channel_number);
        TextView channelNameView = (TextView) v.findViewById(R.id.channel_name);
        String text = "";
        if (tag instanceof Channel) {
            Channel channel = (Channel) tag;
            channelNumberView.setText(channel.getDisplayNumber());
            // TODO: show channel logo if possible.
            channelLogoView.setVisibility(View.GONE);
            channelNameView.setText(channel.getDisplayName());
            Program program = Utils.getCurrentProgram(mTvActivity,
                    ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channel.getId()));
            text = program.getTitle();
            if (TextUtils.isEmpty(text)) {
                text = "[" + getResources().getString(R.string.no_program_information) + "]";
            }
            if (prevSelected) {
                text += " (Selected)";
            }
            programTitleView.setText(text);
            programInfoView.setVisibility(View.GONE);
        } else {
            channelNumberView.setText("");
            channelNumberView.setVisibility(View.VISIBLE);
            channelLogoView.setVisibility(View.GONE);
            programTitleView.setText(getString(R.string.show_only_title));
            programInfoView.setVisibility(View.VISIBLE);
            programInfoView.setText(ShowOnlyItems.getLabel(
                    mSelectedShowOnlyItemPosition, mTvActivity));
        }
    }

    private int getCurrentChannelPosition() {
        Channel[] channels = mChannelMap.getChannelList(true);
        long curChannelId = mChannelMap.getCurrentChannelId();
        int curChannelPos = 1;
        for (int i = 0; i < channels.length; ++i) {
            if (channels[i].getId() == curChannelId) {
                curChannelPos = i + 1;
                break;
            }
        }
        return curChannelPos;
    }
}

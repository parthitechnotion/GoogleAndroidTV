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
import android.content.Context;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.data.Program;
import com.android.tv.data.ShowOnlyItems;
import com.android.tv.data.Channel.LoadLogoCallback;
import com.android.tv.util.Utils;

public class SimpleGuideFragment extends BaseSideFragment {
    private static final String TAG = "SimpleGuideFragment";
    private static final boolean DEBUG = true;
    private static final int SHOW_ONLY_ITEM_DEFAULT_POSITION = ShowOnlyItems.POSITION_ALL_CHANNELS;

    private final TvActivity mTvActivity;
    private final ChannelMap mChannelMap;
    private int mCurPosition;
    private boolean mClosingByItemSelected;
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
        Channel[] channels = mChannelMap.getChannelList(true);
        Object[] itemTags = new Object[channels.length + 1];
        itemTags[0] = new Object(); // a dummy object for the show only menu.
        for (int i = 0; i < channels.length; ++i) {
            itemTags[i + 1] = channels[i];
        }

        initialize(getString(R.string.simple_guide_title), itemTags,
                R.layout.option_fragment, R.layout.simple_guide_item, false,
                R.color.option_item_background, R.color.option_item_focused_background,
                R.dimen.simple_guide_item_height);
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
            mTvActivity.onSideFragmentCanceled(getInitiator());
        }
        mTvActivity.hideOverlays(false, false, true);
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemFocusChanged " + focusGained + ": position=" + position
                + ", label=" + tag);
        super.onItemFocusChanged(v, focusGained, position, tag);
        mCurPosition = position;
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
        final ImageView channelLogoView = (ImageView) v.findViewById(R.id.channel_logo);
        final TextView channelNumberView = (TextView) v.findViewById(R.id.channel_number);
        final TextView channelNumberAloneView =
                (TextView) v.findViewById(R.id.channel_number_alone);
        TextView programTitleView = (TextView) v.findViewById(R.id.program_title);
        TextView channelNameView = (TextView) v.findViewById(R.id.channel_name);
        ProgressBar remainingTimeView = (ProgressBar) v.findViewById(R.id.remaining_time);
        if (tag instanceof Channel) {
            Channel channel = (Channel) tag;
            if (!channel.isLogoLoaded()) {
                channel.loadLogo(getActivity(), new LoadLogoCallback() {
                    @Override
                    public void onLoadLogoFinished(Channel channel, Bitmap logo) {
                        if (logo != null) {
                            channelNumberAloneView.setVisibility(View.GONE);
                            channelLogoView.setVisibility(View.VISIBLE);
                            channelNumberView.setVisibility(View.VISIBLE);
                            channelLogoView.setImageBitmap(logo);
                            channelNumberView.setText(channel.getDisplayNumber());
                        } else {
                            channelLogoView.setVisibility(View.GONE);
                            channelNumberView.setVisibility(View.GONE);
                            channelNumberAloneView.setVisibility(View.VISIBLE);
                            channelNumberAloneView.setText(channel.getDisplayNumber());
                        }
                    }
                });
            } else {
                Bitmap logo = channel.getLogo();
                if (logo != null) {
                    channelNumberAloneView.setVisibility(View.GONE);
                    channelLogoView.setVisibility(View.VISIBLE);
                    channelNumberView.setVisibility(View.VISIBLE);
                    channelLogoView.setImageBitmap(logo);
                    channelNumberView.setText(channel.getDisplayNumber());
                } else {
                    channelLogoView.setVisibility(View.GONE);
                    channelNumberView.setVisibility(View.GONE);
                    channelNumberAloneView.setVisibility(View.VISIBLE);
                    channelNumberAloneView.setText(channel.getDisplayNumber());
                }
            }
            Program program = Utils.getCurrentProgram(mTvActivity,
                    ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channel.getId()));
            String text = program.getTitle();
            if (TextUtils.isEmpty(text)) {
                text = getResources().getString(R.string.no_program_information);
            }
            programTitleView.setText(text);
            channelNameView.setText(channel.getDisplayName());

            long startTime = program.getStartTimeUtcMillis();
            long endTime = program.getEndTimeUtcMillis();
            if (startTime > 0 && endTime > 0) {
                long currTime = System.currentTimeMillis();
                if (currTime <= startTime) {
                    remainingTimeView.setProgress(0);
                } else if (currTime >= endTime) {
                    remainingTimeView.setProgress(100);
                } else {
                    remainingTimeView.setProgress(
                            (int) (100 *(currTime - startTime) / (endTime - startTime)));
                }
                remainingTimeView.setVisibility(View.VISIBLE);
            } else {
                remainingTimeView.setVisibility(View.GONE);
            }
        } else {
            channelLogoView.setVisibility(View.GONE);
            channelNumberView.setVisibility(View.GONE);
            channelNumberAloneView.setVisibility(View.INVISIBLE);
            remainingTimeView.setVisibility(View.GONE);
            programTitleView.setText(getString(R.string.show_only_title));
            channelNameView.setText(ShowOnlyItems.getLabel(
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

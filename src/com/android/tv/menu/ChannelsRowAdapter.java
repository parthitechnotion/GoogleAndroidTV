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
import android.content.Intent;
import android.view.View;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.TvCommonConstants;
import com.android.tv.data.Channel;
import com.android.tv.recommendation.Recommender;
import com.android.tv.util.SetupUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An adapter of the Channels row.
 */
public class ChannelsRowAdapter extends ItemListRowView.ItemListAdapter<Channel> {
    private static final int POSITION_FIRST_CARD = 0;
    private static final int POSITION_SECOND_CARD = 1;
    private static final int POSITION_THIRD_CARD = 2;
    private final Context mContext;
    private final Tracker mTracker;
    private final Recommender mRecommender;
    private final int mMaxCount;
    private final int mMinCount;
    private boolean mShowSetupCard;
    private boolean mShowAppLinkCard;

    private final View.OnClickListener mGuideOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mTracker.sendMenuClicked(R.string.channels_item_program_guide);
            getMainActivity().getOverlayManager().showProgramGuide();
        }
    };

    private final View.OnClickListener mSetupOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mTracker.sendMenuClicked(R.string.channels_item_setup);
            getMainActivity().getOverlayManager().showSetupDialog();
        }
    };

    private final View.OnClickListener mAppLinkOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mTracker.sendMenuClicked(R.string.channels_item_app_link);
            Intent intent = ((AppLinkCardView) view).getIntent();
            if (intent != null) {
                getMainActivity().startActivitySafe(intent);
            }
        }
    };

    private final View.OnClickListener mChannelOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // Always send the label "Channels" because the channel ID or name or number might be
            // sensitive.
            mTracker.sendMenuClicked(R.string.menu_title_channels);
            getMainActivity().tuneToChannel((Channel) view.getTag());
            getMainActivity().hideOverlaysForTune();
        }
    };

    public ChannelsRowAdapter(Context context, Recommender recommender,
            int minCount, int maxCount) {
        super(context);
        mTracker = ((TvApplication) context.getApplicationContext()).getTracker();
        mContext = context;
        mRecommender = recommender;
        mMinCount = minCount;
        mMaxCount = maxCount;
    }

    @Override
    public int getItemViewType(int position) {
        switch (position) {
            case POSITION_FIRST_CARD:
                return R.layout.menu_card_guide;
            case POSITION_SECOND_CARD:
                return mShowSetupCard
                        ? R.layout.menu_card_setup
                        : mShowAppLinkCard
                                ? R.layout.menu_card_app_link
                                : R.layout.menu_card_channel;
            case POSITION_THIRD_CARD:
                return (mShowSetupCard && mShowAppLinkCard)
                        ? R.layout.menu_card_app_link
                        : R.layout.menu_card_channel;
            default:
                return R.layout.menu_card_channel;
        }
    }

    @Override
    protected int getLayoutResId(int viewType) {
        return viewType;
    }

    @Override
    public void onBindViewHolder(MyViewHolder viewHolder, int position) {
        super.onBindViewHolder(viewHolder, position);

        int viewType = getItemViewType(position);
        if (viewType == R.layout.menu_card_guide) {
            viewHolder.itemView.setOnClickListener(mGuideOnClickListener);
        } else if (viewType == R.layout.menu_card_setup) {
            viewHolder.itemView.setOnClickListener(mSetupOnClickListener);
        } else if (viewType == R.layout.menu_card_app_link) {
            viewHolder.itemView.setOnClickListener(mAppLinkOnClickListener);
        } else {
            viewHolder.itemView.setTag(getItemList().get(position));
            viewHolder.itemView.setOnClickListener(mChannelOnClickListener);
        }
    }

    @Override
    public void update() {
        List<Channel> channelList = new ArrayList<>();
        Channel dummyChannel = new Channel.Builder()
                .build();
        // For guide item
        channelList.add(dummyChannel);
        // For setup item
        mShowSetupCard = SetupUtils.getInstance(mContext).hasNewInput(
                ((MainActivity) mContext).getTvInputManagerHelper());
        if (mShowSetupCard) {
            channelList.add(dummyChannel);
        }
        if (TvCommonConstants.IS_MNC_OR_HIGHER) {
            Channel currentChannel = ((MainActivity) mContext).getCurrentChannel();
            mShowAppLinkCard = currentChannel != null
                    && currentChannel.getAppLinkType(mContext) != Channel.APP_LINK_TYPE_NONE;
            if (mShowAppLinkCard) {
                channelList.add(currentChannel);
            }
        }

        channelList.addAll(getRecentChannels());
        setItemList(channelList);
    }

    private List<Channel> getRecentChannels() {
        List<Channel> channelList = new ArrayList<>();
        for (Channel channel : mRecommender.recommendChannels(mMaxCount)) {
            if (channel.isBrowsable()) {
                channelList.add(channel);
            }
        }
        int count = channelList.size();
        // If the number of recommended channels is not enough, add more from the recent channel
        // list.
        if (count < mMinCount && mContext instanceof MainActivity) {
            for (long channelId : ((MainActivity) mContext).getRecentChannels()) {
                Channel channel = mRecommender.getChannel(channelId);
                if (channel == null || channelList.contains(channel)
                        || !channel.isBrowsable()) {
                   continue;
                }
                channelList.add(channel);
                if (++count >= mMinCount) {
                    break;
                }
            }
        }

        return Collections.unmodifiableList(channelList);
    }
}

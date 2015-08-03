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

import com.android.tv.R;
import com.android.tv.common.TvCommonConstants;
import com.android.tv.recommendation.RecentChannelEvaluator;
import com.android.tv.recommendation.Recommender;

public class ChannelsRow extends ItemListRow {
    public static final String ID = ChannelsRow.class.getName();

    private static final int MIN_COUNT_FOR_RECENT_CHANNELS = 5;
    private static final int MAX_COUNT_FOR_RECENT_CHANNELS = 10;

    private Recommender mTvRecommendation;
    private ChannelsRowAdapter mChannelsAdapter;
    private ChannelsPosterPrefetcher mChannelsPosterPrefetcher;

    public ChannelsRow(Context context) {
        super(context,
                TvCommonConstants.IS_MNC_OR_HIGHER
                        ? R.string.menu_title_channels : R.string.menu_title_channels_legacy,
                R.dimen.card_layout_height,
                null);
        mTvRecommendation = new Recommender(getContext(), new Recommender.Listener() {
            @Override
            public void onRecommenderReady() {
                mChannelsAdapter.update();
                mChannelsPosterPrefetcher.prefetch();
            }

            @Override
            public void onRecommendationChanged() {
                mChannelsAdapter.update();
                mChannelsPosterPrefetcher.prefetch();
            }
        }, true);
        mTvRecommendation.registerEvaluator(new RecentChannelEvaluator());
        mChannelsAdapter = new ChannelsRowAdapter(context, mTvRecommendation,
                MIN_COUNT_FOR_RECENT_CHANNELS, MAX_COUNT_FOR_RECENT_CHANNELS);
        setAdapter(mChannelsAdapter);
        mChannelsPosterPrefetcher = new ChannelsPosterPrefetcher(context,
                getMainActivity().getProgramDataManager(), mChannelsAdapter);
    }

    @Override
    public void release() {
        super.release();
        mTvRecommendation.release();
        mTvRecommendation = null;
    }

    /**
     * Handle the update event of the recent channel.
     */
    public void onRecentChannelUpdated() {
        mChannelsPosterPrefetcher.prefetch();
    }

    @Override
    public String getId() {
        return ID;
    }
}

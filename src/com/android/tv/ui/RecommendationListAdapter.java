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
import android.os.Handler;
import android.view.View;

import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.recommendation.TvRecommendation;

/*
 * An adapter of recommended channel list.
 */
public class RecommendationListAdapter extends ItemListView.ItemListAdapter {
    private ChannelMap mChannelMap;
    private Channel[] mChannelList;
    private ItemListView mListView;
    private final String mTitle;
    private final int mTileHeight;
    private final int mMaxCount;
    private final TvRecommendation mRecommendationEngine;

    public RecommendationListAdapter(Context context, Handler handler,
            View.OnClickListener onClickListener, TvRecommendation recommendationEngine,
            int maxCount, int tileResId, String title, int tileHeight) {
        super(context, handler, tileResId, onClickListener);
        mRecommendationEngine = recommendationEngine;
        mMaxCount = maxCount;
        mTitle = title;
        mTileHeight = tileHeight;
    }

    @Override
    public int getTileHeight() {
        return mTileHeight;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public void update(ChannelMap channelMap) {
        update(channelMap, mListView);
    }

    @Override
    public void update(ChannelMap channelMap, ItemListView listView) {
        mChannelMap = channelMap;
        mListView = listView;

        updateChannelList();
        selectCurrentChannel();
    }

    @Override
    public void onBeforeShowing() {
        updateChannelList();
        selectCurrentChannel();
    }

    private void updateChannelList() {
        TvRecommendation.ChannelRecord[] records =
                mRecommendationEngine.getRecommendedChannelList(mMaxCount);
        mChannelList = null;
        if (records != null && records.length > 0) {
            mChannelList = new Channel[records.length];
            for (int i = 0; i < records.length; i++) {
                mChannelList[i] = records[i].getChannel();
            }
        }
        setItemList(mChannelList);
    }

    private void selectCurrentChannel() {
        long id = mChannelMap == null ? Channel.INVALID_ID : mChannelMap.getCurrentChannelId();
        if (mListView == null || mChannelList == null || id == Channel.INVALID_ID) {
            return;
        }
        for (int i = 0; i < mChannelList.length; i++) {
            if (id == mChannelList[i].getId()) {
                mListView.setSelectedPosition(i);
                break;
            }
        }
    }
}
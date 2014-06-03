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

/*
 * An adapter of channel list.
 */
public class ChannelListAdapter extends ItemListView.ItemListAdapter {
    private ChannelMap mChannelMap;
    private Channel[] mChannelList;
    private ItemListView mListView;
    private final boolean mGuideIncluded;
    private final boolean mBrowsableOnly;
    private final String mFixedTitle;
    private String mTitle;
    private final int mTileHeight;

    public ChannelListAdapter(Context context, Handler handler,
            View.OnClickListener onClickListener, boolean guideIncluded, boolean browsableOnly,
            String title,
            int tileHeight) {
        super(context, handler, R.layout.channel_tile, onClickListener);
        mGuideIncluded = guideIncluded;
        mBrowsableOnly = browsableOnly;
        mFixedTitle = title;
        mTileHeight = tileHeight;
    }

    @Override
    public int getTileHeight() {
        return mTileHeight;
    }

    @Override
    public String getTitle() {
        return mFixedTitle != null ? mFixedTitle : mTitle;
    }

    @Override
    public void update(ChannelMap channelMap) {
        update(channelMap, mListView);
    }

    @Override
    public void update(ChannelMap channelMap, ItemListView listView) {
        mChannelMap = channelMap;
        mListView = listView;

        mChannelList = mChannelMap == null ? null : mChannelMap.getChannelList(mBrowsableOnly);
        if (mGuideIncluded) {
            Channel[] channels = mChannelList == null ? new Channel[0] : mChannelList;
            Channel guideChannel = new Channel.Builder()
                    .setType(R.integer.channel_type_guide)
                    .build();
            mChannelList = new Channel[channels.length + 1];
            mChannelList[0] = guideChannel;
            for (int i = 0; i < channels.length; ++i) {
                mChannelList[i + 1] = channels[i];
            }
        }
        setItemList(mChannelList);

        updateTitle();
        selectCurrentChannel();
    }

    @Override
    public void onBeforeShowing() {
        updateTitle();
        selectCurrentChannel();
    }

    private void updateTitle() {
        if (mFixedTitle == null) {
            mTitle = null;
            if (mChannelMap != null) {
                mTitle = mChannelMap.getTvInput().getDisplayName();
            }

            if (mListView != null) {
                mListView.setTitle(mTitle);
            }
        }
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

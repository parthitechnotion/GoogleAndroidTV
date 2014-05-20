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

import com.android.tv.Channel;
import com.android.tv.ChannelMap;
import com.android.tv.R;
import com.android.tv.Utils;

/*
 * An adapter of channel list.
 */
public class ChannelListAdapter extends ItemListView.ItemListAdapter {
    private static final String TAG = "ChannelListAdapter";

    private Channel[] mChannelList;
    private ItemListView mListView;
    private Context mContext;

    public ChannelListAdapter(Context context, Handler handler,
            View.OnClickListener onClickListener) {
        super(context, handler, R.layout.channel_tile, onClickListener);
        mContext = context;
    }

    public void update(ChannelMap channelMap) {
        update(channelMap, mListView);
    }

    public void update(ChannelMap channelMap, ItemListView listView) {
        mChannelList = channelMap == null ? null : channelMap.getAllChannelList();
        setItemList(mChannelList);

        String title = null;
        mListView = listView;
        if (channelMap != null) {
            setCurrentChannelId(channelMap.getCurrentChannelId());
            title = Utils.getDisplayNameForInput(mContext, channelMap.getTvInputInfo(),
                    channelMap.isUnifiedTvInput());
        }

        if (mListView != null) {
            mListView.setTitle(title);
        }
    }

    public void setCurrentChannelId(long id) {
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
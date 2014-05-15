// Copyright 2014 Google Inc. All Rights Reserved.

package com.android.tv.ui;

import com.android.tv.Channel;
import com.android.tv.ChannelMap;
import com.android.tv.R;
import com.android.tv.Utils;

import android.content.Context;
import android.tv.TvInputInfo;
import android.view.View;

/*
 * An adapter of channel list.
 */
public class ChannelListAdapter extends ItemListView.ItemListAdapter {
    private static final String TAG = "ChannelListAdapter";

    private Channel[] mChannelList;
    private ItemListView mListView;
    private Context mContext;

    public ChannelListAdapter(Context context, View.OnClickListener onClickListener) {
        super(context, R.layout.channel_tile, onClickListener);
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
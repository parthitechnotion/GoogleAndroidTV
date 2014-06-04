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
import com.android.tv.data.ChannelMap;
import com.android.tv.input.TvInput;

import java.util.ArrayList;

/*
 * An adapter of options.
 */
public class OptionsAdapter extends ItemListView.ItemListAdapter {
    private final String mTitle;
    private final int mTileHeight;
    private ChannelMap mChannelMap;

    public OptionsAdapter(Context context, Handler handler, View.OnClickListener onClickListener) {
        super(context, handler, R.layout.action_tile, onClickListener);

        mTitle = context.getString(R.string.menu_title);
        mTileHeight = context.getResources().getDimensionPixelOffset(
                R.dimen.action_list_view_height);
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
        mChannelMap = channelMap;
        TvInput tvInput = channelMap == null ? null : channelMap.getTvInput();

        ArrayList<MenuAction> actionList = new ArrayList<MenuAction>();
        actionList.add(MenuAction.SELECT_CLOSED_CAPTION_ACTION);
        actionList.add(MenuAction.SELECT_ASPECT_RATIO_ACTION);
        actionList.add(MenuAction.SELECT_TV_INPUT_ACTION);
        actionList.add(MenuAction.TOGGLE_PIP_ACTION);
        if (channelMap != null) {
            actionList.add(MenuAction.EDIT_CHANNEL_LIST_ACTION);
        }
        if (channelMap != null && tvInput.getIntentForSetupActivity() != null) {
            actionList.add(MenuAction.AUTO_SCAN_CHANNELS_ACTION);
        }
        if (channelMap != null && tvInput.getIntentForSettingsActivity() != null) {
            actionList.add(MenuAction.INPUT_SETTING_ACTION);
        }

        setItemList(actionList.toArray(new MenuAction[0]));
    }

    @Override
    public void update(ChannelMap channelMap, ItemListView list) {
        update(channelMap);
    }
}

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
import android.tv.TvInputInfo;
import android.view.View;

import com.android.tv.ChannelMap;
import com.android.tv.R;
import com.android.tv.TvInput;
import com.android.tv.Utils;

import java.util.ArrayList;

/*
 * An adapter of options.
 */
public class OptionsAdapter extends ItemListView.ItemListAdapter {
    private static final String TAG = "OptionsAdapter";

    public OptionsAdapter(Context context, Handler handler, View.OnClickListener onClickListener) {
        super(context, handler, R.layout.action_tile, onClickListener);
    }

    public void update(ChannelMap channelMap) {
        TvInput tvInput = channelMap == null ? null : channelMap.getTvInput();

        ArrayList<MenuAction> actionList = new ArrayList<MenuAction>();
        actionList.add(MenuAction.SELECT_TV_INPUT_ACTION);
        if (channelMap != null) {
            actionList.add(MenuAction.EDIT_CHANNEL_LIST_ACTION);
        }
        if (channelMap != null && tvInput.hasActivity(Utils.ACTION_SETUP)) {
            actionList.add(MenuAction.AUTO_SCAN_CHANNELS_ACTION);
        }
        actionList.add(MenuAction.PRIVACY_SETTING_ACTION);
        actionList.add(MenuAction.TOGGLE_PIP_ACTION);
        if (channelMap != null && tvInput.hasActivity(Utils.ACTION_SETTINGS)) {
            actionList.add(MenuAction.MORE_ACTION);
        }

        setItemList(actionList.toArray(new MenuAction[0]));
    }
}

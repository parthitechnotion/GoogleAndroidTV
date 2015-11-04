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

package com.android.tv.ui.sidepanel;

import android.view.View;
import android.widget.Toast;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.util.SetupUtils;

import java.util.ArrayList;
import java.util.List;

public class ChannelSourcesFragment extends SideFragment {
    private static final String TRACKER_LABEL =  "channel sources";

    private static int ADDITIONAL_DELAY_TO_SHOW_SETUP_DIALOG_MILLIS = 50;

    private final long mCurrentChannelId;

    public ChannelSourcesFragment(long currentChannelId) {
        mCurrentChannelId = currentChannelId;
    }

    @Override
    protected String getTitle() {
        return getString(R.string.side_panel_title_channel_sources);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        final Item customizeChannelListItem = new SubMenuItem(
                getString(R.string.channel_source_item_customize_channels),
                getString(R.string.channel_source_item_customize_channels_description),
                0, getMainActivity().getOverlayManager().getSideFragmentManager()) {
            @Override
            protected SideFragment getFragment() {
                return new CustomizeChannelListFragment(mCurrentChannelId);
            }

            @Override
            protected void onBind(View view) {
                super.onBind(view);
                setEnabled(false);
            }

            @Override
            protected void onUpdate() {
                super.onUpdate();
                setEnabled(getChannelDataManager().getChannelCount() != 0);
            }
        };
        customizeChannelListItem.setEnabled(false);
        items.add(customizeChannelListItem);
        final MainActivity activity = getMainActivity();
        boolean hasNewInput = SetupUtils.getInstance(activity).hasNewInput(
                activity.getTvInputManagerHelper());
        items.add(new ActionItem(
                getString(R.string.channel_source_item_setup),
                hasNewInput ? getString(R.string.channel_source_item_setup_new_inputs)
                        : null) {
            @Override
            protected void onSelected() {
                closeFragment();
                // Running two animations at the same time causes performance drop.
                // Show the setup dialog with delayed animation.
                activity.getOverlayManager().showSetupDialog(
                        activity.getResources().getInteger(R.integer.side_panel_anim_short_duration)
                        + ADDITIONAL_DELAY_TO_SHOW_SETUP_DIALOG_MILLIS);
            }
        });
        return items;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getChannelDataManager().areAllChannelsHidden()) {
            Toast.makeText(getActivity(), R.string.msg_all_channels_hidden, Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
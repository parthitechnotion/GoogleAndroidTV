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

package com.android.tv.ui.sidepanel;

import android.content.ContentValues;
import android.media.tv.TvContract;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.data.Channel;

import java.util.ArrayList;
import java.util.List;

public class EditChannelsFragment extends SideFragment {
    Channel[] mChannels;

    public EditChannelsFragment(Channel[] channels) {
        mChannels = channels;
    }

    @Override
    protected String getTitle() {
        String displayName = getTvActivity().getSelectedTvInput().getDisplayName();
        return String.format(getString(R.string.edit_channels_title), displayName);
    }

    @Override
    protected List<Item> getItemList() {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new ActionItem(getString(R.string.edit_channels_show_all)) {
            @Override
            protected void onSelected() {
                super.onSelected();
                updateAllChannels(true);
            }
        });
        items.add(new ActionItem(getString(R.string.edit_channels_hide_all)) {
            @Override
            protected void onSelected() {
                super.onSelected();
                updateAllChannels(false);
            }
        });
        items.add(new DividerItem());
        for (Channel channel : mChannels) {
            final Channel currentChannel = channel;
            items.add(new CheckBoxItem(getChannelName(channel)) {
                @Override
                protected void bind(View view) {
                    super.bind(view);
                    setChecked(currentChannel.isBrowsable());
                }

                @Override
                protected void onSelected() {
                    super.onSelected();

                    Uri uri = TvContract.buildChannelUri(currentChannel.getId());
                    ContentValues values = new ContentValues();
                    values.put(TvContract.Channels.COLUMN_BROWSABLE, getChecked() ? 1 : 0);
                    getActivity().getContentResolver().update(uri, values, null, null);

                    currentChannel.setBrowsable(getChecked());
                    if (!getChecked()) {
                        maybeDisplayAllUnchecked();
                    }
                }
            });
        }
        return items;
    }

    private void maybeDisplayAllUnchecked() {
        if (!hasBrowsableChannel()) {
            Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAllChannels(boolean browsable) {
        Uri uri = getTvActivity().getSelectedTvInput().buildChannelsUri(null);
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_BROWSABLE, browsable ? 1 : 0);
        getActivity().getContentResolver().update(uri, values, null, null);

        for (Channel channel : mChannels) {
            channel.setBrowsable(browsable);
        }
        notifyDataSetChanged();
        maybeDisplayAllUnchecked();
    }

    private boolean hasBrowsableChannel() {
        for (Channel channel : mChannels) {
            if (channel.isBrowsable()) {
                return true;
            }
        }
        return false;
    }

    private String getChannelName(Channel channel) {
        String channelName = channel.getDisplayName();
        String channelNumber = channel.getDisplayNumber();
        if (TextUtils.isEmpty(channelName)) {
            return channelNumber;
        }
        return String.format(getString(R.string.channel_item), channelNumber, channelName);
    }
}
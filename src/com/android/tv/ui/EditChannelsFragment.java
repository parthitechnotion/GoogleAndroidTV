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

import android.content.ContentValues;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;
import com.android.tv.input.TvInput;

public class EditChannelsFragment extends BaseSideFragment {
    private static final int ACTION_SHOW_ALL = 0;
    private static final int ACTION_HIDE_ALL = 1;

    private TvInput mSelectedInput;

    private TvActivity mTvActivity;
    private Item[] mItems;
    private String[] mActions;
    private Channel[] mChannels;
    private int mBrowsableChannelCount;

    private int mBgColor;
    private int mFocusedBgColor;

    private static final class Item {
        private static final int TYPE_ACTION = 0;
        private static final int TYPE_CHANNEL = 1;

        private Item(int action) {
            mType = TYPE_ACTION;
            mAction = action;
        }

        private Item(Channel channel) {
            mType = TYPE_CHANNEL;
            mChannel = channel;
        }

        private int mType;
        private int mAction;
        private Channel mChannel;
    }

    public EditChannelsFragment(Channel[] channels) {
        mChannels = channels;
        mBrowsableChannelCount = 0;
        for (Channel channel : channels) {
            if (channel.isBrowsable()) {
                ++mBrowsableChannelCount;
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mTvActivity = (TvActivity) getActivity();
        mSelectedInput = mTvActivity.getSelectedTvInput();

        mBgColor = getActivity().getResources().getColor(R.color.option_item_background);
        mFocusedBgColor = getActivity().getResources().getColor(
                R.color.option_item_focused_background);

        mActions = getActivity().getResources().getStringArray(R.array.edit_channels_actions);
        mItems = new Item[mActions.length + mChannels.length];
        int index = 0;
        for (; index<mActions.length; ++index) {
            mItems[index] = new Item(index);
        }
        for (Channel channel : mChannels) {
            mItems[index++] = new Item(channel);
        }
        String displayName = mSelectedInput.getDisplayName();
        String title = String.format(getString(R.string.edit_channels_title), displayName);
        initialize(title, mItems, R.layout.option_fragment, R.layout.edit_channels_item,
                false);

        if (mBrowsableChannelCount <= 0) {
            Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                    Toast.LENGTH_SHORT).show();
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        super.onBindView(v, position, tag, prevSelected);

        RadioButton button = (RadioButton) v.findViewById(R.id.radio_button);
        TextView textView = (TextView) v.findViewById(R.id.channel_text_view);

        Item item = (Item) tag;
        if (item.mType == Item.TYPE_ACTION) {
            button.setVisibility(View.INVISIBLE);
            textView.setText(mActions[item.mAction]);
        } else if (item.mType == Item.TYPE_CHANNEL) {
            button.setVisibility(View.VISIBLE);
            button.setChecked(item.mChannel.isBrowsable());

            String channelNumber = item.mChannel.getDisplayNumber();
            String channelName = item.mChannel.getDisplayName();
            String channelString;
            if (TextUtils.isEmpty(channelName)) {
                channelString = channelNumber;
            } else {
                channelString = String.format(getString(R.string.channel_item),
                        channelNumber, channelName);
            }
            textView.setText(channelString);
        }
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        Item item = (Item) tag;
        if (item.mType == Item.TYPE_ACTION) {
            if (item.mAction == ACTION_SHOW_ALL) {
                updateAllChannels(true);
            } else if (item.mAction == ACTION_HIDE_ALL) {
                updateAllChannels(false);
            }
        } else if (item.mType == Item.TYPE_CHANNEL) {
            RadioButton button = (RadioButton) v.findViewById(R.id.radio_button);
            boolean checked = button.isChecked();

            Channel channel = item.mChannel;
            Uri uri = TvContract.buildChannelUri(channel.getId());
            ContentValues values = new ContentValues();
            values.put(TvContract.Channels.COLUMN_BROWSABLE, checked ? 0 : 1);
            getActivity().getContentResolver().update(uri, values, null, null);
            channel.setBrowsable(!checked);

            button.setChecked(!checked);
            mBrowsableChannelCount += checked ? -1 : 1;
            if (mBrowsableChannelCount <= 0) {
                Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                        Toast.LENGTH_SHORT).show();
            }
        }

        super.onItemSelected(v, position, tag);
    }

    private void updateAllChannels(boolean browsable) {
        Uri uri = mSelectedInput.buildChannelsUri();
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_BROWSABLE, browsable ? 1 : 0);

        getActivity().getContentResolver().update(uri, values, null, null);

        for (Channel channel : mChannels) {
            channel.setBrowsable(browsable);
        }
        mAdapter.notifyDataSetChanged();

        if (browsable) {
            mBrowsableChannelCount = mChannels.length;
        } else  {
            mBrowsableChannelCount = 0;
            Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        v.setBackgroundColor(focusGained ? mFocusedBgColor : mBgColor);
    }
}

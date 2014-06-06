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
import android.widget.CheckedTextView;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;
import com.android.tv.input.TvInput;

public class EditChannelsFragment extends BaseSideFragment {
    private static final String TAG = "EditChannelsFragment";
    private static final boolean DEBUG = true;

    private TvInput mSelectedInput;

    private TvActivity mTvActivity;
    private Channel[] mChannels;

    private int mBgColor;
    private int mFocusedBgColor;

    private int mBrowsableChannelCount;

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

        String displayName = mSelectedInput.getDisplayName();
        String title = String.format(getString(R.string.edit_channels_title), displayName);
        initialize(title, mChannels, R.layout.edit_channels_fragment, R.layout.edit_channels_item,
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

        Channel channel = (Channel) tag;
        String channelNumber = channel.getDisplayNumber();
        String channelName = channel.getDisplayName();
        String channelString;
        if (TextUtils.isEmpty(channelName)) {
            channelString = channelNumber;
        } else {
            channelString = String.format(getString(R.string.channel_item),
                    channelNumber, channelName);
        }
        CheckedTextView checkedTextView =
                (CheckedTextView) v.findViewById(R.id.channel_text_view);
        checkedTextView.setText(channelString);
        checkedTextView.setChecked(channel.isBrowsable());
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        CheckedTextView checkedTextView =
                (CheckedTextView) v.findViewById(R.id.channel_text_view);
        boolean checked = checkedTextView.isChecked();

        Channel channel = (Channel) tag;
        Uri uri = TvContract.buildChannelUri(channel.getId());
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_BROWSABLE, checked ? 0 : 1);
        getActivity().getContentResolver().update(uri, values, null, null);

        checkedTextView.setChecked(!checked);
        mBrowsableChannelCount += checked ? -1 : 1;
        if (mBrowsableChannelCount <= 0) {
            Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                    Toast.LENGTH_SHORT).show();
        }

        super.onItemSelected(v, position, tag);
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        v.setBackgroundColor(focusGained ? mFocusedBgColor : mBgColor);
    }
}

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
import android.os.Message;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;

import java.util.ArrayList;

public class ChannelNumberView extends LinearLayout {
    private static final String TAG = "ChannelNumberView";
    private static final int MSG_HIDE_VIEW = 0;

    private static final int HIDE_VIEW_DELAY_MS = 2000;
    private static final int MAX_CHANNEL_NUMBER_DIGIT = 4;
    private static final int MAX_MINOR_CHANNEL_NUMBER_DIGIT = 3;
    private static final String PRIMARY_CHANNEL_DELIMETER = "-";
    private static final String[] CHANNEL_DELIMETERS = {"-", ".", " "};
    private static final int[] CHANNEL_DELIMETER_KEYCODES = {
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_PERIOD,
        KeyEvent.KEYCODE_NUMPAD_DOT, KeyEvent.KEYCODE_SPACE
    };

    private TvActivity mTvActivity;
    private Channel[] mChannels;
    private TextView mTypedChannelNumberView;
    private VerticalGridView mChannelItemListView;
    private final ChannelNumber mTypedChannelNumber = new ChannelNumber();
    private final ArrayList<Channel> mChannelCandidates = new ArrayList<Channel>();
    protected final ChannelItemAdapter mAdapter = new ChannelItemAdapter();
    private final LayoutInflater mLayoutInflater;
    private int mItemBgColor;
    private int mItemFocusedBgColor;
    private long mSelectedChannelId = Channel.INVALID_ID;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_HIDE_VIEW) {
                if (mSelectedChannelId != Channel.INVALID_ID) {
                    mTvActivity.moveToChannel(mSelectedChannelId);
                }
                hide();
            }
        }
    };

    public ChannelNumberView(Context context) {
        this(context, null, 0);
    }

    public ChannelNumberView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChannelNumberView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate(){
        mTypedChannelNumberView = (TextView) findViewById(R.id.typed_channel_number);
        mChannelItemListView = (VerticalGridView) findViewById(R.id.channel_list);
        mChannelItemListView.setAdapter(mAdapter);
        mItemBgColor = getContext().getResources().getColor(R.color.option_item_background);
        mItemFocusedBgColor = getContext().getResources().getColor(
                R.color.option_item_focused_background);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Preconditions.checkState(mChannels != null);
        mHandler.removeMessages(MSG_HIDE_VIEW);
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_VIEW, HIDE_VIEW_DELAY_MS);

        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            onNumberKeyUp(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }
        if (isChannelNumberDelimiterKey(keyCode)) {
            onDelimeterKeyUp();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void show() {
        setVisibility(View.VISIBLE);
        mHandler.removeMessages(MSG_HIDE_VIEW);
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_VIEW, HIDE_VIEW_DELAY_MS);
    }

    public void hide() {
        setVisibility(View.INVISIBLE);
        mTypedChannelNumber.reset();
        mSelectedChannelId = Channel.INVALID_ID;
        mChannelCandidates.clear();
        mAdapter.notifyDataSetChanged();
    }

    public void setChannels(Channel[] channels) {
        mChannels = channels;
    }

    public void setTvActivity(TvActivity activity) {
        mTvActivity = activity;
    }

    public static boolean isChannelNumberKey(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return true;
        }
        if (isChannelNumberDelimiterKey(keyCode)) {
            return true;
        }
        return false;
    }

    public static boolean isChannelNumberDelimiterKey(int keyCode) {
        for (int i = 0; i < CHANNEL_DELIMETER_KEYCODES.length; ++i) {
            if (CHANNEL_DELIMETER_KEYCODES[i] == keyCode) {
                return true;
            }
        }
        return false;
    }

    private void onNumberKeyUp(int num) {
        // Reset typed channel number in some cases.
        if (mTypedChannelNumber.mMajor == null) {
            mTypedChannelNumber.reset();
        } else if (!mTypedChannelNumber.mHasDelimeter
                && mTypedChannelNumber.mMajor.length() >= MAX_CHANNEL_NUMBER_DIGIT) {
            mTypedChannelNumber.reset();
        } else if (mTypedChannelNumber.mHasDelimeter
                && mTypedChannelNumber.mMinor != null
                && mTypedChannelNumber.mMinor.length() >= MAX_MINOR_CHANNEL_NUMBER_DIGIT) {
            mTypedChannelNumber.reset();
        }

        if (!mTypedChannelNumber.mHasDelimeter) {
            mTypedChannelNumber.mMajor += String.valueOf(num);
        } else {
            mTypedChannelNumber.mMinor += String.valueOf(num);
        }
        updateView();
    }

    private void onDelimeterKeyUp() {
        if (mTypedChannelNumber.mHasDelimeter || mTypedChannelNumber.mMajor.length() == 0) {
            return;
        }
        mTypedChannelNumber.mHasDelimeter = true;
        updateView();
    }

    private void updateView() {
        mTypedChannelNumberView.setText(mTypedChannelNumber.toString());

        mChannelCandidates.clear();
        ArrayList<Channel> secondaryChannelCandidates = new ArrayList<Channel>();
        for (Channel channel : mChannels) {
            ChannelNumber chNumber = parseChannelNumber(channel.getDisplayNumber());
            if (chNumber == null) {
                Log.i(TAG, "Malformed channel number (name=" + channel.getDisplayName()
                        + ", number=" + channel.getDisplayNumber() + ")");
                continue;
            }
            if (matchChannelNumber(mTypedChannelNumber, chNumber)) {
                mChannelCandidates.add(channel);
            }
            if (!mTypedChannelNumber.mHasDelimeter) {
                // Even if a user doesn't type '-', we need to match the typed number to not only
                // the major number but also the minor number. For example, when a user types '111'
                // without delimeter, it should be matched to '111', '1-11' and '11-1'.
                String inputMajor = mTypedChannelNumber.mMajor;
                int length = inputMajor.length();
                ChannelNumber expectedInputChNumber = new ChannelNumber();
                for (int i = 0; i < length - 1 && i < MAX_MINOR_CHANNEL_NUMBER_DIGIT; ++i) {
                    expectedInputChNumber.setChannelNumber(inputMajor.substring(0, length - i - 1),
                            true, inputMajor.substring(length - i - 1));
                    if (matchChannelNumber(expectedInputChNumber, chNumber)) {
                        secondaryChannelCandidates.add(channel);
                    }
                }
            }
        }
        mChannelCandidates.addAll(secondaryChannelCandidates);
        mAdapter.notifyDataSetChanged();
        if (mAdapter.getItemCount() > 0) {
            mChannelItemListView.setSelectedPosition(0);
        }
    }

    private static boolean matchChannelNumber(ChannelNumber typedChNumber, ChannelNumber chNumber) {
        if (!chNumber.mMajor.equals(typedChNumber.mMajor)) {
            return false;
        }
        if (typedChNumber.mHasDelimeter) {
            if (!chNumber.mHasDelimeter) {
                return false;
            }
            if (!chNumber.mMinor.startsWith(typedChNumber.mMinor)) {
                return false;
            }
        }
        return true;
    }

    private static ChannelNumber parseChannelNumber(String number) {
        ChannelNumber ret = new ChannelNumber();
        int indexOfDelimeter = -1;
        for (int i = 0; i < CHANNEL_DELIMETERS.length; ++i) {
            indexOfDelimeter = number.indexOf(CHANNEL_DELIMETERS[i]);
            if (indexOfDelimeter >= 0) {
                break;
            }
        }
        if (indexOfDelimeter == 0 || indexOfDelimeter == number.length() - 1) {
            return null;
        }
        if (indexOfDelimeter < 0) {
            ret.mMajor = number;
            return ret;
        }
        ret.mHasDelimeter = true;
        ret.mMajor = number.substring(0, indexOfDelimeter);
        if (number.length() > indexOfDelimeter + 1) {
            ret.mMinor = number.substring(indexOfDelimeter + 1);
        }
        return ret;
    }

    private static class ChannelNumber {
        ChannelNumber() {
            reset();
        }

        void reset() {
            setChannelNumber("", false, "");
        }

        void setChannelNumber(String major, boolean hasDelimeter, String minor) {
            mMajor = major;
            mHasDelimeter = hasDelimeter;
            mMinor = minor;
        }

        String mMajor;
        boolean mHasDelimeter;
        String mMinor;

        @Override
        public String toString() {
            return "" + mMajor + (mHasDelimeter ? PRIMARY_CHANNEL_DELIMETER + mMinor : "");
        }
    }

    class ChannelItemAdapter extends RecyclerView.Adapter<ChannelItemAdapter.MyViewHolder> {
        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = mLayoutInflater.inflate(R.layout.channel_item, parent, false);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Channel channel = (Channel) v.getTag();
                    mTvActivity.moveToChannel(channel.getId());
                    hide();
                }
            });
            v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean focusGained) {
                    if (focusGained) {
                        mSelectedChannelId = ((Channel) v.getTag()).getId();
                    } else {
                        mSelectedChannelId = Channel.INVALID_ID;
                    }
                    v.setBackgroundColor(focusGained ? mItemFocusedBgColor : mItemBgColor);
                }
            });
            return new MyViewHolder(v);
        }

        @Override
        public void onBindViewHolder(MyViewHolder baseHolder, int position) {
            View v = baseHolder.itemView;
            Channel channel = mChannelCandidates.get(position);
            v.setTag(channel);
            TextView channelNumberView = (TextView) v.findViewById(R.id.channel_number);
            channelNumberView.setText(channel.getDisplayName() + " " + channel.getDisplayNumber());
            if (v instanceof ViewGroup) {
                final ViewGroup viewGroup = (ViewGroup) v;
                mHandler.post(new Runnable() {
                    void requestLayout(ViewGroup v) {
                        for (int i = 0; i < v.getChildCount(); i++) {
                            v.getChildAt(i).requestLayout();
                            if (v.getChildAt(i) instanceof ViewGroup) {
                                requestLayout((ViewGroup) v.getChildAt(i));
                            }
                        }
                    }

                    @Override
                    public void run() {
                        requestLayout(viewGroup);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return mChannelCandidates.size();
        }

        private class MyViewHolder extends RecyclerView.ViewHolder {
            MyViewHolder(View view) {
                super(view);
            }
        }
    }
}

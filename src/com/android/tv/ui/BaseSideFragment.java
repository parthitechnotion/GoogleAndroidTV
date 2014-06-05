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

import android.app.Fragment;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;

public class BaseSideFragment extends Fragment {
    private static final String TAG = "BaseSideFragment";

    public static final String KEY_INITIATOR = "last_state";
    public static final int INITIATOR_UNKNOWN = 0;
    public static final int INITIATOR_SHORTCUT_KEY = 1;
    public static final int INITIATOR_MENU = 2;

    private String mTitle;
    private TextView mTitleView;
    private VerticalGridView mOptionItemListView;
    private LayoutInflater mLayoutInflater;
    protected final OptionItemAdapter mAdapter = new OptionItemAdapter();
    private Object[] mItemTags;
    private int mPrevSelectedItemPosition;
    private int mFragmentLayoutId;
    private int mItemLayoutId;
    private int mInitiator;
    private boolean mHasDummyItem;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // initialize should be called before onCreateView.
        Preconditions.checkState(mItemTags != null);
        Bundle arg = getArguments();
        if (arg != null && arg.containsKey(KEY_INITIATOR)) {
            mInitiator = arg.getInt(KEY_INITIATOR);
        } else {
            mInitiator = INITIATOR_UNKNOWN;
        }

        View fragView = inflater.inflate(mFragmentLayoutId, container, false);
        mTitleView = (TextView) fragView.findViewById(R.id.side_panel_title);
        mTitleView.setText(mTitle);
        mOptionItemListView = (VerticalGridView) fragView.findViewById(R.id.side_panel_list);
        mOptionItemListView.setAdapter(mAdapter);
        mLayoutInflater = inflater;
        return fragView;
    }

    public int getInitiator() {
        return mInitiator;
    }

    public void setPrevSelectedItemPosition(int position) {
        mPrevSelectedItemPosition = position;
        mAdapter.notifyDataSetChanged();
    }

    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
    }

    public void onItemSelected(View v, int position, Object tag) {
    }

    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
    }

    public void setSelectedPosition(int position) {
        mOptionItemListView.setSelectedPosition(mHasDummyItem ? position + 1 : position);
        mOptionItemListView.requestFocus();
    }

    protected void initialize(String title, Object[] itemTags, int fragmentLayoutId,
            int itemLayoutId, boolean addDummyItemForMargin) {
        Preconditions.checkState(!TextUtils.isEmpty(title));
        mTitle = title;
        mItemTags = itemTags;
        mFragmentLayoutId = fragmentLayoutId;
        mItemLayoutId = itemLayoutId;
        mHasDummyItem = addDummyItemForMargin;
        mAdapter.notifyDataSetChanged();
    }

    class OptionItemAdapter extends RecyclerView.Adapter<OptionItemAdapter.MyViewHolder> {
        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = mLayoutInflater.inflate(mItemLayoutId, parent, false);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = (Integer) v.getTag(R.id.TAG_OPTION_ITEM_POSITOIN);
                    onItemSelected(v, position, mItemTags[position]);
                }
            });
            v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean focusGained) {
                    int position = (Integer) v.getTag(R.id.TAG_OPTION_ITEM_POSITOIN);
                    onItemFocusChanged(v, focusGained, position, mItemTags[position]);
                    }
            });
            return new MyViewHolder(v);
        }

        @Override
        public void onBindViewHolder(MyViewHolder baseHolder, int position) {
            View v = baseHolder.itemView;
            if (mHasDummyItem && (position <= 0 || position >= getItemCount() - 1)) {
                // The first and the last items are dummy items to give a margin.
                v.setVisibility(View.INVISIBLE);
            } else {
                // Since position 0 is a dummy item, recalculate the position.
                if (mHasDummyItem) {
                    position = position - 1;
                }
                v.setVisibility(View.VISIBLE);
                onBindView(v, position, mItemTags[position], position == mPrevSelectedItemPosition);
                v.setTag(R.id.TAG_OPTION_ITEM_POSITOIN, position);
            }
        }

        @Override
        public int getItemCount() {
            return mItemTags == null ? 0 : mHasDummyItem ? mItemTags.length + 2 : mItemTags.length;
        }

        private class MyViewHolder extends RecyclerView.ViewHolder {
            MyViewHolder(View view) {
                super(view);
            }
        }
    }
}

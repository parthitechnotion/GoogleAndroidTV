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
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.util.Preconditions;
import com.android.tv.R;

public class BaseSideFragment extends Fragment {
    private static final String TAG = "BaseSideFragment";

    private String mTitle;
    private TextView mTitleView;
    private VerticalGridView mOptionItemListView;
    private LayoutInflater mLayoutInflater;
    private final OptionItemAdapter mAdapter = new OptionItemAdapter();
    private Object[] mItemTags;
    private int mPrevSelectedItemPosition;
    private int mItemLayoutId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View fragView = inflater.inflate(R.layout.option_fragment, container, false);
        mTitleView = (TextView) fragView.findViewById(R.id.option_title);
        mTitleView.setText(mTitle);
        mOptionItemListView = (VerticalGridView) fragView.findViewById(R.id.option_list);
        mOptionItemListView.setAdapter(mAdapter);
        mLayoutInflater = inflater;
        return fragView;
    }

    public void initialize(String title, Object[] itemTags, int itemLayoutId) {
        Preconditions.checkState(!TextUtils.isEmpty(title));
        mTitle = title;
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
        mItemTags = itemTags;
        mItemLayoutId = itemLayoutId;
        mAdapter.notifyDataSetChanged();
    }

    public void setPrevSelectedItem(int position) {
        mPrevSelectedItemPosition = position;
    }

    public void setItems(String[] itemLabels, int selectedItem, Object[] tags) {
        Preconditions.checkState(tags == null || itemLabels.length == tags.length);
        Preconditions.checkState(itemLabels.length > 0);
        Preconditions.checkState(selectedItem < itemLabels.length);
        mPrevSelectedItemPosition = selectedItem;
        mAdapter.notifyDataSetChanged();
    }

    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
    }

    public void onItemSelected(View v, int position, Object tag) {
    }

    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
    }

    public void setSelectedPosition(int position) {
        mOptionItemListView.setSelectedPosition(position + 1);
        mOptionItemListView.requestFocus();
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
            if (position <= 0 || position >= getItemCount() - 1) {
                // The first and the last items are dummy items to give a margin.
                v.setVisibility(View.INVISIBLE);
            } else {
                // Since position 0 is a dummy item, recalculate the position.
                position = position - 1;
                v.setVisibility(View.VISIBLE);
                onBindView(v, position, mItemTags[position], position == mPrevSelectedItemPosition);
                v.setTag(R.id.TAG_OPTION_ITEM_POSITOIN, position);
            }
        }

        @Override
        public int getItemCount() {
            return mItemTags == null ? 0 : mItemTags.length + 2;
        }

        private class MyViewHolder extends RecyclerView.ViewHolder {
            MyViewHolder(View view) {
                super(view);
            }
        }
    }

    private static class ShadedItemContainer extends LinearLayout {
        public ShadedItemContainer(Context context) {
            this(context, null);
        }

        public ShadedItemContainer(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public ShadedItemContainer(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction,
                Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            if (isAttachedToWindow() && getVisibility() == View.VISIBLE) {
                if (gainFocus) {
                    setBackgroundColor(getContext().getResources().getColor(
                            R.color.option_item_focused_background));
                } else {
                    setBackgroundColor(getContext().getResources().getColor(
                            R.color.option_item_background));
                }
            }
        }
    }
}

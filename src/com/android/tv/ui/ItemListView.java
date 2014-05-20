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
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;

/*
 * A subclass of LinearLayout that shows a title and list view.
 */
public class ItemListView extends LinearLayout {
    public interface TileView {
        void loadViews();
        void populateViews(View.OnClickListener onClickListener, Object item);
    }

    private TextView mTitleView;
    private HorizontalGridView mListView;

    public ItemListView(Context context) {
        this(context, null, 0);
    }

    public ItemListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ItemListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void loadViews(int listViewHeight) {
        mTitleView = (TextView) findViewById(R.id.title);

        mListView = (HorizontalGridView) findViewById(R.id.list_view);
        ViewGroup.LayoutParams lp = mListView.getLayoutParams();
        lp.height = listViewHeight;
    }

    public void populateViews(String title, ItemListAdapter adapter) {
        mTitleView.setText(title);
        mListView.setAdapter(adapter);
    }

    public void setTitle(String title) {
        mTitleView.setText(title);
    }

    public void setSelectedPosition(int position) {
        mListView.setSelectedPosition(position);
    }

    public static class ItemListAdapter extends RecyclerView.Adapter {
        private final LayoutInflater mLayoutInflater;
        private final View.OnClickListener mOnClickListener;
        private final int mLayoutResId;
        private Object[] mItemList;
        private Handler mHandler;

        public ItemListAdapter(Context context, Handler handler, int layoutResId,
                View.OnClickListener onClickListener) {
            mHandler = handler;
            mLayoutResId = layoutResId;
            mLayoutInflater = LayoutInflater.from(context);
            mOnClickListener = onClickListener;
        }

        public void setItemList(Object[] itemList) {
            mItemList = itemList;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        private class MyViewHolder extends RecyclerView.ViewHolder {
            MyViewHolder(View view) {
                super(view);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflater.inflate(mLayoutResId, parent, false);
            ((TileView) view).loadViews();
            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder baseHolder, int position) {
            TileView view = (TileView) baseHolder.itemView;
            if (mItemList != null && position >= 0 && mItemList.length > position) {
                view.populateViews(mOnClickListener, mItemList[position]);
                if (view instanceof ViewGroup) {
                    final ViewGroup viewGroup = (ViewGroup) view;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                                viewGroup.getChildAt(i).requestLayout();
                            }
                        }
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return mItemList == null ? 0 : mItemList.length;
        }
    }
}
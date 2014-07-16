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

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvActivity;

import java.util.List;

public class DebugOptionFragment extends BaseSideFragment {
    private final boolean mSubOption;
    private final String mHeader;
    private final List<Item> mItems;

    public DebugOptionFragment() {
        mSubOption = false;
        mHeader = null;
        mItems = null;
    }

    private DebugOptionFragment(String header, List<Item> items) {
        mSubOption = true;
        mHeader = header;
        mItems = items;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        initialize(mHeader == null ? getString(R.string.menu_debug_options) : mHeader, null,
                R.layout.option_fragment, 0, R.color.option_item_background,
                R.color.option_item_focused_background, R.dimen.option_item_height);
        View view = super.onCreateView(inflater, container, savedInstanceState);
        VerticalGridView listView = (VerticalGridView) view.findViewById(R.id.side_panel_list);
        listView.setAdapter(new ItemAdapter(inflater, mItems == null ? buildItems() : mItems));
        setSelectedPosition(0);
        if (mSubOption) {
            view.findViewById(R.id.side_panel_shadow).setVisibility(View.GONE);
        }
        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (!mSubOption) {
            getTvActivity().onSideFragmentCanceled(getInitiator());
            getTvActivity().hideOverlays(false, false, true);
        }
    }

    protected List<Item> buildItems() {
        return null;
    }

    private TvActivity getTvActivity() {
        return (TvActivity) getActivity();
    }

    public static abstract class Item {
        protected abstract int getResourceId();
        protected void bind(@SuppressWarnings("unused") View view) { }
        protected void unbind() { }
        protected void onSelected() { }
        protected void onFocused() { }
    }

    public static class DividerItem extends Item {
        @Override
        protected int getResourceId() {
            return R.layout.debug_option_divider;
        }
    }

    public static class ActionItem extends Item {
        private final String mTitle;
        private TextView mTitleView;

        public ActionItem(String title) {
            mTitle = title;
        }

        @Override
        protected int getResourceId() {
            return R.layout.debug_option_action;
        }

        @Override
        protected void bind(View view) {
            mTitleView = (TextView) view.findViewById(R.id.title);
            mTitleView.setText(mTitle);
        }

        @Override
        protected void unbind() {
            mTitleView = null;
        }
    }

    public static class SubMenuItem extends Item {
        private final String mTitle;
        private final FragmentManager mFragmentManager;
        private TextView mTitleView;

        public SubMenuItem(String title, FragmentManager fragmentManager) {
            mTitle = title;
            mFragmentManager = fragmentManager;
        }

        @Override
        protected int getResourceId() {
            return R.layout.debug_option_sub_menu;
        }

        @Override
        protected void bind(View view) {
            mTitleView = (TextView) view.findViewById(R.id.title);
            mTitleView.setText(mTitle);
        }

        @Override
        protected void unbind() {
            mTitleView = null;
        }

        @Override
        protected void onSelected() {
            mFragmentManager
                .beginTransaction()
                .add(R.id.right_panel, new DebugOptionFragment(mTitle, buildItems()))
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
        }

        protected List<Item> buildItems() {
            return null;
        }
    }

    public static class CheckBoxItem extends Item {
        private final String mTitle;
        private boolean mChecked;
        private CheckBox mCheckBox;

        public CheckBoxItem(String title) {
            mTitle = title;
        }

        @Override
        protected int getResourceId() {
            return R.layout.debug_option_check_box;
        }

        @Override
        protected void bind(View view) {
            mCheckBox = (CheckBox) view.findViewById(R.id.check_box);
            mCheckBox.setText(mTitle);
            mCheckBox.setChecked(mChecked);
        }

        @Override
        protected void unbind() {
            mCheckBox = null;
        }

        @Override
        protected void onSelected() {
            setChecked(!mChecked);
        }

        public void setChecked(boolean checked) {
            if (mChecked != checked) {
                mChecked = checked;
                if (mCheckBox != null) {
                    mCheckBox.setChecked(mChecked);
                }
            }
        }
    }

    public static class RadioButtonItem extends Item {
        private final String mTitle;
        private boolean mChecked;
        private RadioButton mRadioButton;

        public RadioButtonItem(String title) {
            mTitle = title;
        }

        @Override
        protected int getResourceId() {
            return R.layout.debug_option_radio_button;
        }

        @Override
        protected void bind(View view) {
            mRadioButton = (RadioButton) view.findViewById(R.id.radio_button);
            mRadioButton.setText(mTitle);
            mRadioButton.setChecked(mChecked);
        }

        @Override
        protected void unbind() {
            mRadioButton = null;
        }

        @Override
        protected void onSelected() {
            setChecked(true);
        }

        public void setChecked(boolean checked) {
            if (mChecked != checked) {
                mChecked = checked;
                if (mRadioButton != null) {
                    mRadioButton.setChecked(mChecked);
                }
            }
        }
    }

    private static class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private final LayoutInflater mLayoutInflater;
        private final List<Item> mItems;

        private ItemAdapter(LayoutInflater layoutInflater, List<Item> items) {
            mLayoutInflater = layoutInflater;
            mItems = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflater.inflate(viewType, parent, false);
            final ViewHolder holder = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (holder.item instanceof RadioButtonItem) {
                        clearRadioGroup(holder.item);
                    }
                    holder.item.onSelected();
                }
            });
            view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focusGained) {
                    if (focusGained) {
                        holder.item.onFocused();
                    }
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.item = getItem(position);
            holder.item.bind(holder.itemView);
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.item.unbind();
            holder.item = null;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getResourceId();
        }

        @Override
        public int getItemCount() {
            return mItems == null ? 0 : mItems.size();
        }

        private Item getItem(int position) {
            return mItems.get(position);
        }

        private void clearRadioGroup(Item item) {
            int position = mItems.indexOf(item);
            for (int i = position - 1; i >= 0; --i) {
                if ((item = mItems.get(i)) instanceof RadioButtonItem) {
                    ((RadioButtonItem) item).setChecked(false);
                } else {
                    break;
                }
            }
            for (int i = position + 1; i < mItems.size(); ++i) {
                if ((item = mItems.get(i)) instanceof RadioButtonItem) {
                    ((RadioButtonItem) item).setChecked(false);
                } else {
                    break;
                }
            }
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            public Item item;

            private ViewHolder(View view) {
                super(view);
            }
        }
    }
}
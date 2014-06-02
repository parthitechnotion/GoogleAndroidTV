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
import android.support.v17.leanback.widget.OnChildSelectedListener;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.dialog.PrivacySettingDialogFragment;
import com.android.tv.recommendation.TvRecommendation;

import java.util.ArrayList;
import java.util.Arrays;

/*
 * A subclass of VerticalGridView that shows tv main menu.
 */
public class MainMenuView extends FrameLayout implements View.OnClickListener,
        OnChildSelectedListener {
    private static final int DUMMY_TYPE = 0;
    private static final int CHANNEL_LIST_TYPE = 1;
    private static final int OPTIONS_TYPE = 2;

    private static final int MAX_COUNT_FOR_RECOMMENDATION = 10;

    private final LayoutInflater mLayoutInflater;
    private VerticalGridView mMenuList;
    private final MainMenuAdapter mAdapter = new MainMenuAdapter();
    private ChannelMap mChannelMap;
    private TvActivity mTvActivity;
    private TvRecommendation mTvRecommendation;
    private ItemListView mSelectedList;
    private OptionsAdapter mOptionsAdapter;

    private final Handler mHandler = new Handler();

    private final ArrayList<ItemListView.ItemListAdapter> mAllAdapterList =
            new ArrayList<ItemListView.ItemListAdapter>();

    public MainMenuView(Context context) {
        this(context, null, 0);
    }

    public MainMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainMenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate(){
        mMenuList = (VerticalGridView) findViewById(R.id.menu_list);
        mMenuList.setOnChildSelectedListener(this);
        mMenuList.setAnimateChildLayout(false);
        mMenuList.setAdapter(mAdapter);
        mMenuList.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        mMenuList.setWindowAlignmentOffset(getContext().getResources().getDimensionPixelOffset(
                R.dimen.selected_row_alignment));
        mMenuList.setWindowAlignmentOffsetPercent(
                VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        mMenuList.setItemAlignmentOffset(0);
        mMenuList.setItemAlignmentOffsetPercent(
                VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
    }

    @Override
    protected void onAttachedToWindow() {
        Context context = getContext();

        // List for enabled channels
        mAllAdapterList.add(new ChannelListAdapter(context, mHandler, this, true, null,
                context.getResources().getDimensionPixelOffset(R.dimen.channel_list_view_height)));

        // List for recommended channels
        mTvRecommendation = new TvRecommendation(context, mHandler);
        mAllAdapterList.add(new RecommendationListAdapter(context, mHandler, this,
                mTvRecommendation, MAX_COUNT_FOR_RECOMMENDATION, R.layout.channel_tile,
                context.getString(R.string.recommended_channel_list_title),
                context.getResources().getDimensionPixelOffset(R.dimen.channel_list_view_height)));

        // List for options
        mOptionsAdapter = new OptionsAdapter(context, mHandler, this);
        mAllAdapterList.add(mOptionsAdapter);

        // Keep all items for the main menu
        mMenuList.setItemViewCacheSize(mAllAdapterList.size());
        updateAdapters(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        mOptionsAdapter = null;

        mAllAdapterList.clear();
        updateAdapters(false);

        mTvRecommendation.release();
        mTvRecommendation = null;
    }

    public void setTvActivity(TvActivity activity) {
        mTvActivity = activity;
    }

    public void setChannelMap(ChannelMap channelMap) {
        mChannelMap = channelMap;
        updateAdapters(true);
    }

    private void updateAdapters(boolean channelMapUpdateRequired) {
        ArrayList<ItemListView.ItemListAdapter> availableAdapterList =
                new ArrayList<ItemListView.ItemListAdapter>();
        for (ItemListView.ItemListAdapter adapter : mAllAdapterList) {
            if (channelMapUpdateRequired) {
                adapter.update(mChannelMap);
            }
            if (adapter.getItemCount() > 0) {
                availableAdapterList.add(adapter);
            }
        }

        mAdapter.setItemListAdapters(
                availableAdapterList.toArray(new ItemListView.ItemListAdapter[0]));
    }

    private void show() {
        if (mOptionsAdapter != null) {
            mOptionsAdapter.setMoreOptionsShown(false);
        }
        if (mChannelMap != null) {
            boolean adapterVisibilityChanged = false;

            for (ItemListView.ItemListAdapter adapter : mAllAdapterList) {
                int prevCount = adapter.getItemCount();
                adapter.onBeforeShowing();
                int currCount = adapter.getItemCount();
                if ((prevCount == 0 && currCount != 0) || (prevCount != 0 && currCount == 0)) {
                    adapterVisibilityChanged = true;
                }
            }

            if (adapterVisibilityChanged) {
                updateAdapters(false);
            }
        }

        mMenuList.setSelectedPosition(0);
        requestFocus();
        bringToFront();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        if (changedView == this && visibility == View.VISIBLE) {
            show();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setVisibility(View.INVISIBLE);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public static class MenuTag {
        static final int CHANNEL_TAG_TYPE = 0;
        static final int MENU_ACTION_TAG_TYPE = 1;

        private final int mType;
        private final Object mObj;

        private MenuTag(int type, Object obj) {
            mType = type;
            mObj = obj;
        }

        private MenuTag(Channel channel) {
            this(CHANNEL_TAG_TYPE, channel);
        }

        private MenuTag(MenuAction action) {
            this(MENU_ACTION_TAG_TYPE, action);
        }

        public static Object buildTag(Channel channel) {
            return new MenuTag(channel);
        }

        public static Object buildTag(MenuAction action) {
            return new MenuTag(action);
        }
    }

    @Override
    public void onClick(View v) {
        final MenuTag tag = (MenuTag) v.getTag();
        if (tag != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    switch (tag.mType) {
                        case MenuTag.CHANNEL_TAG_TYPE:
                            Channel channel = (Channel) tag.mObj;
                            if (channel.getType() == R.integer.channel_type_guide) {
                                Toast.makeText(getContext(), R.string.not_implemented_yet,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                mTvActivity.moveToChannel(channel.getId());
                            }
                            break;

                        case MenuTag.MENU_ACTION_TAG_TYPE:
                            MenuAction action = (MenuAction) tag.mObj;
                            switch (action.getType()) {
                                case MenuAction.SELECT_CLOSED_CAPTION:
                                    mTvActivity.showClosedCaptionOption();
                                    break;

                                case MenuAction.SELECT_ASPECT_RATIO:
                                    mTvActivity.showAspectRatioOption();
                                    break;

                                case MenuAction.SELECT_TV_INPUT_TYPE:
                                    mTvActivity.showInputPickerDialog();
                                    break;

                                case MenuAction.TOGGLE_PIP_TYPE:
                                    mTvActivity.togglePipView();
                                    break;

                                case MenuAction.MORE_TYPE:
                                    mOptionsAdapter.setMoreOptionsShown(true);
                                    break;

                                case MenuAction.EDIT_CHANNEL_LIST_TYPE:
                                    mTvActivity.showEditChannelsDialog();
                                    break;

                                case MenuAction.AUTO_SCAN_CHANNELS_TYPE:
                                    mTvActivity.startSetupActivity();
                                    break;

                                case MenuAction.PRIVACY_SETTING_TYPE:
                                    mTvActivity.showDialogFragment(
                                            PrivacySettingDialogFragment.DIALOG_TAG,
                                            new PrivacySettingDialogFragment());
                                    break;

                                case MenuAction.INPUT_SETTING_TYPE:
                                    mTvActivity.startSettingsActivity();
                                    break;
                            }
                            break;
                    }
                }
            });
            if (tag.mType == MenuTag.MENU_ACTION_TAG_TYPE
                    && ((MenuAction) tag.mObj).getType() == MenuAction.MORE_TYPE) {
                // Don't hide menu in the case of More.
                return;
            }
        }

        setVisibility(View.GONE);
    }

    @Override
    public void onChildSelected(ViewGroup parent, View child, int position, long id) {
        if (mSelectedList == child) {
            return;
        }
        for (int i = 0; i < mMenuList.getChildCount(); i++) {
            ItemListView v = (ItemListView) mMenuList.getChildAt(i);
            if (v != child) {
                v.onDeselected();
            }
        }
        mSelectedList = (ItemListView) child;
        if (mSelectedList != null) {
            mSelectedList.onSelected();
        }
    }

    class MainMenuAdapter extends RecyclerView.Adapter<MainMenuAdapter.MyViewHolder> {
        private ItemListView.ItemListAdapter[] mAdapters;

        public void setItemListAdapters(ItemListView.ItemListAdapter[] adapters) {
            if (!Arrays.equals(mAdapters, adapters)) {
                mAdapters = adapters;
                notifyDataSetChanged();
            }
        }

        @Override
        public int getItemViewType(int position) {
            ItemListView.ItemListAdapter[] adapters = mAdapters;
            if (adapters != null && position < adapters.length) {
                if (adapters[position] instanceof ChannelListAdapter) {
                    return CHANNEL_LIST_TYPE;
                }
                if (adapters[position] instanceof OptionsAdapter) {
                    return OPTIONS_TYPE;
                }
            }

            return DUMMY_TYPE;
        }

        private class MyViewHolder extends RecyclerView.ViewHolder {
            MyViewHolder(View view) {
                super(view);
            }
        }

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflater.inflate(R.layout.item_list, parent, false);
            if (viewType == OPTIONS_TYPE) {
                int interCardSpacing = getContext().getResources().getDimensionPixelSize(
                        R.dimen.action_tile_inter_card_spacing);
                ((HorizontalGridView) view.findViewById(R.id.list_view)).setHorizontalMargin(
                        interCardSpacing);
            }
            ((ItemListView) view).loadViews();

            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MyViewHolder baseHolder, int position) {
            ItemListView.ItemListAdapter[] adapters = mAdapters;
            if (adapters != null && position < adapters.length) {
                ItemListView listView = (ItemListView) baseHolder.itemView;
                ItemListView.ItemListAdapter adapter = mAdapters[position];
                listView.populateViews(adapter.getTitle(), adapter);
                adapter.update(mChannelMap, listView);
            }
        }

        @Override
        public int getItemCount() {
            ItemListView.ItemListAdapter[] adapters = mAdapters;
            return adapters == null ? 0 : adapters.length;
        }
    }
}

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
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.dialog.PrivacySettingDialogFragment;
import com.android.tv.util.Utils;

import java.util.ArrayList;

/*
 * A subclass of VerticalGridView that shows tv main menu.
 */
public class MainMenuView extends VerticalGridView implements View.OnClickListener {
    private static final int DUMMY_TYPE = 0;
    private static final int CHANNEL_LIST_TYPE = 1;
    private static final int OPTIONS_TYPE = 2;

    private final LayoutInflater mLayoutInflater;
    private final MainMenuAdapter mAdapter = new MainMenuAdapter();
    private ChannelMap mChannelMap;
    private TvActivity mTvActivity;
    private final Handler mHandler = new Handler();

    private final ArrayList<ItemListView.ItemListAdapter> mAllAdapterList =
            new ArrayList<ItemListView.ItemListAdapter>();

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (mChannelMap != null) {
                        updateAdapters();
                    }
                }
            };

    public MainMenuView(Context context) {
        this(context, null, 0);
    }

    public MainMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainMenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLayoutInflater = LayoutInflater.from(context);

        setAdapter(mAdapter);

        // List for enabled channels
        mAllAdapterList.add(new ChannelListAdapter(context, mHandler, this, true, null,
                context.getResources().getDimensionPixelOffset(R.dimen.channel_list_view_height)));

        // List for options
        mAllAdapterList.add(new OptionsAdapter(context, mHandler, this));

        // Keep all items for the main menu
        setItemViewCacheSize(mAllAdapterList.size());
    }

    @Override
    protected void onAttachedToWindow() {
        Utils.getSharedPreferencesOfDisplayNameForInput(getContext())
                .registerOnSharedPreferenceChangeListener(mPrefChangeListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        Utils.getSharedPreferencesOfDisplayNameForInput(getContext())
                .unregisterOnSharedPreferenceChangeListener(mPrefChangeListener);
    }

    public void setTvActivity(TvActivity activity) {
        mTvActivity = activity;
    }

    public void setChannelMap(ChannelMap channelMap) {
        mChannelMap = channelMap;
        updateAdapters();
    }

    private void updateAdapters() {
        ArrayList<ItemListView.ItemListAdapter> availableAdapterList =
                new ArrayList<ItemListView.ItemListAdapter>();
        for (ItemListView.ItemListAdapter adapter : mAllAdapterList) {
            adapter.update(mChannelMap);
            if (adapter.getItemCount() > 0) {
                availableAdapterList.add(adapter);
            }
        }

        mAdapter.setItemListAdapters(
                availableAdapterList.toArray(new ItemListView.ItemListAdapter[0]));
    }

    private void show() {
        if (mChannelMap != null) {
            long id = mChannelMap.getCurrentChannelId();

            for (ItemListView.ItemListAdapter adapter : mAllAdapterList) {
                if (adapter instanceof ChannelListAdapter) {
                    ((ChannelListAdapter) adapter).setCurrentChannelId(id);
                }
            }
        }

        setSelectedPosition(0);
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
                            mTvActivity.moveToChannel(channel.getId());
                            break;

                        case MenuTag.MENU_ACTION_TAG_TYPE:
                            MenuAction action = (MenuAction) tag.mObj;
                            switch (action.getType()) {
                                case MenuAction.SELECT_TV_INPUT_TYPE:
                                    mTvActivity.showInputPickerDialog();
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

                                case MenuAction.TOGGLE_PIP_TYPE:
                                    mTvActivity.togglePipView();
                                    break;

                                case MenuAction.MORE_TYPE:
                                    mTvActivity.startSettingsActivity();
                                    break;
                            }
                            break;
                    }
                }
            });
        }

        setVisibility(View.GONE);
    }

    class MainMenuAdapter extends RecyclerView.Adapter<MainMenuAdapter.MyViewHolder> {
        private ItemListView.ItemListAdapter[] mAdapters;

        public void setItemListAdapters(ItemListView.ItemListAdapter[] adapters) {
            mAdapters = adapters;
            notifyDataSetChanged();
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

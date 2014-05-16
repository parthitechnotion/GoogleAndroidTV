// Copyright 2014 Google Inc. All Rights Reserved.

package com.android.tv.ui;

import com.android.tv.Channel;
import com.android.tv.ChannelMap;
import com.android.tv.PrivacySettingDialogFragment;
import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.Utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.TvContract;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.tv.TvInputInfo;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/*
 * A subclass of VerticalGridView that shows tv main menu.
 */
public class MainMenuView extends VerticalGridView implements View.OnClickListener {
    private static final String TAG = "MainMenuView";

    private static final int MENU_COUNT = 2;
    private static final int ALL_CHANNEL_LIST_MENU_TYPE = 0;
    private static final int SETTINGS_MENU_TYPE = 1;

    private LayoutInflater mLayoutInflater;
    private MainMenuAdapter mAapter = new MainMenuAdapter();
    private ChannelMap mChannelMap;
    private TvActivity mTvActivity;
    private Handler mHandler = new Handler();

    private ChannelListAdapter mAllChannelListAdapter;
    private OptionsAdapter mOptionsAdapter;

    public MainMenuView(Context context) {
        this(context, null, 0);
    }

    public MainMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainMenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLayoutInflater = LayoutInflater.from(context);
        setAdapter(mAapter);

        mAllChannelListAdapter = new ChannelListAdapter(context, this);
        mOptionsAdapter = new OptionsAdapter(context, this);
    }

    public void setTvActivity(TvActivity activity) {
        mTvActivity = activity;
    }

    public void setChannelMap(ChannelMap channelMap) {
        mChannelMap = channelMap;
        mAllChannelListAdapter.update(channelMap);
        mOptionsAdapter.update(channelMap);
    }

    private void show() {
        if (mChannelMap != null) {
            long id = mChannelMap.getCurrentChannelId();
            mAllChannelListAdapter.setCurrentChannelId(id);
        }

        setSelectedPosition(0);
        requestFocus();
        bringToFront();
    }

    @Override
    protected void onVisibilityChanged (View changedView, int visibility) {
        if (visibility == View.VISIBLE) {
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

        private int mType;
        private Object mObj;

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

    class MainMenuAdapter extends RecyclerView.Adapter {
        @Override
        public int getItemViewType(int position) {
            return position;
        }

        private class MyViewHolder extends RecyclerView.ViewHolder {
            MyViewHolder(View view) {
                super(view);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            view = mLayoutInflater.inflate(R.layout.item_list, parent, false);

            int listViewHeight = 0;
            switch (viewType) {
                case ALL_CHANNEL_LIST_MENU_TYPE:
                    listViewHeight = mContext.getResources().getDimensionPixelOffset(
                            R.dimen.channel_list_view_height);
                    break;
                case SETTINGS_MENU_TYPE:
                    listViewHeight = mContext.getResources().getDimensionPixelOffset(
                            R.dimen.action_list_view_height);
                    break;

                default:
                    throw new IllegalArgumentException("unexpected view type: " + viewType);
            }
            ((ItemListView) view).loadViews(listViewHeight);

            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder baseHolder, int position) {
            int viewType = position;
            ItemListView listView = null;
            switch (viewType) {
                case ALL_CHANNEL_LIST_MENU_TYPE:
                    listView = (ItemListView) baseHolder.itemView;

                    listView.populateViews(null, mAllChannelListAdapter);
                    mAllChannelListAdapter.update(mChannelMap, listView);
                    break;

                case SETTINGS_MENU_TYPE:
                    listView = (ItemListView) baseHolder.itemView;
                    listView.populateViews(mContext.getString(R.string.menu_title),
                            mOptionsAdapter);
                    mOptionsAdapter.update(mChannelMap);
                    break;

                default:
                    throw new IllegalArgumentException("unexpected view type: " + viewType);
            }
        }

        @Override
        public int getItemCount() {
            return MENU_COUNT;
        }
    }
}
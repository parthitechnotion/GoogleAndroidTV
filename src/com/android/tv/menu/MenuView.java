/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.menu;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.IntDef;
import android.support.v17.leanback.widget.OnChildSelectedListener;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import com.android.tv.ChannelTuner;
import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.analytics.DurationTimer;
import com.android.tv.analytics.Tracker;
import com.android.tv.customization.CustomAction;
import com.android.tv.customization.TvCustomizationManager;
import com.android.tv.data.Channel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A subclass of VerticalGridView that shows TV main menu.
 */
public class MenuView extends FrameLayout implements OnChildSelectedListener {
    static final String TAG = "MenuView";
    static final boolean DEBUG = false;

    // TODO: Change the status to STATUS_NONE when the animation for STATUS_CHILD_SELECTING
    // is ended.
    public static final int STATUS_CHILD_SELECTING = 3;
    public static final String SCREEN_NAME = "Menu";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REASON_NONE, REASON_GUIDE, REASON_PLAY_CONTROLS_PLAY, REASON_PLAY_CONTROLS_PAUSE,
        REASON_PLAY_CONTROLS_PLAY_PAUSE, REASON_PLAY_CONTROLS_REWIND,
        REASON_PLAY_CONTROLS_FAST_FORWARD, REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS,
        REASON_PLAY_CONTROLS_JUMP_TO_NEXT})
    public @interface MenuShowReason {}
    public static final int REASON_NONE = 0;
    public static final int REASON_GUIDE = 1;
    public static final int REASON_PLAY_CONTROLS_PLAY = 2;
    public static final int REASON_PLAY_CONTROLS_PAUSE = 3;
    public static final int REASON_PLAY_CONTROLS_PLAY_PAUSE = 4;
    public static final int REASON_PLAY_CONTROLS_REWIND = 5;
    public static final int REASON_PLAY_CONTROLS_FAST_FORWARD = 6;
    public static final int REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS = 7;
    public static final int REASON_PLAY_CONTROLS_JUMP_TO_NEXT = 8;

    public static final List<String> sRowIdListForReason = new ArrayList<>();
    static {
        sRowIdListForReason.add(null);  // REASON_NONE
        sRowIdListForReason.add(ChannelsRow.ID);  // REASON_GUIDE
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_PLAY
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_PAUSE
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_PLAY_PAUSE
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_REWIND
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_FAST_FORWARD
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS
        sRowIdListForReason.add(PlayControlsRow.ID);  // REASON_PLAY_CONTROLS_JUMP_TO_NEXT
    }

    private final LayoutInflater mLayoutInflater;
    private VerticalGridView mMenuList;
    private final MenuAdapter mAdapter = new MenuAdapter();
    private ChannelTuner mChannelTuner;
    private int mPreviousSelectedPosition;

    private Runnable mPreShowRunnable;
    private Runnable mPostHideRunnable;

    private final Animator mShowAnimator;
    private final Animator mHideAnimator;
    private final int mMenuHeight;
    private final int mMenuRowTitleHeight;
    private final int mMenuRowPaddingHeight;
    private final long mShowDurationMillis;
    private final int mRowSelectionAnimationDurationMs;
    private final OverScroller mScroller;
    private final DurationTimer mVisibleTimer = new DurationTimer();

    private ChannelsRow mChannelsRow;

    private Tracker mTracker;

    private boolean mKeepVisible;
    @MenuShowReason private int mShowReason = REASON_NONE;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide(true);
        }
    };

    private final ChannelTuner.Listener mChannelTunerListener = new ChannelTuner.Listener() {
        @Override
        public void onLoadFinished() {}

        @Override
        public void onBrowsableChannelListChanged() {
            update();
        }

        @Override
        public void onCurrentChannelUnavailable(Channel channel) {}

        @Override
        public void onChannelChanged(Channel previousChannel, Channel currentChannel) {}
    };

    public MenuView(Context context) {
        this(context, null, 0);
    }

    public MenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLayoutInflater = LayoutInflater.from(context);
        mShowAnimator = AnimatorInflater.loadAnimator(context, R.animator.menu_enter);
        mShowAnimator.setTarget(this);
        mHideAnimator = AnimatorInflater.loadAnimator(context, R.animator.menu_exit);
        mHideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Animation is still in running state at this point.
                hideInternal();
            }
        });
        mHideAnimator.setTarget(this);

        Resources res = context.getResources();
        mShowDurationMillis = res.getInteger(R.integer.menu_show_duration);
        mMenuHeight = res.getDimensionPixelSize(R.dimen.menu_height);
        mMenuRowTitleHeight = res.getDimensionPixelSize(R.dimen.menu_row_title_height);
        mMenuRowPaddingHeight = res.getDimensionPixelOffset(R.dimen.menu_list_padding_top)
                + res.getDimensionPixelOffset(R.dimen.menu_list_padding_bottom)
                + res.getDimensionPixelOffset(R.dimen.menu_list_margin_top);
        mRowSelectionAnimationDurationMs =
                res.getInteger(R.integer.menu_row_selection_anim_duration);

        mScroller = new OverScroller(context);
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getContext();
    }

    /**
     * This method will be called from MainActivity.onStart()
     */
    public void onStart() {
        Context context = getContext();

        // Menu list(VerticalGridView) should be refreshed to forget the previous status.
        // If not, mMenuList.setSelectedPosition() would not work properly.
        mAdapter.notifyDataSetChanged();

        MainActivity mainActivity = getMainActivity();
        mTracker= ((TvApplication) mainActivity.getApplication()).getTracker();

        // Build menu rows
        TvCustomizationManager manager = mainActivity.getTvCustomizationManager();
        List<MenuRow> itemList = new ArrayList<>();
        itemList.add(new PlayControlsRow(context));
        itemList.add(mChannelsRow = new ChannelsRow(context));
        List<CustomAction> customActions =
                manager.getCustomActions(TvCustomizationManager.ID_PARTNER_ROW);
        String title = manager.getPartnerRowTitle();
        if (customActions != null && !TextUtils.isEmpty(title)) {
            itemList.add(new PartnerRow(context, title, customActions));
        }
        itemList.add(new TvOptionsRow(
                context, manager.getCustomActions(TvCustomizationManager.ID_OPTIONS_ROW)));
        itemList.add(new PipOptionsRow(context));

        mAdapter.setItemList(itemList);
    }

    /**
     * This method will be called from MainActivity.onStop()
     */
    public void onStop() {
        mAdapter.resetItemList();
    }

    /**
     * This method will be called when channels are updated.
     */
    public void onRecentChannelUpdated() {
        if (mChannelsRow != null) {
            mChannelsRow.onRecentChannelUpdated();
        }
    }

    @Override
    protected void onFinishInflate() {
        mMenuList = (VerticalGridView) findViewById(R.id.menu_list);
        mMenuList.setOnChildSelectedListener(this);
        mMenuList.setScrollEnabled(false);
        mMenuList.setAdapter(mAdapter);
        // TODO: Use alignment features of GridView once the bugs of the features are fixed.
        // NOTE: There's a problem that the menu jumps up/down, if a row whose position is less than
        // the selected position is inserted or removed while the menu is displayed.
        // The reason is because we use OverScroller to scroll the rows.
    }

    public void setPreShowCallback(Runnable preShowRunnable) {
        mPreShowRunnable = preShowRunnable;
    }

    public void setPostHideCallback(Runnable postHideRunnable) {
        mPostHideRunnable = postHideRunnable;
    }

    public boolean isActive() {
        return getVisibility() == View.VISIBLE && !isHiding();
    }

    public boolean isHiding() {
        return mHideAnimator.isStarted();
    }

    /**
     * Returns the padding to the height of the item.
     *
     * <p>It is used to calculate the exact height of the item.
     */
    public int getItemPaddingHeight() {
        return mMenuRowPaddingHeight;
    }

    /**
     * Shows the main menu.
     *
     * @param reason A reason why this is called. See {@link MenuShowReason}
     */
    public void show(@MenuShowReason int reason) {
        if (DEBUG) Log.d(TAG, "show reason:" + reason);
        mTracker.sendShowMenu();
        mVisibleTimer.start();
        mShowReason = reason;
        if (isHiding()) {
            mHideAnimator.end();
        }
        String rowIdToSelect = sRowIdListForReason.get(reason);
        if (getVisibility() == View.VISIBLE) {
            if (rowIdToSelect != null) {
                int position = mAdapter.getItemPosition(rowIdToSelect);
                if (position >= 0) {
                    for (int i = 0; i < mMenuList.getChildCount(); ++i) {
                        MenuRowView rowView = (MenuRowView) mMenuList.getChildAt(i);
                        if (rowIdToSelect.equals(rowView.getRowId())) {
                            rowView.initialize(reason);
                            break;
                        }
                    }
                    mMenuList.setSelectedPosition(position);
                    requestFocus();
                }
            }
            return;
        }
        if (rowIdToSelect == null) {
            rowIdToSelect = ChannelsRow.ID;
        }
        // The child row views need be initialized before they become visible.
        initializeChildren();
        setVisibility(View.VISIBLE);
        mTracker.sendScreenView(SCREEN_NAME);
        if (mPreShowRunnable != null) {
            mPreShowRunnable.run();
        }
        if (update()) {
            // To apply the row insertion or removal immediately,
            // notifyDataSetChanged need to be called after update.
            // If we don't call this, the intermediate state might be shown.
            mAdapter.notifyDataSetChanged();
        }
        int positionToSelect = mAdapter.getItemPosition(rowIdToSelect);
        resetSelectedItemPosition(positionToSelect);
        requestFocus();

        // Abort animation because the scroll animation can occur while updating the adapter above.
        mScroller.abortAnimation();
        setScrollY(getScrollPosition(positionToSelect));
        mShowAnimator.start();
        scheduleHide();
    }

    int getItemPositionY(int position) {
        return mMenuHeight - mMenuRowTitleHeight - mAdapter.getItemHeight(position);
    }

    private void initializeChildren() {
        for (int i = 0, count = mMenuList.getChildCount(); i < count; ++i) {
            MenuRowView rowView = (MenuRowView) mMenuList.getChildAt(i);
            rowView.initialize(mShowReason);
        }
    }

    private void resetSelectedItemPosition(int positionToSelect) {
        mPreviousSelectedPosition = positionToSelect;
        if (DEBUG) Log.d(TAG, "Row count of the main menu is " + mMenuList.getChildCount());
        /*
         * Must reset mMenuList's selected position after resetting selected position of child
         * ListView. Otherwise it can be changed while resetting child ListView.
         */
        mMenuList.setSelectedPosition(mPreviousSelectedPosition);
        for (int i = 0, count = mMenuList.getChildCount(); i < count; ++i) {
            MenuRowView rowView = (MenuRowView) mMenuList.getChildAt(i);
            if (DEBUG) {
                Log.d(TAG, "The child position of the row " + i + " is "
                        + mMenuList.getChildAdapterPosition(rowView));
            }
            rowView.updateView(false);
        }
    }

    public void hide(boolean withAnimation) {
        removeCallbacks(mHideRunnable);
        if (withAnimation) {
            if (!isHiding()) {
                mHideAnimator.start();
            }
            return;
        }
        if (isHiding()) {
            mHideAnimator.end();
            return;
        }
        hideInternal();
    }

    private void hideInternal() {
        if (getVisibility() == View.GONE) {
            return;
        }
        mTracker.sendHideMenu(mVisibleTimer.reset());
        setVisibility(View.GONE);
        if (mPostHideRunnable != null) {
            mPostHideRunnable.run();
        }
    }

    public void scheduleHide() {
        removeCallbacks(mHideRunnable);
        if (!mKeepVisible) {
            postDelayed(mHideRunnable, mShowDurationMillis);
        }
    }

    /**
     * Called when the caller wants the main menu to be kept visible or not.
     * If {@code keepVisible} is set to {@code true}, the hide schedule doesn't close the main menu,
     * but calling {@link #hide} still hides it.
     * If {@code keepVisible} is set to {@code false}, the hide schedule works as usual.
     */
    public void setKeepVisible(boolean keepVisible) {
        mKeepVisible = keepVisible;
        if (mKeepVisible) {
            removeCallbacks(mHideRunnable);
        } else if (isActive()) {
            scheduleHide();
        }
    }

    public void setChannelTuner(ChannelTuner channelTuner) {
        if (mChannelTuner != null) {
            mChannelTuner.removeListener(mChannelTunerListener);
        }
        mChannelTuner = channelTuner;
        if (mChannelTuner != null) {
            mChannelTuner.addListener(mChannelTunerListener);
        }
        update();
    }

    /**
     * Updates the options row.
     */
    public void updateOptionsRow() {
        if (DEBUG) {
            Log.d(TAG, "update options row in main menu");
        }
        mAdapter.updateOptionsRow();
    }

    /**
     * Updates the adapter.
     *
     * <p>Returns <@code true> if the adapter has been changed, otherwise {@code false}.
     */
    public boolean update() {
        if (DEBUG) {
            Log.d(TAG, "update main menu");
        }
        return mAdapter.update();
    }

    /**
     * Returns a duration of the animation when the row selection changes.
     */
    public int getRowSelectionAnimationDurationMs() {
        return mRowSelectionAnimationDurationMs;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            setScrollY(mScroller.getCurrY());
            invalidate();
        }
    }

    private boolean scrollYSmooth(int position) {
        int newScrollPosition = getScrollPosition(position);
        if (newScrollPosition == getScrollY()) {
            return false;
        }
        mScroller.startScroll(0, getScrollY(), 0, newScrollPosition - getScrollY(),
                mRowSelectionAnimationDurationMs);
        return true;
    }

    private int getScrollPosition(int selectedPosition) {
        int visibleHeight = mMenuRowTitleHeight * selectedPosition
                + mAdapter.getItemHeight(selectedPosition);
        boolean lastItem = selectedPosition == mAdapter.getItemCount() - 1;
        if (!lastItem) {
            visibleHeight += mMenuRowTitleHeight;
        }
        return visibleHeight - mMenuHeight;
    }

    @Override
    public void onChildSelected(ViewGroup parent, View child, int position, long id) {
        boolean withAnimation = mPreviousSelectedPosition != position;
        for (int i = 0; i < mMenuList.getChildCount(); i++) {
            MenuRowView rowView = (MenuRowView) mMenuList.getChildAt(i);
            rowView.updateView(withAnimation);
        }
        mPreviousSelectedPosition = position;
        if (withAnimation) {
            mScroller.abortAnimation();
            scrollYSmooth(position);
        }
    }

    /**
     * Returns the previous selected position.
     */
    public int getPreviousSelectedPosition() {
        return mPreviousSelectedPosition;
    }

    private class MenuAdapter extends RecyclerView.Adapter<MenuViewHolder> {
        private List<MenuRow> mAllItems = Collections.emptyList();
        private List<MenuRow> mVisibleItems = new ArrayList<>();

        private void setItemList(List<MenuRow> items) {
            mAllItems = items;
            updateVisibleItems();
        }

        private void resetItemList() {
            for (MenuRow item : mAllItems) {
                item.release();
            }
            setItemList(Collections.<MenuRow>emptyList());
        }

        private void updateOptionsRow() {
            if (isActive()) {
                for (MenuRow item : mAllItems) {
                    if (item.getId().equals(TvOptionsRow.ID)) {
                        item.update();
                    }
                }
            }
        }

        private boolean update() {
            if (isActive()) {
                for (MenuRow item : mAllItems) {
                    item.update();
                }
                return updateVisibleItems();
            }
            return false;
        }

        private boolean updateVisibleItems() {
            // To preserve the item focus, we need a fine-grained control using notifyItemXXXed()
            // instead of using notifyDataSetChanged().
            // We assume that the order of the adapters will not be changed.
            List<MenuRow> oldVisibleItems = mVisibleItems;
            mVisibleItems = new ArrayList<>();
            boolean changed = false;
            int oldSelectedPosition = mMenuList.getSelectedPosition();
            MenuRow oldSelectedRow = null;
            if (oldSelectedPosition >= 0 && oldSelectedPosition < oldVisibleItems.size()) {
                oldSelectedRow = oldVisibleItems.get(oldSelectedPosition);
            }
            int position = 0;
            int newSelectedPosition = 0;
            for (MenuRow item : mAllItems) {
                if (item.isVisible()) {
                    mVisibleItems.add(item);
                    if (!oldVisibleItems.contains(item)) {
                        notifyItemInserted(position);
                        changed = true;
                    }
                    if (item.equals(oldSelectedRow)) {
                        newSelectedPosition = position;
                    }
                    ++position;
                } else if (oldVisibleItems.contains(item)) {
                    notifyItemRemoved(position);
                    changed = true;
                }
            }
            if (DEBUG) Log.d(TAG, "Visible item count is " + mVisibleItems.size());
            if (changed && scrollYSmooth(newSelectedPosition)) {
                // Call invalidate() to make sure that computeScroll() is invoked.
                invalidate();
            }
            return changed;
        }

        @Override
        public int getItemViewType(int position) {
            // Each row needs to have a unique view type to avoid messing the focus up.
            // If a row is recycled from a view of another type, the previous focus will not be
            // preserved.
            return mVisibleItems.get(position).getId().hashCode();
        }

        @Override
        public MenuViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            for (MenuRow item : mVisibleItems) {
                if (viewType == item.getId().hashCode()) {
                    MenuRowView view = (MenuRowView) mLayoutInflater.inflate(item.getLayoutResId(),
                            parent, false);
                    view.setMenuView(MenuView.this);
                    view.setParentView(mMenuList);
                    return new MenuViewHolder(view);
                }
            }
            // Main menu is in the illegal state.
            Log.e(TAG, "Error in creating view holder", new IllegalStateException(
                    "Can't create view holder due to the invalid view type " + viewType));
            return null;
        }

        @Override
        public void onBindViewHolder(MenuViewHolder viewHolder, int position) {
            MenuRowView itemView = (MenuRowView) viewHolder.itemView;
            MenuRow item = mVisibleItems.get(position);
            itemView.onBind(item);
            itemView.initialize(mShowReason);
        }

        @Override
        public int getItemCount() {
            return mVisibleItems.size();
        }

        private int getItemPosition(String rowIdToSelect) {
            if (rowIdToSelect == null) {
                return -1;
            }
            int position = 0;
            for (MenuRow item : mVisibleItems) {
                if (rowIdToSelect.equals(item.getId())) {
                    return position;
                }
                ++position;
            }
            return -1;
        }

        private int getItemHeight(int position) {
            if (position < 0 || position >= mVisibleItems.size()) {
                return mMenuRowTitleHeight;
            }
            return mVisibleItems.get(position).getHeight() + mMenuRowPaddingHeight
                    + mMenuRowTitleHeight;
        }
    }

    private static class MenuViewHolder extends RecyclerView.ViewHolder {
        MenuViewHolder(View view) {
            super(view);
        }
    }

    private static class TvOptionsRow extends ItemListRow {
        private static final String ID = TvOptionsRow.class.getName();
        public TvOptionsRow(Context context, List<CustomAction> customActions) {
            super(context, R.string.menu_title_options, R.dimen.action_card_height,
                    new TvOptionsRowAdapter(context, customActions));
        }

        @Override
        public String getId() {
            return ID;
        }
    }

    private static class PipOptionsRow extends ItemListRow {
        public PipOptionsRow(Context context) {
            super(context, R.string.menu_title_pip_options, R.dimen.action_card_height,
                    new PipOptionsRowAdapter(context));
        }

        @Override
        public boolean isVisible() {
            return super.isVisible() && getMainActivity().isPipEnabled();
        }
    }

    private static class PartnerRow extends ItemListRow {
        public PartnerRow(Context context, String title, List<CustomAction> customActions) {
            super(context, title, R.dimen.action_card_height,
                    new PartnerOptionsRowAdapter(context, customActions));
        }
    }
}

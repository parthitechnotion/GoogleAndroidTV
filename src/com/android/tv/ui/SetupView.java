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

package com.android.tv.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvInputInfo;
import android.support.annotation.VisibleForTesting;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SetupView extends FullscreenDialogView {
    private static final int FINISH_ACTIVITY_DELAY_MS = 200;
    private static final int REFRESH_DELAY_MS_AFTER_WINDOW_FOCUS_GAINED = 200;

    private VerticalGridView mInputView;
    private ChannelDataManager mChannelDataManager;
    private List<TvInputInfo> mInputList;
    // mInputList[0:mKnownInputStartIndex - 1] are new inputs.
    // And mInputList[mKnownInputStartIndex:end] are inputs which have been shown in SetupView.
    private int mKnownInputStartIndex;
    private boolean mShowDivider;
    private SetupAdapter mAdapter;
    private boolean mClosing;
    private boolean mInitialized;
    private SetupUtils mSetupUtils;
    private boolean mNeedIntroDialog;

    private final ChannelDataManager.Listener mChannelDataListener = new ChannelDataManager.Listener() {
        @Override
        public void onLoadFinished() { }

        @Override
        public void onChannelListUpdated() {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onChannelBrowsableChanged() {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    public SetupView(Context context) {
        this(context, null, 0);
    }

    public SetupView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SetupView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setTransitionAnimationEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        TextView titleView = (TextView) findViewById(R.id.setup_title);
        titleView.setText(R.string.setup_title);
        TextView descriptionView = (TextView) findViewById(R.id.setup_description);
        descriptionView.setText(R.string.setup_description);
        mInputView = (VerticalGridView) findViewById(R.id.input_list);
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.setup_item_window_alignment_offset_percent, outValue, true);
        mInputView.setWindowAlignmentOffsetPercent(outValue.getFloat());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mClosing || super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus && mAdapter != null) {
            // Without the following delay, the channel count description is sometimes
            // changed twice by this method and mChannelDataListener.
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    // When channel count is still 0 after setup, the description should be changed
                    // from "Not set up" to "No channels".
                    if (mAdapter.getItemCount() != 0) {
                        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
                    }
                }
            }, REFRESH_DELAY_MS_AFTER_WINDOW_FOCUS_GAINED);
        }
    }

    /**
     * Initializes SetupView.
     */
    @Override
    public void initialize(MainActivity activity, Dialog dialog) {
        super.initialize(activity, dialog);
        if (mInitialized) {
            throw new IllegalStateException("initialize() is called more than once");
        }
        mInitialized = true;
        final TvInputManagerHelper inputManager = getActivity().getTvInputManagerHelper();
        mChannelDataManager = getActivity().getChannelDataManager();
        mSetupUtils = SetupUtils.getInstance(activity);
        mKnownInputStartIndex = 0;
        mInputList = inputManager.getTvInputInfos(true, true);
        Collections.sort(mInputList, new TvInputInfoComparator(mSetupUtils, inputManager));
        for (TvInputInfo input : mInputList) {
            if (mSetupUtils.isNewInput(input.getId())) {
                mSetupUtils.markAsKnownInput(input.getId());
                ++mKnownInputStartIndex;
            }
        }
        mShowDivider = mKnownInputStartIndex != 0 && mKnownInputStartIndex != mInputList.size();
        mAdapter = new SetupAdapter();
        mInputView.setAdapter(mAdapter);
        mChannelDataManager.addListener(mChannelDataListener);
        mNeedIntroDialog = mSetupUtils.isFirstTune();
    }

    /**
     * Called when the DialogFragment including this view is destroyed.
     */
    @Override
    public void onDestroy() {
        mChannelDataManager.removeListener(mChannelDataListener);
    }

    @Override
    protected void dismiss() {
        mClosing = true;
        if (mNeedIntroDialog) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            IntroView v = (IntroView) inflater.inflate(R.layout.intro_dialog, null);
            transitionTo(v);
        } else {
            super.dismiss();
        }
    }
    /**
     * Called when the back key is pressed.
     */
    @Override
    public void onBackPressed() {
        if (mChannelDataManager.getChannelCount() == 0) {
            // If there is no channel, we finish the activity rather than closing just the view.
            getActivity().finish();
        }
        dismiss();
    }

    private class SetupAdapter extends RecyclerView.Adapter<MyViewHolder> {
        @Override
        public int getItemViewType(int position) {
            if (mShowDivider && position == mKnownInputStartIndex) {
                return R.layout.setup_item_divider;
            } else if (position == getItemCount() - 1) {
                return R.layout.setup_item_action;
            } else {
                return R.layout.setup_item_input;
            }
        }

        @Override
        public int getItemCount() {
            if (mInputList == null) {
                return 1;
            }
            return mInputList.size() + 1 + (mShowDivider ? 1 : 0);
        }

        @Override
        public void onBindViewHolder(final MyViewHolder viewHolder, int position) {
            if (position == getItemCount() - 1) {
                final boolean closeActivity = mChannelDataManager.getChannelCount() == 0;
                viewHolder.mTitle.setText(R.string.setup_done_button_label);
                viewHolder.itemView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mClosing = true;
                        if (closeActivity) {
                            // To wait completing ripple animation, finish() is called
                            // FINISH_ACTIVITY_DELAY_MS later.
                            mNeedIntroDialog = false;
                            postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    getActivity().finish();
                                }
                            }, FINISH_ACTIVITY_DELAY_MS);
                        } else {
                            dismiss();
                        }
                    }
                });
            } else {
                if (mShowDivider) {
                    if (position == mKnownInputStartIndex) {
                        // This view is a divider.
                        return;
                    } else if (position > mKnownInputStartIndex) {
                        --position;
                    }
                }
                final TvInputInfo input = mInputList.get(position);
                viewHolder.mTitle.setText(input.loadLabel(getContext()));
                int channelCount = mChannelDataManager.getChannelCountForInput(input.getId());
                if (mSetupUtils.hasSetupLaunched(input.getId())) {
                    if (channelCount == 0) {
                        viewHolder.mDescription.setText(R.string.setup_input_no_channels);
                    } else {
                        viewHolder.mDescription.setText(getResources().getQuantityString(
                                R.plurals.setup_input_channels, channelCount, channelCount));
                    }
                } else if (position >= mKnownInputStartIndex) {
                    viewHolder.mDescription.setText(R.string.channel_description_setup_now);
                } else {
                    viewHolder.mDescription.setText(R.string.setup_input_new);
                }
                viewHolder.itemView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().startSetupActivity(input, true);
                    }
                });
            }
        }

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent,
                    false);
            return new MyViewHolder(itemView);
        }
    }

    private static class MyViewHolder extends RecyclerView.ViewHolder {
        final TextView mTitle;
        final TextView mDescription;

        public MyViewHolder(View itemView) {
            super(itemView);
            mTitle = (TextView) itemView.findViewById(R.id.title);
            mDescription = (TextView) itemView.findViewById(R.id.description);
        }
    }

    @VisibleForTesting
    static class TvInputInfoComparator implements Comparator<TvInputInfo> {
        private SetupUtils mSetupUtils;
        private TvInputManagerHelper mInputManager;

        public TvInputInfoComparator(SetupUtils setupUtils, TvInputManagerHelper inputManager) {
            mSetupUtils = setupUtils;
            mInputManager = inputManager;
        }

        @Override
        public int compare(TvInputInfo lhs, TvInputInfo rhs) {
            boolean lhsIsNewInput = mSetupUtils.isNewInput(lhs.getId());
            boolean rhsIsNewInput = mSetupUtils.isNewInput(rhs.getId());
            if (lhsIsNewInput != rhsIsNewInput) {
                return lhsIsNewInput ? -1 : 1;
            }
            return mInputManager.getDefaultTvInputInfoComparator().compare(lhs, rhs);
        }
    };
}

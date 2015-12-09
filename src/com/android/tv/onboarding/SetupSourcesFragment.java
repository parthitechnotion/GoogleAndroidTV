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

package com.android.tv.onboarding;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.SetupPassthroughActivity;
import com.android.tv.TvApplication;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.ui.setup.SetupGuidedStepFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.TvInputNewComparator;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fragment for channel source info/setup.
 */
public class SetupSourcesFragment extends SetupMultiPaneFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        setOnClickAction(view.findViewById(R.id.button_done), ACTION_DONE);
        return view;
    }

    @Override
    protected SetupGuidedStepFragment onCreateContentFragment() {
        SetupGuidedStepFragment fragment = new ContentFragment(getActivity());
        Bundle arguments = new Bundle();
        arguments.putBoolean(SetupGuidedStepFragment.KEY_THREE_PANE, true);
        fragment.setArguments(arguments);
        return fragment;
    }

    private class ContentFragment extends SetupGuidedStepFragment {
        private static final int REQUEST_CODE_START_SETUP_ACTIVITY = 1;

        private static final int ACTION_DIVIDER = ACTION_DONE + 1;
        private static final int ACTION_INPUT_START = ACTION_DONE + 2;

        private final TvInputManagerHelper mInputManager;
        private final ChannelDataManager mChannelDataManager;
        private final SetupUtils mSetupUtils;
        private List<TvInputInfo> mInputList;
        private SetupSourcesAdapter mAdapter;
        private int mKnownInputStartIndex;
        private boolean mShowDivider;

        ContentFragment(Context context) {
            // TODO: Handle USB TV tuner differently.
            ApplicationSingletons app = TvApplication.getSingletons(context);
            mInputManager = app.getTvInputManagerHelper();
            mChannelDataManager = app.getChannelDataManager();
            mSetupUtils = SetupUtils.getInstance(context);
            mInputList = mInputManager.getTvInputInfos(true, true);
            Collections.sort(mInputList, new TvInputNewComparator(mSetupUtils, mInputManager));
            mKnownInputStartIndex = 0;
            for (TvInputInfo input : mInputList) {
                if (mSetupUtils.isNewInput(input.getId())) {
                    mSetupUtils.markAsKnownInput(input.getId());
                    ++mKnownInputStartIndex;
                }
            }
            mShowDivider = mKnownInputStartIndex != 0 && mKnownInputStartIndex != mInputList.size();
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        @SuppressWarnings("rawtypes")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            VerticalGridView gridView = getGuidedActionsStylist().getActionsGridView();
            RecyclerView.Adapter adapter = gridView.getAdapter();
            mAdapter = new SetupSourcesAdapter(adapter);
            gridView.setAdapter(mAdapter);
            return view;
        }

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.setup_sources_text);
            String description = getString(R.string.setup_sources_description);
            return new Guidance(title, description, null, null);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            createActionsInternal(actions);
            if (!mChannelDataManager.isDbLoadFinished()) {
                mChannelDataManager.addListener(new ChannelDataManager.Listener() {
                    @Override
                    public void onLoadFinished() {
                        mChannelDataManager.removeListener(this);
                        updateActions();
                    }

                    @Override
                    public void onChannelListUpdated() { }

                    @Override
                    public void onChannelBrowsableChanged() { }
                });
            }
        }

        private void updateActions() {
            List<GuidedAction> actions = new ArrayList<>();
            createActionsInternal(actions);
            setActions(actions);
            mAdapter.notifyDataSetChanged();
        }

        private void createActionsInternal(List<GuidedAction> actions) {
            for (int i = 0; i < mInputList.size(); ++i) {
                if (mShowDivider && i == mKnownInputStartIndex) {
                    actions.add(new GuidedAction.Builder().id(ACTION_DIVIDER).title(null)
                            .description(null).build());
                }
                TvInputInfo input = mInputList.get(i);
                String description;
                int channelCount = mChannelDataManager.getChannelCountForInput(input.getId());
                if (mSetupUtils.isSetupDone(input.getId())) {
                    if (channelCount == 0) {
                        description = getResources().getString(R.string.setup_input_no_channels);
                    } else {
                        description = getResources().getQuantityString(
                                R.plurals.setup_input_channels, channelCount, channelCount);
                    }
                } else if (i >= mKnownInputStartIndex) {
                    description = getResources().getString(R.string.channel_description_setup_now);
                } else {
                    description = getResources().getString(R.string.setup_input_new);
                }
                actions.add(new GuidedAction.Builder().id(ACTION_INPUT_START + i)
                        .title(input.loadLabel(getActivity()).toString()).description(description)
                        .build());
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            TvInputInfo input = mInputList.get((int) action.getId() - ACTION_INPUT_START);
            Intent intent = TvCommonUtils.createSetupIntent(input);
            if (intent == null) {
                Toast.makeText(getActivity(), R.string.msg_no_setup_activity, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            // Even though other app can handle the intent, the setup launched by Live channels
            // should go through Live channels SetupPassthroughActivity.
            intent.setComponent(new ComponentName(getActivity(), SetupPassthroughActivity.class));
            try {
                // Now we know that the user intends to set up this input. Grant permission for writing
                // EPG data.
                SetupUtils.grantEpgPermission(getActivity(), input.getServiceInfo().packageName);
                startActivityForResult(intent, REQUEST_CODE_START_SETUP_ACTIVITY);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), getString(R.string.msg_unable_to_start_setup_activity,
                        input.loadLabel(getActivity())), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            updateActions();
        }

        @SuppressWarnings("rawtypes")
        private class SetupSourcesAdapter extends RecyclerView.Adapter {
            private static final int VIEW_TYPE_INPUT = 1;
            private static final int VIEW_TYPE_DIVIDER = 2;

            private final RecyclerView.Adapter mGuidedActionAdapter;

            SetupSourcesAdapter(RecyclerView.Adapter adapter) {
                mGuidedActionAdapter = adapter;
            }

            @Override
            public int getItemViewType(int position) {
                if (mShowDivider && position == mKnownInputStartIndex) {
                    return VIEW_TYPE_DIVIDER;
                }
                return VIEW_TYPE_INPUT;
            }

            @Override
            public int getItemCount() {
                if (mInputList == null) {
                    return 0;
                }
                return mInputList.size() + (mShowDivider ? 1 : 0);
            }

            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                if (viewType == VIEW_TYPE_INPUT) {
                    return mGuidedActionAdapter.onCreateViewHolder(parent, viewType);
                }
                View itemView = LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.onboarding_item_divider, parent, false);
                return new MyViewHolder(itemView);
            }

            @SuppressWarnings("unchecked")
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int position) {
                if (mShowDivider && position == mKnownInputStartIndex) {
                    return;
                }
                mGuidedActionAdapter.onBindViewHolder(viewHolder, position);
            }

            @Override
            public void onAttachedToRecyclerView(RecyclerView recyclerView) {
                mGuidedActionAdapter.onAttachedToRecyclerView(recyclerView);
            }

            @Override
            public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
                mGuidedActionAdapter.onDetachedFromRecyclerView(recyclerView);
            }
        }
    }

    private static class MyViewHolder extends RecyclerView.ViewHolder {
        public MyViewHolder(View itemView) {
            super(itemView);
        }
    }
}

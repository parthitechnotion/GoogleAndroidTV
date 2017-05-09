/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dialog.HalfSizedDialogFragment.OnActionClickListener;
import com.android.tv.dialog.SafeDismissDialogFragment;
import com.android.tv.dvr.DvrManager;

import java.util.List;

public class DvrGuidedStepFragment extends GuidedStepFragment {
    /**
     * Action ID for "recording/scheduling the program anyway".
     */
    public static final int ACTION_RECORD_ANYWAY = 1;
    /**
     * Action ID for "deleting existed recordings".
     */
    public static final int ACTION_DELETE_RECORDINGS = 2;
    /**
     * Action ID for "cancelling current recording request".
     */
    public static final int ACTION_CANCEL_RECORDING = 3;

    private DvrManager mDvrManager;
    private OnActionClickListener mOnActionClickListener;

    protected DvrManager getDvrManager() {
        return mDvrManager;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mDvrManager = TvApplication.getSingletons(context).getDvrManager();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        VerticalGridView actionsList = getGuidedActionsStylist().getActionsGridView();
        actionsList.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        VerticalGridView buttonActionsList = getGuidedButtonActionsStylist().getActionsGridView();
        buttonActionsList.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        return view;
    }

    @Override
    public int onProvideTheme() {
        return R.style.Theme_TV_Dvr_GuidedStep;
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (mOnActionClickListener != null) {
            mOnActionClickListener.onActionClick(action.getId());
        }
        dismissDialog();
    }

    protected void dismissDialog() {
        if (getActivity() instanceof MainActivity) {
            SafeDismissDialogFragment currentDialog =
                    ((MainActivity) getActivity()).getOverlayManager().getCurrentDialog();
            if (currentDialog instanceof DvrHalfSizedDialogFragment) {
                currentDialog.dismiss();
            }
        } else if (getParentFragment() instanceof DialogFragment) {
            ((DialogFragment) getParentFragment()).dismiss();
        }
    }

    protected void setOnActionClickListener(OnActionClickListener listener) {
        mOnActionClickListener = listener;
    }

    /**
     * The inner guided step fragment for
     * {@link com.android.tv.dvr.ui.DvrHalfSizedDialogFragment
     * .DvrNoFreeSpaceErrorDialogFragment}.
     */
    public static class DvrNoFreeSpaceErrorFragment
            extends DvrGuidedStepFragment {
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(getString(R.string.dvr_error_no_free_space_title),
                    getString(R.string.dvr_error_no_free_space_description), null, null);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            Activity activity = getActivity();
            actions.add(new GuidedAction.Builder(activity)
                    .id(ACTION_RECORD_ANYWAY)
                    .title(R.string.dvr_action_record_anyway)
                    .build());
            actions.add(new GuidedAction.Builder(activity)
                    .id(ACTION_DELETE_RECORDINGS)
                    .title(R.string.dvr_action_delete_recordings)
                    .build());
            actions.add(new GuidedAction.Builder(activity)
                    .id(ACTION_CANCEL_RECORDING)
                    .title(R.string.dvr_action_record_cancel)
                    .build());
        }
    }
}
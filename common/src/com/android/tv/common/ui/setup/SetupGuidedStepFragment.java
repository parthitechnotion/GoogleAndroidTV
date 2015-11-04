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

package com.android.tv.common.ui.setup;

import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A fragment for channel source info/setup.
 */
public abstract class SetupGuidedStepFragment extends GuidedStepFragment {
    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return new GuidanceStylist() {
            @Override
            public View onCreateView(LayoutInflater inflater, ViewGroup container,
                    Guidance guidance) {
                View view = super.onCreateView(inflater, container, guidance);
                if (guidance.getIconDrawable() == null) {
                    // Icon view should not take up space when we don't use image.
                    getIconView().setVisibility(View.GONE);
                }
                return view;
            }
        };
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        SetupActionHelper.onActionClick(this, (int) action.getId());
    }
}

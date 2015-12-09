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

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.Features;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.ui.setup.SetupGuidedStepFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;

import java.util.List;

/**
 * A fragment for channel source info/setup.
 */
public class AppOverviewFragment extends SetupMultiPaneFragment {
    public static final int ACTION_SETUP_SOURCE = 1;
    public static final int ACTION_GET_MORE_CHANNELS = 2;
    public static final int ACTION_SETUP_USB_TUNER = 3;

    public static final String KEY_AC3_SUPPORT = "key_ac3_support";

    private boolean mAc3Supported;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        Bundle bundle = getArguments();
        mAc3Supported = bundle.getBoolean(KEY_AC3_SUPPORT);
        return view;
    }

    @Override
    protected SetupGuidedStepFragment onCreateContentFragment() {
        return new ContentFragment();
    }

    @Override
    protected boolean needsDoneButton() {
        return false;
    }

    // AppOverviewFragment should inherit OnboardingPageFragment for animation and command execution
    // purpose. So child fragment which inherits GuidedStepFragment is needed.
    private class ContentFragment extends SetupGuidedStepFragment {
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.app_overview_text);
            String description = mAc3Supported
                    ? getString(R.string.app_overview_description_has_ac3)
                            : getString(R.string.app_overview_description_no_ac3);
            return new Guidance(title, description, null, null);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            boolean hasTvInput =
                    TvApplication.getSingletons(getActivity()).getTvInputManagerHelper()
                            .getTunerTvInputSize() > 0;
            Resources res = getResources();
            if (hasTvInput) {
                actions.add(new GuidedAction.Builder()
                        .id(ACTION_SETUP_SOURCE)
                        .title(res.getString(R.string.app_overview_action_text_setup_source))
                        .description(res.getString(
                                R.string.app_overview_action_description_setup_source))
                        .build());
            }
            if (Features.ONBOARDING_PLAY_STORE.isEnabled(getActivity())) {
                actions.add(new GuidedAction.Builder()
                        .id(ACTION_GET_MORE_CHANNELS)
                        .title(res.getString(R.string.app_overview_action_text_play_store))
                        .description(res.getString(
                                R.string.app_overview_action_description_play_store))
                        .build());
            }
            if (Features.ONBOARDING_USB_TUNER.isEnabled(getActivity()) && mAc3Supported) {
                actions.add(new GuidedAction.Builder()
                        .id(ACTION_SETUP_USB_TUNER)
                        .title(res.getString(R.string.app_overview_action_text_usb_tuner))
                        .description(res.getString(
                                R.string.app_overview_action_description_usb_tuner))
                        .build());
            }
        }
    }
}

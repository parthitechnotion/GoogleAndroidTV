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

package com.android.tv.ui.sidepanel;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import com.android.tv.Features;
import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.analytics.OptOutPreferenceHelper;
import com.android.tv.dialog.WebDialogFragment;
import com.android.tv.license.LicenseUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows developer options like enabling USB TV tuner.
 */
public class DeveloperFragment extends SideFragment {
    private static final String TRACKER_LABEL = "developer options";

    /**
     * Sets USB TV tuner enabled.
     */
    private static final class UsbTvTunerItem extends SwitchItem {
        Context mContext;

        public UsbTvTunerItem(Context context) {
            super(context.getResources().getString(R.string.developer_menu_enable_usb_tv_tuner),
                    context.getResources().getString(R.string.developer_menu_enable_usb_tv_tuner),
                    context.getResources().getString(
                            R.string.developer_menu_enable_usb_tv_tuner_description));
            mContext = context;
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);
            setChecked(Features.USB_TUNER.isEnabled(view.getContext()));
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            Features.USB_TUNER.setEnabled(mContext, checked);
        }
    }

    /**
     * Shows AC3 capability of the connected TV.
     */
    private static final class Ac3CapabilityItem extends Item {
        @Override
        protected int getResourceId() {
            return R.layout.option_item_simple;
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            titleView.setText(R.string.developer_menu_ac3_support);
            TextView descriptionView = (TextView) view.findViewById(R.id.description);
            Resources res = view.getContext().getResources();
            boolean ac3Support = ((MainActivity) view.getContext()).isAc3PassthroughSupported();
            descriptionView.setText(ac3Support ? R.string.developer_menu_ac3_support_yes
                    : R.string.developer_menu_ac3_support_no);
        }

        @Override
        protected void onSelected() {
        }
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.side_panel_title_developer);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        Activity activity = getActivity();
        items.add(new UsbTvTunerItem(activity));
        items.add(new Ac3CapabilityItem());
        return items;
    }
}

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

import android.view.View;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dialog.WebDialogFragment;
import com.android.tv.license.LicenseUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows version and optional license information.
 */
public class AboutFragment extends SideFragment {
    private final static String TAG = "AboutFragment";
    private static final String TRACKER_LABEL = "about";

    /**
     * Shows the application version name.
     */
    public static class VersionItem extends Item {
        @Override
        protected int getResourceId() {
            return R.layout.option_item_simple;
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            titleView.setText(R.string.about_menu_version);
            TextView descriptionView = (TextView) view.findViewById(R.id.description);
            descriptionView.setText(TvApplication.getVersionName());
        }

        @Override
        protected void onSelected() {
        }
    }

    /**
     * Opens a dialog showing open source licenses.
     */
    public static class LicenseActionItem extends ActionItem {
        public final static String DIALOG_TAG = LicenseActionItem.class.getSimpleName();
        public static final String TRACKER_LABEL = "Open Source Licenses";
        private final MainActivity mMainActivity;

        public LicenseActionItem(MainActivity mainActivity) {
            super(mainActivity.getString(R.string.about_menu_licenses));
            mMainActivity = mainActivity;
        }

        @Override
        protected void onSelected() {
            WebDialogFragment dialog = WebDialogFragment.newInstance(LicenseUtils.LICENSE_FILE,
                    mMainActivity.getString(R.string.dialog_title_licenses), TRACKER_LABEL);
            mMainActivity.getOverlayManager().showDialogFragment(DIALOG_TAG, dialog, false);
        }
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.side_panel_title_about);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        items.add(new VersionItem());
        if (LicenseUtils.hasLicenses(getActivity().getAssets())) {
            items.add(new LicenseActionItem((MainActivity) getActivity()));
        }
        return items;
    }
}

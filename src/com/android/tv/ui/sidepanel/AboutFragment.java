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
import android.os.Build;
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
 * Shows version, optional license information and Analytics OptOut.
 */
public class AboutFragment extends SideFragment {
    private static final String TRACKER_LABEL = "about";

    /**
     * Shows the application version name.
     */
    private static final class VersionItem extends Item {
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
    public static final class LicenseActionItem extends ActionItem {
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

    /**
     * Sets the users preference for allowing analytics.
     */
    private static final class AllowAnalyticsItem extends SwitchItem {
        //TODO: change this to use SwitchPreference
        private final OptOutPreferenceHelper mPreferenceHelper;
        private TextView mDescriptionView;
        private int mOriginalMaxDescriptionLine;
        private MainActivity mMainActivity;
        private View mBoundView;

        public AllowAnalyticsItem(Context context) {
            super(context.getResources().getString(R.string.about_menu_improve),
                    context.getResources().getString(R.string.about_menu_improve),
                    context.getResources().getString(R.string.about_menu_improve_summary));
            mPreferenceHelper = TvApplication.getSingletons(context).getOptPreferenceHelper();
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);
            mDescriptionView = (TextView) view.findViewById(getDescriptionViewId());
            mOriginalMaxDescriptionLine = mDescriptionView.getMaxLines();
            mDescriptionView.setMaxLines(Integer.MAX_VALUE);
            mMainActivity = (MainActivity) view.getContext();
            mBoundView = view;
        }

        @Override
        protected void onUnbind() {
            super.onUnbind();
            mDescriptionView.setMaxLines(mOriginalMaxDescriptionLine);
            mDescriptionView = null;
            mMainActivity = null;
            mBoundView = null;
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            setChecked(!mPreferenceHelper
                    .getOptOutPreference(OptOutPreferenceHelper.ANALYTICS_OPT_OUT_DEFAULT_VALUE));
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            mPreferenceHelper.setOptOutPreference(!isChecked());
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            if (mMainActivity != null && mBoundView != null && mBoundView.hasFocus()) {
                // Quick fix for accessibility
                // TODO: Need to change the resource in the future.
                mMainActivity.sendAccessibilityText(
                        checked ? mMainActivity.getString(R.string.options_item_pip_on)
                                : mMainActivity.getString(R.string.options_item_pip_off));
            }
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
        Activity activity = getActivity();
        if (LicenseUtils.hasLicenses(activity.getAssets())) {
            items.add(new LicenseActionItem((MainActivity) activity));
        }
        if (Features.ANALYTICS_OPT_OUT.isEnabled(activity)) {
            items.add(new AllowAnalyticsItem(activity));
        }
        boolean developerOptionEnabled = Settings.Secure.getInt(getActivity().getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED , 0) != 0;
        if (Features.DEVELOPER_OPTION.isEnabled(getActivity()) && developerOptionEnabled
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Resources res = getActivity().getResources();
            items.add(new ActionItem(res.getString(R.string.side_panel_title_developer)) {
                @Override
                protected void onSelected() {
                    getMainActivity().getOverlayManager().getSideFragmentManager().show(
                            new DeveloperFragment());
                }
            });
        }
        return items;
    }
}

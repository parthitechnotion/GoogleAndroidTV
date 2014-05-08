/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

import com.android.tv.InputPickerDialogFragment;
import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.TvInputUtils;

import java.util.List;

/**
 * The TV app's menu dialog.
 */
public final class MenuDialogFragment extends DialogFragment {
    public static final String DIALOG_TAG = MenuDialogFragment.class.getName();
    public static final boolean PIP_MENU_ENABLED = true;

    public static final String ARG_CURRENT_SERVICE_NAME = "current_service_name";
    public static final String ARG_CURRENT_PACKAGE_NAME = "current_package_name";

    private static final int POSITION_SELECT_INPUT  = 0;
    private static final int POSITION_EDIT_CHANNELS = 1;
    private static final int POSITION_SETUP         = 2;
    private static final int POSITION_PRIVACY       = 3;
    private static final int POSITION_PIP           = 4;
    private static final int POSITION_SETTINGS      = 5;

    private String mCurrentPackageName;
    private String mCurrentServiceName;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = getArguments();
        if (arg != null) {
            mCurrentPackageName = arg.getString(ARG_CURRENT_PACKAGE_NAME);
            mCurrentServiceName = arg.getString(ARG_CURRENT_SERVICE_NAME);
        }

        String[] items = {
                getString(R.string.menu_select_input),
                getString(R.string.menu_edit_channels),
                getString(R.string.menu_auto_scan),
                getString(R.string.menu_privacy_setting),
                getString(R.string.menu_toggle_pip),
                getString(R.string.source_specific_setting)
        };

        ListAdapter adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, items) {
                    @Override
                    public boolean areAllItemsEnabled() {
                        return false;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        view.setEnabled(isEnabled(position));
                        return view;
                    }

                    @Override
                    public boolean isEnabled(int position) {
                        switch (position) {
                            case POSITION_EDIT_CHANNELS:
                                return mCurrentServiceName != null;
                            case POSITION_SETUP:
                                return getSetupActivityInfo() != null;
                            case POSITION_PIP:
                                return PIP_MENU_ENABLED;
                            case POSITION_SETTINGS:
                                return getSettingsActivityInfo() != null;
                        }
                        return true;
                    }
        };

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_title)
                .setAdapter(adapter, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case POSITION_SELECT_INPUT:
                                showDialogFragment(InputPickerDialogFragment.DIALOG_TAG,
                                        new InputPickerDialogFragment());
                                break;
                            case POSITION_EDIT_CHANNELS:
                                EditChannelsDialogFragment f = new EditChannelsDialogFragment();
                                Bundle arg = new Bundle();
                                arg.putString(EditChannelsDialogFragment.ARG_CURRENT_PACKAGE_NAME,
                                        mCurrentPackageName);
                                arg.putString(EditChannelsDialogFragment.ARG_CURRENT_SERVICE_NAME,
                                        mCurrentServiceName);
                                f.setArguments(arg);

                                showDialogFragment(EditChannelsDialogFragment.DIALOG_TAG, f);
                                break;
                            case POSITION_SETUP:
                                startSetupActivity();
                                break;
                            case POSITION_PRIVACY:
                                showDialogFragment(PrivacySettingDialogFragment.DIALOG_TAG,
                                        new PrivacySettingDialogFragment());
                                break;
                            case POSITION_PIP:
                                TvActivity activity = (TvActivity) getActivity();
                                activity.togglePipView();
                                break;
                            case POSITION_SETTINGS:
                                startSettingsActivity();
                                break;
                        }
                    }
                })
                .create();
    }

    private void showDialogFragment(String tag, DialogFragment dialog) {
        dismiss();

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(tag);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        dialog.show(ft, tag);
    }

    private void startSetupActivity() {
        startActivity(TvInputUtils.ACTION_SETUP);
    }

    private void startSettingsActivity() {
        startActivity(TvInputUtils.ACTION_SETTINGS);
    }

    private void startActivity(String action) {
        ActivityInfo info = getActivityInfo(action);
        if (info == null) {
            return;
        }

        Intent intent = new Intent(action);
        intent.setClassName(info.packageName, info.name);
        intent.putExtra(TvInputUtils.EXTRA_SERVICE_NAME, mCurrentServiceName);
        startActivity(intent);
    }

    private ActivityInfo getSetupActivityInfo() {
        return getActivityInfo(TvInputUtils.ACTION_SETUP);
    }

    private ActivityInfo getSettingsActivityInfo() {
        return getActivityInfo(TvInputUtils.ACTION_SETTINGS);
    }

    private ActivityInfo getActivityInfo(String action) {
        if (mCurrentPackageName == null) {
            return null;
        }

        List<ResolveInfo> infos = getActivity().getPackageManager().queryIntentActivities(
                new Intent(action), PackageManager.GET_ACTIVITIES);
        if (infos == null) {
            return null;
        }

        for (ResolveInfo info : infos) {
            if (info.activityInfo.packageName.equals(mCurrentPackageName)) {
                return info.activityInfo;
            }
        }
        return null;
    }
}

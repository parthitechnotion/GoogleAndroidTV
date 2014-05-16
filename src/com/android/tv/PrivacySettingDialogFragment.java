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

package com.android.tv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.android.tv.R;

/**
 * Privacy setting dialog.
 */
public final class PrivacySettingDialogFragment extends DialogFragment {
    public static final String DIALOG_TAG = PrivacySettingDialogFragment.class.getName();

    private static final int POSITION_WATCH_LOGGING = 0;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] items = {
                getString(R.string.privacy_watch_logging)
        };
        boolean[] checked = {
                isTvWatchLoggingEnabled()
        };

        return new AlertDialog.Builder(getActivity())
                .setTitle(getActivity().getString(R.string.privacy_dialog_title))
                .setMultiChoiceItems(items, checked, new OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        switch (which) {
                            case POSITION_WATCH_LOGGING:
                                setTvWatchLogging(isChecked);
                                break;
                        }
                    }
                }).create();
    }

    private boolean isTvWatchLoggingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(TvSettings.PREF_TV_WATCH_LOGGING_ENABLED, false);
    }

    private void setTvWatchLogging(boolean enable) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(TvSettings.PREF_TV_WATCH_LOGGING_ENABLED, enable)
                .commit();
    }
}

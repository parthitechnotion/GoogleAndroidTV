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

package com.android.tv.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.R;

public class BaseOptionFragment extends BaseSideFragment {
    private static final String TAG = "ClosedCaptionOptionFragment";
    private static final boolean DEBUG = true;

    private View mMainView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mMainView = super.onCreateView(inflater, container, savedInstanceState);
        return mMainView;
    }

    @Override
    public void onResume() {
        super.onResume();
        setSelectedPosition(0);
    }

    @Override
    public final void initialize(String title, Object[] itemTags, int fragmentLayoutId,
            int itemLayoutId) {
        throw new UnsupportedOperationException("Call initialize(String title, Object[] itemTags)");
    }

    public void initialize(String title, Object[] itemTags) {
        super.initialize(title, itemTags, R.layout.option_fragment, R.layout.option_item);
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        RadioButton radioButton = (RadioButton) v.findViewById(R.id.option_item);
        uncheckAllRadioButtons((ViewGroup) mMainView);
        radioButton.setChecked(true);
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        RadioButton radioButton = (RadioButton) v.findViewById(R.id.option_item);
        if (prevSelected) {
            radioButton.setChecked(true);
        } else {
            radioButton.setChecked(false);
        }
        radioButton.setText((String) tag);
    }

    private static void uncheckAllRadioButtons(ViewGroup parent) {
        int count = parent.getChildCount();
        for (int i = 0; i < count; ++i) {
            View v = parent.getChildAt(i);
            if (v instanceof ViewGroup) {
                uncheckAllRadioButtons((ViewGroup) v);
            } else if (v instanceof RadioButton) {
                ((RadioButton) v).setChecked(false);
            }
        }
    }
}

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

package com.android.tv.ui.sidepanel;

import android.view.View;
import android.widget.RadioButton;

import com.android.tv.R;

public class RadioButtonItem extends Item {
    private final String mTitle;
    private boolean mChecked;
    private RadioButton mRadioButton;

    public RadioButtonItem(String title) {
        mTitle = title;
    }

    @Override
    protected int getResourceId() {
        return R.layout.option_item_radio_button;
    }

    @Override
    protected void bind(View view) {
        mRadioButton = (RadioButton) view.findViewById(R.id.radio_button);
        mRadioButton.setText(mTitle);
        mRadioButton.setChecked(mChecked);
    }

    @Override
    protected void unbind() {
        mRadioButton = null;
    }

    @Override
    protected void onSelected() {
        setChecked(true);
    }

    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            if (mRadioButton != null) {
                mRadioButton.setChecked(mChecked);
            }
        }
    }

    public boolean getChecked() {
        return mChecked;
    }
}
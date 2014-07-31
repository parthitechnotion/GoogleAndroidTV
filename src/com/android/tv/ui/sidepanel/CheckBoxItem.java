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
import android.widget.CheckBox;

import com.android.tv.R;

public class CheckBoxItem extends Item {
    private final String mTitle;
    private boolean mChecked;
    private CheckBox mCheckBox;

    public CheckBoxItem(String title) {
        mTitle = title;
    }

    @Override
    protected int getResourceId() {
        return R.layout.option_item_check_box;
    }

    @Override
    protected void bind(View view) {
        mCheckBox = (CheckBox) view.findViewById(R.id.check_box);
        mCheckBox.setText(mTitle);
        mCheckBox.setChecked(mChecked);
    }

    @Override
    protected void unbind() {
        mCheckBox = null;
    }

    @Override
    protected void onSelected() {
        setChecked(!mChecked);
    }

    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            if (mCheckBox != null) {
                mCheckBox.setChecked(mChecked);
            }
        }
    }

    public boolean getChecked() {
        return mChecked;
    }
}
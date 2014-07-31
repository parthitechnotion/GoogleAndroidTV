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

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioButton;

import com.android.tv.R;
import com.android.tv.util.TvSettings;

import java.util.ArrayList;
import java.util.List;

public class PipLocationFragment extends SideFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.pip_location_option_title);
    }

    @Override
    protected List<Item> getItemList() {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new PipLocationRadio(
                TvSettings.PIP_LOCATION_TOP_LEFT, R.drawable.ic_pip_loc_top_left));
        items.add(new PipLocationRadio(
                TvSettings.PIP_LOCATION_TOP_RIGHT, R.drawable.ic_pip_loc_top_right));
        items.add(new PipLocationRadio(
                TvSettings.PIP_LOCATION_BOTTOM_LEFT, R.drawable.ic_pip_loc_bottom_left));
        items.add(new PipLocationRadio(
                TvSettings.PIP_LOCATION_BOTTOM_RIGHT, R.drawable.ic_pip_loc_bottom_right));
        return items;
    }

    private class PipLocationRadio extends RadioButtonItem {
        int mLocation;
        int mDrawable;

        private PipLocationRadio(int location, int drawable) {
            super(null);
            mLocation = location;
            mDrawable = drawable;
        }

        @Override
        protected void bind(View view) {
            super.bind(view);
            RadioButton radioButton = (RadioButton) view.findViewById(R.id.radio_button);
            BitmapDrawable drawable = (BitmapDrawable) getResources().getDrawable(mDrawable);
            drawable.setGravity(Gravity.CENTER);
            drawable.setTargetDensity(Bitmap.DENSITY_NONE);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            radioButton.setCompoundDrawablesRelative(drawable, null, null, null);
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            getTvActivity().setPipLocation(mLocation, true);
        }
    }
}
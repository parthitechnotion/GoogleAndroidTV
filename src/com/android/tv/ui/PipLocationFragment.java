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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.util.TvSettings;

public class PipLocationFragment extends BaseOptionFragment {
    private static final String TAG = "PipLocationFragment";
    private static final boolean DEBUG = true;

    private TvActivity mTvActivity;
    private boolean mIsFirstResume;
    private int mPipLocation;
    private final int[] mLocationToItemPosition = new int[4];
    private final Object[] mItem  = new Integer[] {
        TvSettings.PIP_LOCATION_BOTTOM_RIGHT,
        TvSettings.PIP_LOCATION_TOP_RIGHT,
        TvSettings.PIP_LOCATION_TOP_LEFT,
        TvSettings.PIP_LOCATION_BOTTOM_LEFT,
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mIsFirstResume = true;
        mTvActivity = (TvActivity) getActivity();

        mLocationToItemPosition[TvSettings.PIP_LOCATION_BOTTOM_RIGHT] = 0;
        mLocationToItemPosition[TvSettings.PIP_LOCATION_TOP_RIGHT] = 1;
        mLocationToItemPosition[TvSettings.PIP_LOCATION_TOP_LEFT] = 2;
        mLocationToItemPosition[TvSettings.PIP_LOCATION_BOTTOM_LEFT] = 3;

        initialize(getString(R.string.closed_caption_option_title),
                R.layout.pip_location_item, mItem);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsFirstResume) {
            mPipLocation = mTvActivity.getPipLocation();
            int initialPosition = mLocationToItemPosition[mPipLocation];
            setSelectedPosition(initialPosition);
            setPrevSelectedItemPosition(initialPosition);
            mIsFirstResume = false;
        }
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        super.onBindView(v, position, tag, prevSelected);
        ImageView pipLocationImageView = (ImageView) v.findViewById(R.id.pip_location);
        int location = (Integer) tag;
        if (location == TvSettings.PIP_LOCATION_TOP_LEFT) {
            pipLocationImageView.setImageResource(R.drawable.ic_pip_loc_top_left);
        } else if (location == TvSettings.PIP_LOCATION_TOP_RIGHT) {
            pipLocationImageView.setImageResource(R.drawable.ic_pip_loc_top_right);
        } else if (location == TvSettings.PIP_LOCATION_BOTTOM_LEFT) {
            pipLocationImageView.setImageResource(R.drawable.ic_pip_loc_bottom_left);
        } else if (location == TvSettings.PIP_LOCATION_BOTTOM_RIGHT) {
            pipLocationImageView.setImageResource(R.drawable.ic_pip_loc_bottom_right);
        } else {
            throw new IllegalArgumentException("Invaild PIP location: " + location);
        }
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        int pipLocation = (Integer) tag;
        mTvActivity.setPipLocation(pipLocation, true);
        super.onItemSelected(v, position, tag);
    }
}

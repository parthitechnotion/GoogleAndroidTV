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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.DisplayMode;

public class DisplayModeOptionFragment extends BaseOptionFragment {
    private static final String TAG = "AspectRatioOptionFragment";
    private static final boolean DEBUG = true;

    private TvActivity mTvActivity;
    private boolean mIsFirstResume;
    private int mLastStoredAspectRatio;
    private int mAspectRatio;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mIsFirstResume = true;
        mTvActivity = (TvActivity) getActivity();

        Object[] items = new Object[DisplayMode.SIZE_OF_RATIO_TYPES];
        for (int i = 0; i < DisplayMode.SIZE_OF_RATIO_TYPES; ++i) {
            items[i] = DisplayMode.getLabel(i, getActivity());
        }
        initialize(getString(R.string.display_mode_option_title), items);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsFirstResume) {
            mAspectRatio = mTvActivity.getDisplayMode();
            mLastStoredAspectRatio = mAspectRatio;
            int initialPosition = mAspectRatio;
            setSelectedPosition(initialPosition);
            setPrevSelectedItemPosition(initialPosition);
            mIsFirstResume = false;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mLastStoredAspectRatio != mAspectRatio) {
            mTvActivity.restoreDisplayMode();
        }
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        super.onItemFocusChanged(v, focusGained, position, tag);
        if (focusGained) {
            mAspectRatio = position;
            mTvActivity.setDisplayMode(position, false);
        }
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        mAspectRatio = position;
        mTvActivity.setDisplayMode(mAspectRatio, true);
        mLastStoredAspectRatio = mAspectRatio;
        super.onItemSelected(v, position, tag);
    }
}

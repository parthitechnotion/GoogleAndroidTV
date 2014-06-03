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

public class ClosedCaptionOptionFragment extends BaseOptionFragment {
    private static final String TAG = "ClosedCaptionOptionFragment";
    private static final boolean DEBUG = true;

    private static final int CC_ON = 0;
    private static final int CC_OFF = 1;

    private TvActivity mTvActivity;
    private boolean mIsFirstResume;
    private boolean mLastStoredCcEnabled;
    private boolean mCcEnabled;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mIsFirstResume = true;
        mTvActivity = (TvActivity) getActivity();

        Object[] items = new Object[2];
        items[0] = getString(R.string.option_item_on);
        items[1] = getString(R.string.option_item_off);

        initialize(getString(R.string.closed_caption_option_title), items);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsFirstResume) {
            mCcEnabled = mTvActivity.isClosedCaptionEnabled();
            mLastStoredCcEnabled = mCcEnabled;
            int initialPosition = mCcEnabled ? CC_ON : CC_OFF;
            setSelectedPosition(initialPosition);
            setPrevSelectedItemPosition(initialPosition);
            mIsFirstResume = false;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mLastStoredCcEnabled != mCcEnabled) {
            mTvActivity.restoreClosedCaptionEnabled();
        }
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        super.onItemFocusChanged(v, focusGained, position, tag);
        if (focusGained) {
            mCcEnabled = (position == CC_ON);
            mTvActivity.setClosedCaptionEnabled(mCcEnabled, false);
        }
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        mCcEnabled = (position == CC_ON);
        mTvActivity.setClosedCaptionEnabled(mCcEnabled, true);
        mLastStoredCcEnabled = mCcEnabled;
        super.onItemSelected(v, position, tag);
        Toast.makeText(getActivity(), R.string.not_implemented_yet, Toast.LENGTH_SHORT).show();
    }
}

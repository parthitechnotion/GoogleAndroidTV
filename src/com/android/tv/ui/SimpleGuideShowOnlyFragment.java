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

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Rect;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.data.AspectRatio;
import com.android.tv.data.Channel;
import com.android.tv.data.ShowOnlyItems;
import com.android.tv.data.Program;
import com.android.tv.util.Utils;

public class SimpleGuideShowOnlyFragment extends BaseSideFragment {
    private static final String TAG = "SimpleGuideShowOnlyFragment";
    private static final boolean DEBUG = true;

    private final TvActivity mTvActivity;
    private View mMainView;
    private int mFocusedItemPosition;
    private int mSelectedItemPosition;
    private int mFocusedBgColor;
    private int mBgColor;

    public SimpleGuideShowOnlyFragment(TvActivity tvActivity) {
        super();
        mTvActivity = tvActivity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mBgColor = getActivity().getResources().getColor(R.color.simple_guide_fragment_background);
        mFocusedBgColor = getActivity().getResources().getColor(
                R.color.simple_guide_fragment_focused_background);

        Object[] items = new Object[ShowOnlyItems.SHOW_ONLY_ITEM_SIZE];
        for (int i = 0; i < ShowOnlyItems.SHOW_ONLY_ITEM_SIZE; ++i) {
            items[i] = ShowOnlyItems.getLabel(i, getActivity());
        }
        initialize(getString(R.string.show_only_title), items,
                R.layout.simple_guide_fragment, R.layout.show_only_item);
        // TODO: set the current position correctly.
        mFocusedItemPosition = 0;
        mMainView = super.onCreateView(inflater, container, savedInstanceState);
        return mMainView;
    }

    @Override
    public void onResume() {
        super.onResume();
        setSelectedPosition(mFocusedItemPosition);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemFocusChanged " + focusGained + ": position=" + position
                + ", label=" + (String) tag);
        v.setBackgroundColor(focusGained ? mFocusedBgColor : mBgColor);
        mFocusedItemPosition = position;
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemSelected: position=" + position + ", label=" + (String) tag);
        // TODO: enable all items.
        if (position != ShowOnlyItems.POSITION_ALL_CHANNELS) {
            Toast.makeText(getActivity(), R.string.not_implemented_yet, Toast.LENGTH_SHORT).show();
            getFragmentManager().popBackStack();
            return;
        }

        mSelectedItemPosition = position;
        mFocusedItemPosition = position;

        RadioButton radioButton = (RadioButton) v.findViewById(R.id.show_only_item);
        uncheckAllRadioButtons((ViewGroup) mMainView);
        radioButton.setChecked(true);

        getFragmentManager().popBackStack();
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        if (DEBUG) Log.d(TAG, "onBindView: position=" + position + ", label=" + (String) tag);
        RadioButton radioButton = (RadioButton) v.findViewById(R.id.show_only_item);
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

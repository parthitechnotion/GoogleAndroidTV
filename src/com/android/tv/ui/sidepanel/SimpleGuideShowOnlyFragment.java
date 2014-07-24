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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import com.android.tv.R;
import com.android.tv.data.GenreItems;

public class SimpleGuideShowOnlyFragment extends BaseSideFragment {
    private static final String TAG = "SimpleGuideShowOnlyFragment";
    private static final boolean DEBUG = true;

    private SimpleGuideFragment mSimpleGuideFragment;

    private View mMainView;
    private int mFocusedItemPosition;

    public SimpleGuideShowOnlyFragment(SimpleGuideFragment simpleGuideFragment,
            String selectedGenre) {
        super();
        mSimpleGuideFragment = simpleGuideFragment;
        mFocusedItemPosition = GenreItems.getPosition(selectedGenre);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Object[] items = GenreItems.getItems(getActivity());
        initialize(getString(R.string.show_only_title), items,
                R.layout.option_fragment, R.layout.show_only_item,
                R.color.option_item_background, R.color.option_item_focused_background,
                R.dimen.simple_guide_item_height);
        setPrevSelectedItemPosition(mFocusedItemPosition);
        mMainView = super.onCreateView(inflater, container, savedInstanceState);
        // This fragment is always added on top of SimpleGuideFragment. So we need to make
        // this fragment shadow invisible.
        mMainView.findViewById(R.id.side_panel_shadow).setVisibility(View.INVISIBLE);
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
        super.onItemFocusChanged(v, focusGained, position, tag);
        mFocusedItemPosition = position;
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemSelected: position=" + position + ", label=" + (String) tag);

        mFocusedItemPosition = position;

        RadioButton radioButton = (RadioButton) v.findViewById(R.id.show_only_item);
        uncheckAllRadioButtons((ViewGroup) mMainView);
        radioButton.setChecked(true);

        mSimpleGuideFragment.setGenreOnGuide(GenreItems.getCanonicalGenre(getActivity(),
                (String) tag));
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

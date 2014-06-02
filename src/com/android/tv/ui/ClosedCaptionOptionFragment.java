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
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.R;

public class ClosedCaptionOptionFragment extends BaseSideFragment {
    private static final String TAG = "ClosedCaptionOptionFragment";
    private static final boolean DEBUG = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Object[] items = new Object[2];
        items[0] = getString(R.string.option_item_on);
        items[1] = getString(R.string.option_item_off);

        initialize(getString(R.string.closed_caption_option_title), items, R.layout.option_item);
        View fragView = super.onCreateView(inflater, container, savedInstanceState);
        // TODO: implement to get the current closed caption.
        setPrevSelectedItem(0);
        return fragView;
    }

    @Override
    public void onResume() {
        super.onResume();
        setSelectedPosition(0);
    }

    @Override
    public void onItemFocusChanged(View v, boolean focusGained, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemFocusChanged " + focusGained + ": position=" + position
                + ", label=" + (String) tag);
        // TODO: temporally change aspect ratio to test the focused ratio.
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        if (DEBUG) Log.d(TAG, "onItemSelected: position=" + position + ", label=" + (String) tag);
        // TODO: change aspect ratio.
        Toast.makeText(getActivity(), R.string.not_implemented_yet, Toast.LENGTH_SHORT).show();
        getFragmentManager().popBackStack();
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        TextView textView = (TextView) v.findViewById(R.id.option_item);
        String text = (String) tag;
        // TODO: Once we get the assets of Radio buttons, the selected item should be
        // distinguished by icons.
        if (prevSelected) {
            text += " (Selected)";
        }
        textView.setText(text);
    }
}

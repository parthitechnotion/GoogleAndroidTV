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

import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import com.android.internal.util.Preconditions;
import com.android.tv.R;
import com.android.tv.TvActivity;
import com.android.tv.input.TisTvInput;
import com.android.tv.input.TvInput;
import com.android.tv.input.UnifiedTvInput;
import com.android.tv.util.TvInputManagerHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

public class InputPickerFragment extends BaseOptionFragment {
    private TvInput mSelectedInput;

    private TvActivity mTvActivity;
    private boolean mIsFirstResume;
    private int mInitialPosition;

    private TvInputManagerHelper mInputManager;

    private final TvInputManager.TvInputListener mAvailabilityListener =
            new TvInputManager.TvInputListener() {
                @Override
                public void onAvailabilityChanged(String inputId, boolean isAvailable) {
                    mAdapter.notifyDataSetChanged();
                }
            };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mIsFirstResume = true;
        mTvActivity = (TvActivity) getActivity();
        mInputManager = mTvActivity.getTvInputManagerHelper();
        mInputManager.update();
        Collection<TvInputInfo> inputInfos = mInputManager.getTvInputInfos(false);
        int inputSize = inputInfos.size();
        Preconditions.checkState(inputSize > 0);
        mInputManager.addListener(mAvailabilityListener);
        mSelectedInput = mTvActivity.getSelectedTvInput();

        Object[] items = new Object[inputSize + 1];
        // Unified TV input is always the first item.
        items[0] = new UnifiedTvInput(mInputManager, getActivity());
        int i = 1;
        for (TvInputInfo inputInfo : inputInfos) {
            items[i++] = new TisTvInput(mInputManager, inputInfo, mTvActivity);
        }
        Arrays.sort(items, 1, items.length, new Comparator<Object>() {
            @Override
            public int compare(Object lhs, Object rhs) {
                return ((TvInput) lhs).getDisplayName().compareTo(((TvInput) rhs).getDisplayName());
            }
        });

        mInitialPosition = 0;
        for (i = 0; i < items.length; ++i) {
            if (items[i].equals(mSelectedInput)) {
                mInitialPosition = i;
                break;
            }
        }
        initialize(getString(R.string.select_input_device), items);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsFirstResume) {
            setSelectedPosition(mInitialPosition);
            setPrevSelectedItemPosition(mInitialPosition);
            mIsFirstResume = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mInputManager.removeListener(mAvailabilityListener);
    }

    @Override
    public void onBindView(View v, int position, Object tag, boolean prevSelected) {
        super.onBindView(v, position, tag, prevSelected);
        TvInput input = (TvInput) tag;
        boolean available = input.isAvailable();
        v.setEnabled(available);
        v.setClickable(available);

        RadioButton radioButton = (RadioButton) v.findViewById(R.id.option_item);
        radioButton.setEnabled(available);
        radioButton.setText(input.getDisplayName());
    }

    @Override
    public void onItemSelected(View v, int position, Object tag) {
        if (!((TvInput) tag).equals(mSelectedInput)) {
            mTvActivity.onInputPicked((TvInput) tag);
        }
        super.onItemSelected(v, position, tag);
    }
}

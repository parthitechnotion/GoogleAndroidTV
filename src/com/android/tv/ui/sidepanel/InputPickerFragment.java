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

import android.media.tv.TvInputInfo;

import com.android.tv.R;
import com.android.tv.input.TisTvInput;
import com.android.tv.input.TvInput;
import com.android.tv.input.UnifiedTvInput;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InputPickerFragment extends SideFragment {
    @Override
    protected String getTitle() {
        return getString(R.string.select_input_device);
    }

    @Override
    protected List<Item> getItemList() {
        ArrayList<Item> items = new ArrayList<>();

        items.add(new TvInputItem(
                new UnifiedTvInput(getTvActivity().getTvInputManagerHelper(), getActivity())));

        getTvActivity().getTvInputManagerHelper().update();
        List<TvInputInfo> infos = new ArrayList<>(
                getTvActivity().getTvInputManagerHelper().getTvInputInfos(false));
        Collections.sort(infos, new Comparator<TvInputInfo>() {
            @Override
            public int compare(TvInputInfo lhs, TvInputInfo rhs) {
                String a = Utils.getDisplayNameForInput(getActivity(), lhs);
                String b = Utils.getDisplayNameForInput(getActivity(), rhs);
                return a.compareTo(b);
            }
        });
        for (TvInputInfo inputInfo : infos) {
            if (inputInfo.getType() == TvInputInfo.TYPE_TUNER) {
                items.add(new TvInputItem(new TisTvInput(
                        getTvActivity().getTvInputManagerHelper(), inputInfo, getActivity())));
            }
        }

        TvInput selected = getTvActivity().getSelectedTvInput();
        if (selected == null) {
            ((TvInputItem) items.get(0)).setChecked(true);
        } else {
            for (Item item : items) {
                if (((TvInputItem) item).getTvInput().equals(selected)) {
                    ((TvInputItem) item).setChecked(true);
                    break;
                }
            }
        }

        return items;
    }

    private class TvInputItem extends RadioButtonItem {
        private TvInput mTvInput;

        private TvInputItem(TvInput tvInput) {
            super(tvInput.getDisplayName());
            mTvInput = tvInput;
        }

        public TvInput getTvInput() {
            return mTvInput;
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            getTvActivity().onInputPicked(mTvInput);
        }
    }
}
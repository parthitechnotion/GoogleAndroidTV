/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.menu;

import android.content.Context;

import com.android.tv.R;
import com.android.tv.TimeShiftManager;
import com.android.tv.common.TvCommonConstants;

public class PlayControlsRow extends MenuRow {
    public static final String ID = PlayControlsRow.class.getName();

    public PlayControlsRow(Context context) {
        super(context, R.string.menu_title_play_controls, R.dimen.play_controls_height);
    }

    @Override
    public void update() {
    }

    @Override
    public int getLayoutResId() {
        return R.layout.play_controls;
    }

    public TimeShiftManager getTimeShiftManager() {
        return getMainActivity().getTimeShiftManager();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isVisible() {
        return TvCommonConstants.HAS_TIME_SHIFT_API;
    }
}

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

package com.android.tv.data;

import android.media.tv.TvInputInfo;

import com.android.tv.util.SetupUtils;
import com.android.tv.util.TvInputManagerHelper;

import java.util.Comparator;

/**
 * Compares TV input such that the new input comes first.
 */
public class TvInputNewComparator implements Comparator<TvInputInfo> {
    private final SetupUtils mSetupUtils;
    private final TvInputManagerHelper mInputManager;

    public TvInputNewComparator(SetupUtils setupUtils, TvInputManagerHelper inputManager) {
        mSetupUtils = setupUtils;
        mInputManager = inputManager;
    }

    @Override
    public int compare(TvInputInfo lhs, TvInputInfo rhs) {
        boolean lhsIsNewInput = mSetupUtils.isNewInput(lhs.getId());
        boolean rhsIsNewInput = mSetupUtils.isNewInput(rhs.getId());
        if (lhsIsNewInput != rhsIsNewInput) {
            return lhsIsNewInput ? -1 : 1;
        }
        return mInputManager.getDefaultTvInputInfoComparator().compare(lhs, rhs);
    }
}

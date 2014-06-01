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

package com.android.tv.data;

import android.content.Context;

import com.android.tv.R;

public class AspectRatio {

    // The values should be synced with R.arrays.aspect_ratio_label
    public static final int RATIO_16_9 = 0;
    public static final int RATIO_4_3 = 1;
    public static final int RATIO_FULL = 2;
    public static final int RATIO_ZOOM = 3;
    public static final int RATIO_SET_BY_PROGRAM = 4;
    public static final int SIZE_OF_RATIO_TYPES = RATIO_SET_BY_PROGRAM + 1;

    private AspectRatio() { }

    public static final String getLabel(int ratio, Context context) {
        return context.getResources().getStringArray(R.array.aspect_ratio_label)[ratio];
    }
}

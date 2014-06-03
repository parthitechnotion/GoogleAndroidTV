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

public class ShowOnlyItems {

    // The values should be synced with R.arrays.show_only_label
    public static final int POSITION_ALL_CHANNELS = 0;
    public static final int POSITION_FAVORITES = 1;
    public static final int POSITION_SPORTS = 2;
    public static final int POSITION_KIDS = 3;
    public static final int POSITION_MOVIEWS = 4;
    public static final int POSITION_REALITY_AND_GAMES = 5;
    public static final int POSITION_NEWS_AND_TALKS = 6;
    public static final int SHOW_ONLY_ITEM_SIZE = POSITION_NEWS_AND_TALKS + 1;

    private ShowOnlyItems() { }

    public static final String getLabel(int item, Context context) {
        return context.getResources().getStringArray(R.array.show_only_label)[item];
    }
}

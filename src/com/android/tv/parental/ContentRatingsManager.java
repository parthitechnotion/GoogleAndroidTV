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

package com.android.tv.parental;

import android.content.Context;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class ContentRatingsManager {
    private final List<ContentRatingSystem> mContenRatings = new ArrayList<ContentRatingSystem>();

    private Context mContext;

    public ContentRatingsManager(Context context) {
        mContext = context;
    }

    public void update() {
        mContenRatings.clear();

        TvInputManager manager =
                (TvInputManager) mContext.getSystemService(Context.TV_INPUT_SERVICE);
        List<Uri> uris = manager.getTvContentRatingSystemXmls();
        for (Uri uri : uris) {
            List<ContentRatingSystem> list = ContentRatingsParser.parse(mContext, uri);
            if (list != null) {
                mContenRatings.addAll(list);
            }
        }
    }

    public List<ContentRatingSystem> getContentRatingSystems() {
        return mContenRatings;
    }
}

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

package com.android.tv.recommendation;

import android.content.Context;

import com.android.tv.data.Program;
import com.android.tv.recommendation.TvRecommendation.ChannelRecord;
import com.android.tv.recommendation.TvRecommendation.TvRecommender;
import com.android.tv.util.Utils;

public class SampleRecommender extends TvRecommender {
    private final Context mContext;

    public SampleRecommender(Context context) {
        mContext = context;
    }

    @Override
    public double calculateScore(final ChannelRecord cr) {
        if (cr.getLastWatchedTimeMs() == 0l) {
            return NOT_RECOMMENDED;
        }

        Program program = Utils.getCurrentProgram(mContext, cr.getChannelUri());
        if (program == null) {
            return NOT_RECOMMENDED;
        }

        double ret = Math.random();
        ret *= program.getEndTimeUtcMillis() - System.currentTimeMillis();
        ret /= program.getEndTimeUtcMillis() - program.getStartTimeUtcMillis();
        return ret;
    }
}
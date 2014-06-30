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

import com.android.tv.recommendation.TvRecommendation.ChannelRecord;
import com.android.tv.recommendation.TvRecommendation.TvRecommender;

public class RecentChannelRecommender extends TvRecommender {
    private static final long MIN_WATCH_DURATION_MS = 7 * 60 * 1000; // 7 minutes

    private long mLastWatchLogUpdateTimeMs;

    public RecentChannelRecommender() {
        mLastWatchLogUpdateTimeMs = System.currentTimeMillis();
    }

    @Override
    public void onNewWatchLog(ChannelRecord channelRecord) {
        mLastWatchLogUpdateTimeMs = System.currentTimeMillis();
    }

    @Override
    public double calculateScore(final ChannelRecord cr) {
        if (cr.getLastWatchedTimeMs() == 0l
                || cr.getLastWatchDurationMs() < MIN_WATCH_DURATION_MS) {
            return NOT_RECOMMENDED;
        }

        return ((double) cr.getLastWatchedTimeMs()) / mLastWatchLogUpdateTimeMs;
    }
}
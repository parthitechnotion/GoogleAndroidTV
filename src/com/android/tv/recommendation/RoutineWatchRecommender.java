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
import android.text.format.Time;

import com.android.tv.data.Program;
import com.android.tv.recommendation.TvRecommendation.ChannelRecord;
import com.android.tv.recommendation.TvRecommendation.TvRecommender;
import com.android.tv.util.Utils;

public class RoutineWatchRecommender extends TvRecommender {
    // TODO: test and refine constant values in WatchedProgramRecommender in order to
    // improve the performance of this recommender.
    private static final double REQUIRED_MIN_SCORE = 0.15;
    private static final double MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK = 0.7;
    private static final double TITLE_MATCH_WEIGHT = 0.5;
    private static final double TIME_MATCH_WEIGHT = 1 - TITLE_MATCH_WEIGHT;

    private final Context mContext;

    public RoutineWatchRecommender(Context context) {
        mContext = context;
    }

    @Override
    public double calculateScore(final ChannelRecord cr) {
        double maxScore = NOT_RECOMMENDED;
        Program curProgram = Utils.getCurrentProgram(mContext, cr.getChannelUri());

        if (curProgram != null) {
            for (Program program : cr.getWatchHistory()) {
                // TODO: need to consider the watched time.
                double score = calculateTitleMatchScore(curProgram, program) * (TITLE_MATCH_WEIGHT
                        + calculateTimeMatchScore(program) * TIME_MATCH_WEIGHT);
                if (score >= REQUIRED_MIN_SCORE && score > maxScore) {
                    maxScore = score;
                }
            }
        }
        return maxScore;
    }

    private int calculateTimeOfDay(Time time) {
        return time.hour * 60 * 60 + time.minute * 60 + time.second;
    }

    private double calculateTitleMatchScore(Program p1, Program p2) {
        // TODO: Use more proper algorithm for matching title.
        if (p1 == null | p2 == null || p1.getTitle() == null || p2.getTitle() == null) {
            return 0.0;
        }
        return p1.getTitle().equals(p2.getTitle()) ? 1.0 : 0.0;
    }

    private double calculateTimeMatchScore(Program program) {
        Time curTime = new Time();
        curTime.set(System.currentTimeMillis());
        int curTimeOfDay = calculateTimeOfDay(curTime);
        int curWeekDay = curTime.weekDay;

        Time startTime = new Time();
        startTime.set(program.getStartTimeUtcMillis());
        int startTimeOfDay = calculateTimeOfDay(startTime);

        Time endTime = new Time();
        endTime.set(program.getEndTimeUtcMillis());
        int endTimeOfDay = calculateTimeOfDay(endTime);
        if (startTimeOfDay > endTimeOfDay) {
            if (curTimeOfDay < endTimeOfDay) {
                curTimeOfDay += 24 * 60 * 60;
                curWeekDay = (curWeekDay + 6) % 7;
            }
            endTimeOfDay += 24 * 60 * 60;
        }

        double score = calculateTimeOfDayMatchScore(curTimeOfDay, startTimeOfDay, endTimeOfDay);
        if (curWeekDay != startTime.weekDay) {
            score *= MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK;
        }
        return score;
    }

    private double calculateTimeOfDayMatchScore(int curTimeOfDay, int startTimeOfDay,
            int endTimeOfDay) {
        // TODO: need to have intermediate values between 0.0 and 1.0
        if (curTimeOfDay >= startTimeOfDay && curTimeOfDay < endTimeOfDay) {
            return 1.0;
        }
        return 0.0;
    }
}

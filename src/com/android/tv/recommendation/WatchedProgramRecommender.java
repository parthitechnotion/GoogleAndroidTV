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

public class WatchedProgramRecommender extends TvRecommender {
    // TODO: test and refine constant values in WatchedProgramRecommender in order to
    // improve the performance of this recommender.
    private static final double REQUIRED_MIM_SCORE = 0.15;
    private static final double MATCH_SCORE_DAY_OF_WEEK_MATCHED= 1.0;
    private static final double MATCH_SCORE_DAY_OF_WEEK_HIGH = 0.9;
    private static final double MATCH_SCORE_DAY_OF_WEEK_LOW = 0.5;
    private static final double MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED = 0.0;

    private final Context mContext;

    public WatchedProgramRecommender(Context context) {
        mContext = context;
    }

    @Override
    public double calculateScore(final ChannelRecord cr) {
        double maxScore = NOT_RECOMMENDED;
        Program curProgram = Utils.getCurrentProgram(mContext, cr.getChannelUri());

        if (curProgram != null) {
            for (Program program : cr.getWatchHistory()) {
                double score = calculateTitleMatchScore(curProgram, program) * 0.8
                        + calculateTimeMatchScore(program) * 0.2;
                if (score >= REQUIRED_MIM_SCORE && score > maxScore) {
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
        // TODO: need to refine this heuristic method.
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

        return calculateTimeOfDayMatchScore(curTimeOfDay, startTimeOfDay, endTimeOfDay) * 0.7
                + calculateDayOfWeekMatchScore(curWeekDay, startTime.weekDay) * 0.3;
    }

    private double calculateTimeOfDayMatchScore(int curTimeOfDay, int startTimeOfDay,
            int endTimeOfDay) {
        // TODO: need to refine this heuristic method.
        if (curTimeOfDay >= startTimeOfDay && curTimeOfDay < endTimeOfDay) {
            return 1.0;
        }
        double minDiff = Math.min(
                Math.abs(startTimeOfDay - curTimeOfDay),
                Math.abs(curTimeOfDay - endTimeOfDay));
        if (minDiff < 3600.0) {
            return 1.0 - minDiff / 3600.0;
        }
        return 0.0;
    }

    private double calculateDayOfWeekMatchScore(int curWeekDay, int programWeekDay) {
        // TODO: need to refine this heuristic method.
        if (curWeekDay == programWeekDay) {
            return MATCH_SCORE_DAY_OF_WEEK_MATCHED;
        }
        switch (curWeekDay) {
            case Time.MONDAY: {
                switch (programWeekDay) {
                    case Time.TUESDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_HIGH;
                    case Time.WEDNESDAY:
                    case Time.THURSDAY:
                    case Time.FRIDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_LOW;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
            case Time.TUESDAY: {
                switch (programWeekDay) {
                    case Time.MONDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_HIGH;
                    case Time.WEDNESDAY:
                    case Time.THURSDAY:
                    case Time.FRIDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_LOW;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
            case Time.WEDNESDAY: {
                switch (programWeekDay) {
                    case Time.THURSDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_HIGH;
                    case Time.MONDAY:
                    case Time.TUESDAY:
                    case Time.FRIDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_LOW;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
            case Time.THURSDAY: {
                switch (programWeekDay) {
                    case Time.WEDNESDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_HIGH;
                    case Time.MONDAY:
                    case Time.TUESDAY:
                    case Time.FRIDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_LOW;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
            case Time.FRIDAY: {
                switch (programWeekDay) {
                    case Time.MONDAY:
                    case Time.TUESDAY:
                    case Time.WEDNESDAY:
                    case Time.THURSDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_LOW;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
            case Time.SATURDAY: {
                switch (programWeekDay) {
                    case Time.SUNDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_HIGH;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
            case Time.SUNDAY: {
                switch (programWeekDay) {
                    case Time.SATURDAY:
                        return MATCH_SCORE_DAY_OF_WEEK_HIGH;
                }
                return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
            }
        }
        return MATCH_SCORE_DAY_OF_WEEK_NOT_MATCHED;
    }
}

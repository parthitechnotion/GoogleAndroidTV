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

package com.android.tv.dvr;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.recording.RecordingCapability;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.util.SoftPreconditions;
import com.android.tv.util.Utils;

import java.util.Collections;
import java.util.List;

/**
 * DVR manager class to add and remove recordings. UI can modify recording list through this class,
 * instead of modifying them directly through {@link DvrDataManager}.
 */
@MainThread
public class DvrManager {
    private final static String TAG = "DvrManager";
    private final WritableDvrDataManager mDataManager;
    private final ChannelDataManager mChannelDataManager;
    private final DvrSessionManager mDvrSessionManager;

    public DvrManager(Context context) {
        SoftPreconditions.checkFeatureEnabled(context, CommonFeatures.DVR, TAG);
        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        mDataManager = (WritableDvrDataManager) appSingletons.getDvrDataManager();
        mChannelDataManager = appSingletons.getChannelDataManager();
        mDvrSessionManager = appSingletons.getDvrSessionManger();
    }

    /**
     * Schedules a recording for {@code program} instead of the list of recording that conflict.
     * @param program the program to record
     * @param recordingsToOverride the possible empty list of recordings that will not be recorded
     */
    public void addSchedule(Program program, List<Recording> recordingsToOverride) {
        Log.i(TAG,
                "Adding scheduled recording of " + program + " instead of " + recordingsToOverride);
        Collections.sort(recordingsToOverride, Recording.PRIORITY_COMPARATOR);
        Channel c = mChannelDataManager.getChannel(program.getChannelId());
        long priority = recordingsToOverride.isEmpty() ? Long.MAX_VALUE
                : recordingsToOverride.get(0).getPriority() - 1;
        Recording r = Recording.builder(c, program)
                .setPriority(priority)
                .build();
        mDataManager.addRecording(r);
    }

    /**
     * Adds a recording schedule with a time range.
     */
    public void addSchedule(Channel channel, long startTime, long endTime) {
        Log.i(TAG, "Adding scheduled recording of channel" + channel + " starting at " +
                Utils.toTimeString(startTime) + " and ending at " + Utils.toTimeString(endTime));
        //TODO: handle error cases
        Recording r = Recording.builder(channel, startTime, endTime).build();
        mDataManager.addRecording(r);
    }

    /**
     * Adds a season recording schedule based on {@code program}.
     */
    public void addSeasonSchedule(Program program) {
        Log.i(TAG, "Adding season recording of " + program);
        // TODO: implement
    }

    /**
     * Removes a scheduled recording or an existing recording.
     */
    public void removeRecording(Recording recording) {
        Log.i(TAG, "Removing " + recording);
        // TODO(DVR): ask the TIS to delete the recording and respond to the result.
        mDataManager.removeRecording(recording);
    }

    /**
     * Returns priority ordered list of all scheduled recording that will not be recorded if
     * this program is.
     *
     * <p>Any empty list means there is no conflicts.  If there is conflict the program must be
     * scheduled to record with a Priority lower than the first Recording in the list returned.
     */
    public List<Recording> getScheduledRecordingsThatConflict(Program program) {
        //TODO(DVR): move to scheduler.
        //TODO(DVR): deal with more than one DvrInputService
        List<Recording> overLap = mDataManager.getRecordingsThatOverlapWith(getPeriod(program));
        if (!overLap.isEmpty()) {
            // TODO(DVR): ignore shows that already won't record.
            Channel channel = mChannelDataManager.getChannel(program.getChannelId());
            if (channel != null) {
                RecordingCapability recordingCapability = mDvrSessionManager
                        .getRecordingCapability(channel.getInputId());
                int remove = Math.max(0, recordingCapability.maxConcurrentTunedSessions - 1);
                if (remove >= overLap.size()) {
                    return Collections.EMPTY_LIST;
                }
                overLap = overLap.subList(remove, overLap.size() - 1);
            }
        }
        return overLap;
    }

    @NonNull
    private static Range getPeriod(Program program) {
        return new Range(program.getStartTimeUtcMillis(), program.getEndTimeUtcMillis());
    }

    /**
     * Checks whether {@code channel} can be tuned without any conflict with existing recordings
     * in progress. If there is any conflict, {@code outConflictRecordings} will be filled.
     */
    public boolean canTuneTo(Channel channel, List<Recording> outConflictRecordings) {
        // TODO: implement
        return true;
    }

    /**
     * Returns true is the inputId supports recording.
     */
    public boolean canRecord(String inputId) {
        RecordingCapability recordingCapability = mDvrSessionManager
                .getRecordingCapability(inputId);
        return recordingCapability != null && recordingCapability.maxConcurrentTunedSessions > 0;
    }
}

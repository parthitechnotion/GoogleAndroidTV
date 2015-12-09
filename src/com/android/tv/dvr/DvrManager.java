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
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.Features;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.util.SoftPreconditions;
import com.android.tv.util.Utils;

import java.util.List;

/**
 * DVR manager class to add and remove recordings. UI can modify recording list through this class,
 * instead of modifying them directly through {@link DvrDataManager}.
 */
public class DvrManager {
    private final static String TAG = "DvrManager";
    private final WritableDvrDataManager mDataManager;
    private final ChannelDataManager mChannelDataManager;

    public DvrManager(Context context) {
        SoftPreconditions.checkFeatureEnabled(context, Features.DVR, TAG);
        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        mDataManager = (WritableDvrDataManager) appSingletons.getDvrDataManager();
        mChannelDataManager = appSingletons.getChannelDataManager();
    }

    /**
     * Adds a recording schedule for {@code program}.
     */
    public void addSchedule(Program program) {
        Log.i(TAG, "Adding scheduled recording of " + program);
        //TODO: handle error cases
        Channel c = mChannelDataManager.getChannel(program.getChannelId());
        Recording r = Recording.builder(c, program).build();
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
        mDataManager.removeRecording(recording);
    }

    /**
     * Checks whether {@code program} can be recorded without any conflict. If there is any
     * conflict, {@code outConflictRecordings} will be filled.
     */
    public boolean canAddSchedule(Program program, List<Recording> outConflictRecordings) {
        // TODO: implement
        return true;
    }

    /**
     * Checks whether {@code channel} can be tuned without any conflict with existing recordings
     * in progress. If there is any conflict, {@code outConflictRecordings} will be filled.
     */
    public boolean canTuneTo(Channel channel, List<Recording> outConflictRecordings) {
        // TODO: implement
        return true;
    }
}

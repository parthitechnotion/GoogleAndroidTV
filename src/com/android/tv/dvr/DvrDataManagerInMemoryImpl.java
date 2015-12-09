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
 * limitations under the License
 */

package com.android.tv.dvr;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A DVR Data manager that stores values in memory suitable for testing.
 */
@VisibleForTesting // TODO(DVR): move to testing dir.
public final class DvrDataManagerInMemoryImpl extends BaseDvrDataManager {
    private final Map<Long, Recording> mRecordings = new HashMap<>();
    private List<SeasonRecording> mSeasonSchedule = new ArrayList<>();

    DvrDataManagerInMemoryImpl(Context context) {
        super(context);
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public List<Recording> getRecordings() {
        return new ArrayList(mRecordings.values());
    }

    @Override
    public List<Recording> getFinishedRecordings() {
        //TODO filter
        return new ArrayList(mRecordings.values());
    }

    @Override
    public List<Recording> getStartedRecordings() {
        return null;
    }

    @Override
    public List<Recording> getScheduledRecordings() {
        //TODO filter
        return new ArrayList(mRecordings.values());
    }

    @Override
    public List<SeasonRecording> getSeasonRecordings() {
        return mSeasonSchedule;
    }

    @Override
    public long getNextScheduledStartTimeAfter(long startTime) {

        List<Recording> temp = getScheduledRecordings();
        Collections.sort(temp, Recording.START_TIME_COMPARATOR);
        for (Recording r : temp) {
            if (r.getStartTimeMs() > startTime) {
                return r.getStartTimeMs();
            }
        }
        return DvrDataManager.NEXT_START_TIME_NOT_FOUND;
    }

    @Override
    public List<Recording> getRecordingsThatOverlapWith(Range<Long> period) {
        List<Recording> temp = getRecordings();
        List<Recording> result = new ArrayList<>();
        for (Recording r : temp) {
            if (r.isOverLapping(period)) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * Add a new recording.
     */
    @Override
    public void addRecording(Recording recording) {
        mRecordings.put(recording.getId(), recording);
        notifyRecordingAdded(recording);
    }

    @Override
    public void addSeasonRecording(SeasonRecording seasonRecording) {
        mSeasonSchedule.add(seasonRecording);
    }

    @Override
    public void removeRecording(Recording recording) {
        mRecordings.remove(recording.getId());
        notifyRecordingRemoved(recording);
    }

    @Override
    public void removeSeasonSchedule(SeasonRecording seasonSchedule) {
        mSeasonSchedule.remove(seasonSchedule);
    }

    @Override
    public void updateRecording(Recording r) {
        long id = r.getId();
        if (mRecordings.containsKey(id)) {
            mRecordings.put(id, r);
            notifyRecordingStatusChanged(r);
        } else {
            throw new IllegalArgumentException("Recording not found:" + r);
        }
    }

    @Nullable
    public Recording getRecording(long id) {
        return mRecordings.get(id);
    }
}

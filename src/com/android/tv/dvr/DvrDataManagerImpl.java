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
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Range;

import com.android.tv.dvr.Recording.RecordingState;
import com.android.tv.dvr.provider.AsyncDvrDbTask;
import com.android.tv.dvr.provider.AsyncDvrDbTask.AsyncDvrQueryTask;
import com.android.tv.util.SoftPreconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * DVR Data manager to handle recordings and schedules.
 */
@MainThread
public class DvrDataManagerImpl extends BaseDvrDataManager {
    private static final String TAG = "DvrDataManagerImpl";

    private Context mContext;
    private boolean mLoadFinished;
    private final HashMap<Long, Recording> mRecordings = new HashMap<>();
    private AsyncDvrQueryTask mQueryTask;

    public DvrDataManagerImpl(Context context) {
        super(context);
        mContext = context;
    }

    public void start() {
        mQueryTask = new AsyncDvrQueryTask(mContext) {
            @Override
            protected void onPostExecute(List<Recording> result) {
                mQueryTask = null;
                mLoadFinished = true;
                for (Recording r : result) {
                    mRecordings.put(r.getId(), r);
                }
            }
        };
        mQueryTask.executeOnDbThread();
    }

    public void stop() {
        if (mQueryTask != null) {
            mQueryTask.cancel(true);
            mQueryTask = null;
        }
    }

    @Override
    public boolean isInitialized() {
        return mLoadFinished;
    }

    @Override
    public List<Recording> getRecordings() {
        if (!mLoadFinished) {
            return Collections.emptyList();
        }
        ArrayList<Recording> list = new ArrayList<>(mRecordings.size());
        list.addAll(mRecordings.values());
        Collections.sort(list, Recording.START_TIME_COMPARATOR);
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<Recording> getFinishedRecordings() {
        return getRecordingsWithState(Recording.STATE_RECORDING_FINISHED);
    }

    @Override
    public List<Recording> getStartedRecordings() {
        return getRecordingsWithState(Recording.STATE_RECORDING_IN_PROGRESS);
    }

    @Override
    public List<Recording> getScheduledRecordings() {
        return getRecordingsWithState(Recording.STATE_RECORDING_NOT_STARTED);
    }

    private List<Recording> getRecordingsWithState(@RecordingState int state) {
        List<Recording> result = new ArrayList<>();
        for (Recording r : mRecordings.values()) {
            if (r.getState() == state) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public List<SeasonRecording> getSeasonRecordings() {
        // If we return dummy data here, we can implement UI part independently.
        return Collections.emptyList();
    }

    @Override
    public long getNextScheduledStartTimeAfter(long startTime) {
        return getNextStartTimeAfter(getRecordings(), startTime);
    }

    @VisibleForTesting
    static long getNextStartTimeAfter(List<Recording> recordings, long startTime) {
        int start = 0;
        int end = recordings.size() - 1;
        while (start <= end) {
            int mid = (start + end) / 2;
            if (recordings.get(mid).getStartTimeMs() <= startTime) {
                start = mid + 1;
            } else {
                end = mid - 1;
            }
        }
        return start < recordings.size() ? recordings.get(start).getStartTimeMs()
                : NEXT_START_TIME_NOT_FOUND;
    }

    @Override
    public List<Recording> getRecordingsThatOverlapWith(Range<Long> period) {
        List<Recording> result = new ArrayList<>();
        for (Recording r : mRecordings.values()) {
            if (r.isOverLapping(period)) {
                result.add(r);
            }
        }
        return result;
    }

    @Nullable
    @Override
    public Recording getRecording(long recordingId) {
        if (mLoadFinished) {
            return mRecordings.get(recordingId);
        }
        return null;
    }

    @Override
    public void addRecording(final Recording recording) {
        new AsyncDvrDbTask.AsyncAddRecordingTask(mContext) {
            @Override
            protected void onPostExecute(List<Recording> recordings) {
                super.onPostExecute(recordings);
                SoftPreconditions.checkArgument(recordings.size() == 1);
                for (Recording r : recordings) {
                    if (r.getId() != -1) {
                        mRecordings.put(r.getId(), r);
                        notifyRecordingAdded(r);
                    } else {
                        Log.w(TAG, "Error adding " + r);
                    }
                }

            }
        }.executeOnDbThread(recording);
    }

    @Override
    public void addSeasonRecording(SeasonRecording seasonRecording) { }

    @Override
    public void removeRecording(final Recording recording) {
        new AsyncDvrDbTask.AsyncDeleteRecordingTask(mContext) {
            @Override
            protected void onPostExecute(List<Integer> counts) {
                super.onPostExecute(counts);
                SoftPreconditions.checkArgument(counts.size() == 1);
                for (Integer c : counts) {
                    if (c == 1) {
                        mRecordings.remove(recording.getId());
                        //TODO change to notifyRecordingUpdated
                        notifyRecordingRemoved(recording);
                    } else {
                        Log.w(TAG, "Error removing " + recording);
                    }
                }

            }
        }.executeOnDbThread(recording);
    }

    @Override
    public void removeSeasonSchedule(SeasonRecording seasonSchedule) { }

    @Override
    public void updateRecording(final Recording recording) {
        new AsyncDvrDbTask.AsyncUpdateRecordingTask(mContext) {
            @Override
            protected void onPostExecute(List<Integer> counts) {
                super.onPostExecute(counts);
                SoftPreconditions.checkArgument(counts.size() == 1);
                for (Integer c : counts) {
                    if (c == 1) {
                        mRecordings.put(recording.getId(), recording);
                        //TODO change to notifyRecordingUpdated
                        notifyRecordingStatusChanged(recording);
                    } else {
                        Log.w(TAG, "Error updating " + recording);
                    }
                }

            }
        }.executeOnDbThread(recording);
    }
}

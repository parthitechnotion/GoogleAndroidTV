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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Range;

import com.android.tv.util.Clock;
import com.android.tv.util.NamedThreadFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * The core class to manage schedule and run actual recording.
 */
@VisibleForTesting
public class Scheduler implements DvrDataManager.Listener {
    private static final String TAG = "Scheduler";
    private static final boolean DEBUG = false;

    /**
     * Wraps a RecordingTask removing it from {@link #mPendingRecordings} when it is done.
     */
    private final class TaskWrapper extends FutureTask<Void> {
        private final long mId;

        TaskWrapper(Recording recording) {
            super(new RecordingTask(recording, mSessionManager, mDataManager, mClock), null);
            mId = recording.getId();
        }

        @Override
        public void done() {
            if (DEBUG) Log.d(TAG, "done " + mId);
            mPendingRecordings.remove(mId);
            super.done();
        }
    }

    private final WritableDvrDataManager mDataManager;
    private final Context mContext;
    private final DvrSessionManager mSessionManager;
    private PendingIntent mAlarmIntent;

    private static final NamedThreadFactory sNamedThreadFactory = new NamedThreadFactory(
            "DVR-scheduler");
    @VisibleForTesting final static long MS_TO_WAKE_BEFORE_START = TimeUnit.MINUTES.toMillis(1);
    private final static long SOON_DURATION_IN_MS = TimeUnit.MINUTES.toMillis(5);

    private final ExecutorService mExecutorService = Executors
            .newCachedThreadPool(sNamedThreadFactory);
    private final LongSparseArray<TaskWrapper> mPendingRecordings = new LongSparseArray<>();
    private final Clock mClock;
    private final AlarmManager mAlarmManager;

    public Scheduler(DvrSessionManager sessionManager, WritableDvrDataManager dataManager,
            Context context, Clock clock,
            AlarmManager alarmManager) {
        mSessionManager = sessionManager;
        mDataManager = dataManager;
        mContext = context;
        mClock = clock;
        mAlarmManager = alarmManager;
    }

    private void updatePendingRecordings() {
        List<Recording> recordings = mDataManager.getRecordingsThatOverlapWith(
                new Range(mClock.currentTimeMillis(),
                        mClock.currentTimeMillis() + SOON_DURATION_IN_MS));
        // TODO(DVR): handle removing and updating exiting recordings.
        for (Recording r : recordings) {
            scheduleRecordingSoon(r);
        }
    }

    /**
     * Start recording that will happen soon, and set the next alarm time.
     */
    public void update() {
        if (DEBUG) Log.d(TAG, "update");
        updatePendingRecordings();
        updateNextAlarm();
    }


    @Override
    public void onRecordingAdded(Recording recording) {
        if (DEBUG) Log.d(TAG, "added " + recording);
        if (startsWithin(recording, SOON_DURATION_IN_MS)) {
            scheduleRecordingSoon(recording);
        } else {
            updateNextAlarm();
        }
    }

    @Override
    public void onRecordingRemoved(Recording recording) {
        long id = recording.getId();
        TaskWrapper task = mPendingRecordings.get(id);
        if (task != null) {
            task.cancel(true);
            mPendingRecordings.remove(id);
        } else {
            updateNextAlarm();
        }
    }

    @Override
    public void onRecordingStatusChanged(Recording recording) {
        //TODO(DVR): implement
    }


    private void scheduleRecordingSoon(Recording recording) {
        // TODO(DVR) test match in mPendingRecordings recordings.
        TaskWrapper task = new TaskWrapper(recording);
        mPendingRecordings.put(recording.getId(), task);
        mExecutorService.submit(task);
    }

    private void updateNextAlarm() {
        long lastStartTimePending = getLastStartTimePending();
        long nextStartTime = mDataManager.getNextScheduledStartTimeAfter(lastStartTimePending);
        if (nextStartTime != DvrDataManager.NEXT_START_TIME_NOT_FOUND) {
            long wakeAt = nextStartTime - MS_TO_WAKE_BEFORE_START;
            if (DEBUG) Log.d(TAG, "Set alarm to record at " + wakeAt);
            Intent intent = new Intent(mContext, DvrStartRecordingReceiver.class);
            mAlarmIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
            //This will cancel the previous alarm.
            mAlarmManager.set(AlarmManager.RTC_WAKEUP, wakeAt, mAlarmIntent);
        } else {
            if (DEBUG) Log.d(TAG, "No future recording, alarm not set");
        }
    }

    private long getLastStartTimePending() {
        // TODO(DVR): implement
        return mClock.currentTimeMillis();
    }

    @VisibleForTesting
    boolean startsWithin(Recording recording, long durationInMs) {
        return mClock.currentTimeMillis() >= recording.getStartTimeMs() - durationInMs;
    }
}

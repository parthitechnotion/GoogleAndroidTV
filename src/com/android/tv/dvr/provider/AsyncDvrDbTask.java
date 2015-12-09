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

package com.android.tv.dvr.provider;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dvr.Recording;
import com.android.tv.dvr.provider.DvrContract.DvrChannels;
import com.android.tv.dvr.provider.DvrContract.DvrPrograms;
import com.android.tv.dvr.provider.DvrContract.RecordingToPrograms;
import com.android.tv.dvr.provider.DvrContract.Recordings;
import com.android.tv.util.NamedThreadFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link AsyncTask} that defaults to executing on its own single threaded Executor Service.
 */
public abstract class AsyncDvrDbTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {
    private static final NamedThreadFactory THREAD_FACTORY = new NamedThreadFactory(
            AsyncDvrDbTask.class.getSimpleName());
    private static final ExecutorService DB_EXECUTOR = Executors
            .newSingleThreadExecutor(THREAD_FACTORY);

    private static DvrDatabaseHelper sDbHelper;

    private static synchronized DvrDatabaseHelper initializeDbHelper(Context context) {
        if (sDbHelper == null) {
            sDbHelper = new DvrDatabaseHelper(context.getApplicationContext());
        }
        return sDbHelper;
    }

    final Context mContext;

    private AsyncDvrDbTask(Context context) {
        mContext = context;
    }

    /**
     * Execute the task on the {@link #DB_EXECUTOR} thread.
     */
    @SafeVarargs
    public final void executeOnDbThread(Params... params) {
        executeOnExecutor(DB_EXECUTOR, params);
    }

    public abstract static class AsyncDvrQueryTask
            extends AsyncDvrDbTask<Void, Void, List<Recording>> {
        public AsyncDvrQueryTask(Context context) {
            super(context);
        }

        @Override
        protected List<Recording> doInBackground(Void... params) {
            initializeDbHelper(mContext);

            if (isCancelled()) {
                return null;
            }
            // Read Channels Table.
            Map<Long, Channel> channelMap = new HashMap<>();
            try (Cursor c = sDbHelper.query(DvrChannels.TABLE_NAME, Channel.PROJECTION_DVR)) {
                while (c.moveToNext() && !isCancelled()) {
                    Channel channel = Channel.fromDvrCursor(c);
                    channelMap.put(channel.getDvrId(), channel);
                }
            }

            if (isCancelled()) {
                return null;
            }
            // Read Programs Table.
            Map<Long, Program> programMap = new HashMap<>();
            try (Cursor c = sDbHelper.query(DvrPrograms.TABLE_NAME, Program.PROJECTION_DVR)) {
                while (c.moveToNext() && !isCancelled()) {
                    Program program = Program.fromDvrCursor(c);
                    programMap.put(program.getDvrId(), program);
                }
            }

            if (isCancelled()) {
                return null;
            }
            // Read Mapping Table.
            Map<Long, List<Long>> recordingToProgramMap = new HashMap<>();
            try (Cursor c = sDbHelper.query(RecordingToPrograms.TABLE_NAME, new String[] {
                    RecordingToPrograms.COLUMN_RECORDING_ID,
                    RecordingToPrograms.COLUMN_PROGRAM_ID})) {
                while (c.moveToNext() && !isCancelled()) {
                    long recordingId = c.getLong(0);
                    List<Long> programList = recordingToProgramMap.get(recordingId);
                    if (programList == null) {
                        programList = new ArrayList<>();
                    }
                    programList.add(c.getLong(1));
                }
            }

            if (isCancelled()) {
                return null;
            }
            List<Recording> recordings = new ArrayList<>();
            try (Cursor c = sDbHelper.query(Recordings.TABLE_NAME, Recording.PROJECTION)) {
                int idIndex = c.getColumnIndex(Recordings._ID);
                int channelIndex = c.getColumnIndex(Recordings.COLUMN_CHANNEL_ID);
                while (c.moveToNext() && !isCancelled()) {
                    Channel channel = channelMap.get(c.getLong(channelIndex));
                    List<Program> programs = null;
                    long recordingId = c.getLong(idIndex);
                    List<Long> programIds = recordingToProgramMap.get(recordingId);
                    if (programIds != null) {
                        programs = new ArrayList<>();
                        for (long programId : programIds) {
                            programs.add(programMap.get(programId));
                        }
                    }
                    recordings.add(Recording.fromCursor(c, channel, programs));
                }
            }
            return recordings;
        }
    }
}

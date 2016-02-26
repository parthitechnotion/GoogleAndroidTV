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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import com.android.tv.data.Channel;
import com.android.tv.dvr.Recording;
import com.android.tv.dvr.provider.DvrContract.DvrChannels;
import com.android.tv.dvr.provider.DvrContract.DvrPrograms;
import com.android.tv.dvr.provider.DvrContract.RecordingToPrograms;
import com.android.tv.dvr.provider.DvrContract.Recordings;

import java.util.ArrayList;
import java.util.List;

/**
 * A data class for one recorded contents.
 */
public class DvrDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DvrDatabaseHelper";
    private static final boolean DEBUG = true;

    private static final int DATABASE_VERSION = 2;
    private static final String DB_NAME = "dvr.db";

    private static final String SQL_CREATE_RECORDINGS =
            "CREATE TABLE " + Recordings.TABLE_NAME + "("
            + Recordings._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + Recordings.COLUMN_PRIORITY
                    + " INTEGER DEFAULT " + Long.MAX_VALUE + ","
            + Recordings.COLUMN_TYPE + " TEXT NOT NULL,"
            + Recordings.COLUMN_URI + " TEXT,"
            + Recordings.COLUMN_CHANNEL_ID + " INTEGER NOT NULL,"
            + Recordings.COLUMN_START_TIME_UTC_MILLIS + " INTEGER NOT NULL,"
            + Recordings.COLUMN_END_TIME_UTC_MILLIS + " INTEGER NOT NULL,"
            + Recordings.COLUMN_MEDIA_SIZE + " INTEGER,"
            + Recordings.COLUMN_STATE + " TEXT NOT NULL)";

    private static final String SQL_CREATE_DVR_CHANNELS =
            "CREATE TABLE " + DvrChannels.TABLE_NAME + "("
            + DvrChannels._ID + " INTEGER PRIMARY KEY AUTOINCREMENT)";

    private static final String SQL_CREATE_DVR_PROGRAMS =
            "CREATE TABLE " + DvrPrograms.TABLE_NAME + "("
            + DvrPrograms._ID + " INTEGER PRIMARY KEY AUTOINCREMENT)";

    private static final String SQL_CREATE_RECORDING_PROGRAMS =
            "CREATE TABLE " + RecordingToPrograms.TABLE_NAME + "("
            + RecordingToPrograms._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + RecordingToPrograms.COLUMN_RECORDING_ID + " INTEGER,"
            + RecordingToPrograms.COLUMN_PROGRAM_ID + " INTEGER,"
            + "FOREIGN KEY(" + RecordingToPrograms.COLUMN_RECORDING_ID
            + ") REFERENCES " + Recordings.TABLE_NAME + "(" + Recordings._ID
            + ") ON UPDATE CASCADE ON DELETE CASCADE,"
            + "FOREIGN KEY(" + RecordingToPrograms.COLUMN_PROGRAM_ID
            + ") REFERENCES " + DvrPrograms.TABLE_NAME + "(" + DvrPrograms._ID
            + ") ON UPDATE CASCADE ON DELETE CASCADE)";

    private static final String SQL_DROP_RECORDINGS = "DROP TABLE IF EXISTS "
            + Recordings.TABLE_NAME;
    private static final String SQL_DROP_DVR_CHANNELS = "DROP TABLE IF EXISTS "
            + DvrChannels.TABLE_NAME;
    private static final String SQL_DROP_DVR_PROGRAMS = "DROP TABLE IF EXISTS "
            + DvrPrograms.TABLE_NAME;
    private static final String SQL_DROP_RECORDING_PROGRAMS = "DROP TABLE IF EXISTS "
            + RecordingToPrograms.TABLE_NAME;
    public static final String WHERE_RECORDING_ID_EQUALS = Recordings._ID + " = ?";

    public DvrDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_CREATE_RECORDINGS);
        db.execSQL(SQL_CREATE_RECORDINGS);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_CREATE_DVR_CHANNELS);
        db.execSQL(SQL_CREATE_DVR_CHANNELS);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_CREATE_DVR_PROGRAMS);
        db.execSQL(SQL_CREATE_DVR_PROGRAMS);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_CREATE_RECORDING_PROGRAMS);
        db.execSQL(SQL_CREATE_RECORDING_PROGRAMS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_DROP_RECORDING_PROGRAMS);
        db.execSQL(SQL_DROP_RECORDING_PROGRAMS);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_DROP_DVR_PROGRAMS);
        db.execSQL(SQL_DROP_DVR_PROGRAMS);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_DROP_DVR_CHANNELS);
        db.execSQL(SQL_DROP_DVR_CHANNELS);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_DROP_RECORDINGS);
        db.execSQL(SQL_DROP_RECORDINGS);
        onCreate(db);
    }

    /**
     * Handles the query request and returns a {@link Cursor}.
     */
    public Cursor query(String tableName, String[] projections) {
        SQLiteDatabase db = getReadableDatabase();
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(tableName);
        return builder.query(db, projections, null, null, null, null, null);
    }

    /**
     * Inserts recordings.
     *
     * @return The list of recordings with id set.  The id will be -1 if there was an error.
     */
    public List<Recording> insertRecordings(Recording... recordings) {
        updateChannelsFromRecordings(recordings);

        SQLiteDatabase db = getReadableDatabase();
        List<Recording> results = new ArrayList<>();
        for (Recording r : recordings) {
            ContentValues values = getContentValues(r);
            long id = db.insert(Recordings.TABLE_NAME, null, values);
            results.add(Recording.buildFrom(r).setId(id).build());
        }
        return results;
    }

    /**
     * Update recordings.
     *
     * @return The list of row update counts.  The count will be -1 if there was an error or 0
     * if no match was found.  The count is expected to be exactly 1 for each recording.
     */
    public List<Integer> updateRecordings(Recording[] recordings) {
        updateChannelsFromRecordings(recordings);
        SQLiteDatabase db = getWritableDatabase();
        List<Integer> results = new ArrayList<>();
        long count = 0;
        for (Recording r : recordings) {
            ContentValues values = getContentValues(r);
            int updated = db.update(Recordings.TABLE_NAME, values, Recordings._ID + " = ?",
                    new String[] {String.valueOf(r.getId())});
            results.add(updated);
        }
        return results;
    }

    private void updateChannelsFromRecordings(Recording[] recordings) {
       // TODO(DVR) implement/
       // TODO(DVR) consider not deleting channels instead of keeping a separate table.
    }

    private ContentValues getContentValues(Recording r) {
        ContentValues values = new ContentValues();
        // TODO(DVR): use DVR channel id instead
        Channel channel = r.getChannel();
        if (channel != null) {
            values.put(Recordings.COLUMN_CHANNEL_ID, channel.getId());
        }
        values.put(Recordings.COLUMN_PRIORITY, r.getPriority());
        values.put(Recordings.COLUMN_START_TIME_UTC_MILLIS, r.getStartTimeMs());
        values.put(Recordings.COLUMN_END_TIME_UTC_MILLIS, r.getEndTimeMs());
        values.put(Recordings.COLUMN_STATE, r.getState());
        values.put(Recordings.COLUMN_MEDIA_SIZE, r.getSize());
        values.put(Recordings.COLUMN_TYPE, r.getType());
        if (r.getUri() != null) {
            values.put(Recordings.COLUMN_URI, r.getUri().toString());
        }
        return values;
    }

    /**
     * Delete recordings.
     *
     * @return The list of row update counts.  The count will be -1 if there was an error or 0
     * if no match was found.  The count is expected to be exactly 1 for each recording.
     */
    public List<Integer> deleteRecordings(Recording[] recordings) {
        SQLiteDatabase db = getWritableDatabase();
        List<Integer> results = new ArrayList<>();
        long count = 0;
        for (Recording r : recordings) {
            ContentValues values = getContentValues(r);
            int deleted = db.delete(Recordings.TABLE_NAME, WHERE_RECORDING_ID_EQUALS,
                    new String[] {String.valueOf(r.getId())});
            results.add(deleted);
        }
        return results;
    }
}

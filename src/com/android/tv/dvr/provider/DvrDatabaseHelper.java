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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import com.android.tv.dvr.provider.DvrContract.DvrChannels;
import com.android.tv.dvr.provider.DvrContract.DvrPrograms;
import com.android.tv.dvr.provider.DvrContract.RecordingToPrograms;
import com.android.tv.dvr.provider.DvrContract.Recordings;

/**
 * A data class for one recorded contents.
 */
public class DvrDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DvrDatabaseHelper";
    private static final boolean DEBUG = true;

    private static final int DATABASE_VERSION = 1;
    private static final String DB_NAME = "dvr.db";

    private static final String SQL_CREATE_RECORDINGS =
            "CREATE TABLE " + Recordings.TABLE_NAME + "("
            + Recordings._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
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
}

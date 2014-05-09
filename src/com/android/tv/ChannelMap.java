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

package com.android.tv;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.TvContract;
import android.util.Log;

/**
 * The class that abstracts the channel information for each input service and provides convenient
 * methods to access it.
 */
public class ChannelMap implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ChannelMap";
    private static final int CURSOR_LOADER_ID = 0;

    private final Activity mActivity;
    private final ComponentName mInputName;
    private long mCurrentChannelId;
    private Cursor mCursor;
    private final Runnable mOnLoadFinished;
    private boolean mIsLoadFinished;
    private int mIndexId;
    private int mIndexDisplayNumber;
    private int mIndexDisplayName;

    public ChannelMap(Activity activity, ComponentName inputName, long initChannelId,
            Runnable onLoadFinished) {
        mActivity = activity;
        mInputName = inputName;
        mCurrentChannelId = initChannelId;
        mOnLoadFinished = onLoadFinished;
        mActivity.getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);
    }

    public boolean isLoadFinished() {
        return mIsLoadFinished;
    }

    public int size() {
        checkCursor();
        return mCursor.getCount();
    }

    public Uri getCurrentChannelUri() {
        checkCursor();
        if (mCursor.getCount() < 1) {
            return null;
        }
        long id = mCursor.getLong(mIndexId);
        return ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, id);
    }

    public String getCurrentDisplayNumber() {
        checkCursor();
        if (mCursor.getCount() < 1) {
            return null;
        }
        return mCursor.getString(mIndexDisplayNumber);
    }

    public String getCurrentDisplayName() {
        checkCursor();
        if (mCursor.getCount() < 1) {
            return null;
        }
        return mCursor.getString(mIndexDisplayName);
    }

    public void moveToNextChannel() {
        checkCursor();
        if (!mCursor.moveToNext()) {
            mCursor.moveToFirst();
        }
        mCurrentChannelId = (mCursor.getCount() == 0) ? Channel.INVALID_ID :
                mCursor.getLong(mIndexId);
    }

    public void moveToPreviousChannel() {
        checkCursor();
        if (!mCursor.moveToPrevious()) {
            mCursor.moveToLast();
        }
        mCurrentChannelId = (mCursor.getCount() == 0) ? Channel.INVALID_ID :
                mCursor.getLong(mIndexId);
    }

    public void moveToChannel(long id) {
        checkCursor();
        if (mCursor.getCount() == 0) {
            return;
        }
        int position = mCursor.getPosition();
        mCursor.moveToFirst();
        do {
            if (mCursor.getLong(mIndexId) == id) {
                mCurrentChannelId = mCursor.getLong(mIndexId);
                return;
            }
        } while (mCursor.moveToNext());
        mCursor.moveToPosition(position);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = TvContract.buildChannelsUriForInput(mInputName);
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.DISPLAY_NUMBER,
                TvContract.Channels.DISPLAY_NAME};
        return new CursorLoader(mActivity, uri, projection, null, null,
                TvInputUtils.CHANNEL_SORT_ORDER);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Log.d(TAG, "onLoadFinished()");
        mCursor = cursor;
        if (mCursor == null || mCursor.isClosed()) {
            return;
        }
        cursor.setNotificationUri(mActivity.getContentResolver(), TvContract.Channels.CONTENT_URI);
        mIndexId = mCursor.getColumnIndex(TvContract.Channels._ID);
        mIndexDisplayNumber = mCursor.getColumnIndex(TvContract.Channels.DISPLAY_NUMBER);
        mIndexDisplayName = mCursor.getColumnIndex(TvContract.Channels.DISPLAY_NAME);
        if (mCursor.getCount() > 0) {
            mCursor.moveToFirst();
            if (mCurrentChannelId != Channel.INVALID_ID) {
                moveToChannel(mCurrentChannelId);
            }
            mCurrentChannelId = mCursor.getLong(mIndexId);
        } else {
            mCurrentChannelId = Channel.INVALID_ID;
        }
        mIsLoadFinished = true;
        mOnLoadFinished.run();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursor) {
        mCursor = null;
        // The framework will take care of closing the old cursor once we return.
    }

    public void close() {
        mActivity.getLoaderManager().destroyLoader(CURSOR_LOADER_ID);
        mCursor = null;
    }

    public void dump() {
        checkCursor();
        int oldPosition = mCursor.getPosition();
        mCursor.moveToFirst();
        do {
            Log.d(TAG, "Ch " + mCursor.getString(mIndexDisplayNumber) + " "
                    + mCursor.getString(mIndexDisplayName));
        } while (mCursor.moveToNext());
        mCursor.moveToPosition(oldPosition);
    }

    private void checkCursor() {
        if (mCursor == null) {
            throw new IllegalStateException("Cursor not loaded");
        }
    }
}

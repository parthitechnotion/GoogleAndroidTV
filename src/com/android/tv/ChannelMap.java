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

    private static final int BROWSABLE = 1;

    private final Activity mActivity;
    private final ComponentName mInputName;
    private long mCurrentChannelId;
    private Cursor mCursor;
    private final TvInputManagerHelper mTvInputManagerHelper;
    private final Runnable mOnLoadFinished;
    private boolean mIsLoadFinished;
    private final boolean mIsUnifiedTvInput;
    private int mIndexId;
    private int mIndexDisplayNumber;
    private int mIndexDisplayName;
    private int mIndexPackageName;
    private int mIndexServiceName;
    private int mIndexBrowsable;
    private int mBrowsableChannelCount;

    public ChannelMap(Activity activity, ComponentName inputName, long initChannelId,
            TvInputManagerHelper tvInputManagerHelper, Runnable onLoadFinished) {
        mActivity = activity;
        mInputName = inputName;
        mIsUnifiedTvInput = mInputName == null;
        mCurrentChannelId = initChannelId;
        mTvInputManagerHelper = tvInputManagerHelper;
        mOnLoadFinished = onLoadFinished;
        mActivity.getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);
    }

    public boolean isLoadFinished() {
        return mIsLoadFinished;
    }

    public int size() {
        checkCursor();
        return mBrowsableChannelCount == 0 ? mCursor.getCount() : mBrowsableChannelCount;
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

    public boolean moveToNextChannel() {
        checkCursor();
        if (mCursor.getCount() <= 0) {
            return false;
        }

        int browsableChannelCount = size();
        int oldPosition = mCursor.getPosition();
        boolean ignoreBrowsable = mBrowsableChannelCount == 0;

        while(browsableChannelCount > 0 && (mCursor.moveToNext() || mCursor.moveToFirst())) {
            if (mCursor.getInt(mIndexBrowsable) == BROWSABLE || ignoreBrowsable) {
                --browsableChannelCount;
                if (!mTvInputManagerHelper.isAvaliable(getComponentName())) {
                    continue;
                }
                mCurrentChannelId = mCursor.getLong(mIndexId);
                return true;
            }
        }
        mCursor.moveToPosition(oldPosition);
        return false;
   }

    public boolean moveToPreviousChannel() {
        checkCursor();
        if (mCursor.getCount() <= 0) {
            return false;
        }

        int browsableChannelCount = size();
        int oldPosition = mCursor.getPosition();
        boolean ignoreBrowsable = mBrowsableChannelCount == 0;

        while(browsableChannelCount > 0 && (mCursor.moveToPrevious() || mCursor.moveToLast())) {
            if (mCursor.getInt(mIndexBrowsable) == BROWSABLE || ignoreBrowsable) {
                --browsableChannelCount;
                if (!mTvInputManagerHelper.isAvaliable(getComponentName())) {
                    continue;
                }
                mCurrentChannelId = mCursor.getLong(mIndexId);
                return true;
            }
        }
        mCursor.moveToPosition(oldPosition);
        return false;
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
        Uri uri;
        if (mIsUnifiedTvInput) {
            uri = TvContract.Channels.CONTENT_URI;
        } else {
            uri = TvContract.buildChannelsUriForInput(mInputName, false);
        }
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.DISPLAY_NUMBER,
                TvContract.Channels.DISPLAY_NAME,
                TvContract.Channels.PACKAGE_NAME,
                TvContract.Channels.SERVICE_NAME,
                TvContract.Channels.BROWSABLE};
        String sortOrder;
        if (mIsUnifiedTvInput) {
            sortOrder = TvInputUtils.CHANNEL_SORT_ORDER_BY_INPUT_NAME + ", "
                    + TvInputUtils.CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER;
        } else {
            sortOrder = TvInputUtils.CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER;
        }
        return new CursorLoader(mActivity, uri, projection, null, null, sortOrder);
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
        mIndexPackageName = mCursor.getColumnIndex(TvContract.Channels.PACKAGE_NAME);
        mIndexServiceName = mCursor.getColumnIndex(TvContract.Channels.SERVICE_NAME);
        mIndexBrowsable = mCursor.getColumnIndex(TvContract.Channels.BROWSABLE);
        mBrowsableChannelCount = 0;
        if (mCursor.getCount() > 0) {
            mCursor.moveToFirst();
            do {
                if (mCursor.getInt(mIndexBrowsable) == BROWSABLE) {
                    ++mBrowsableChannelCount;
                }
            } while (mCursor.moveToNext());

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

    private ComponentName getComponentName() {
        return new ComponentName(mCursor.getString(mIndexPackageName),
                mCursor.getString(mIndexServiceName));
    }

    private void checkCursor() {
        if (mCursor == null) {
            throw new IllegalStateException("Cursor not loaded");
        }
    }
}

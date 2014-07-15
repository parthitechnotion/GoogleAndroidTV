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

package com.android.tv.data;

import static android.media.tv.TvInputManager.INPUT_STATE_DISCONNECTED;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.android.tv.input.TvInput;
import com.android.tv.util.TvInputManagerHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The class that abstracts the channel information for each input and provides convenient
 * methods to access it.
 */
public class ChannelMap implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ChannelMap";
    private static final int CURSOR_LOADER_ID = 0;

    private static final int BROWSABLE = 1;

    private final Activity mActivity;
    private final TvInput mInput;
    private long mCurrentChannelId;
    private Cursor mCursor;
    private final TvInputManagerHelper mTvInputManagerHelper;
    private final Runnable mOnLoadFinished;
    private boolean mIsLoadFinished;
    private int mIndexId;
    private int mIndexDisplayNumber;
    private int mIndexDisplayName;
    private int mIndexPackageName;
    private int mIndexInputId;
    private int mIndexBrowsable;
    private int mBrowsableChannelCount;
    private final Map<Long, Channel> mChannels = new LinkedHashMap<Long, Channel>();

    public ChannelMap(Activity activity, TvInput tvInput, long initChannelId,
            TvInputManagerHelper tvInputManagerHelper, Runnable onLoadFinished) {
        mActivity = activity;
        mInput = tvInput;
        mCurrentChannelId = initChannelId;
        mTvInputManagerHelper = tvInputManagerHelper;
        mOnLoadFinished = onLoadFinished;
        mActivity.getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);
    }

    public boolean isLoadFinished() {
        return mIsLoadFinished;
    }

    public int getBrowsableChannelCount() {
        checkCursor();
        return mBrowsableChannelCount;
    }

    public TvInput getTvInput() {
        return mInput;
    }

    public boolean containsAndIsBrowsable(Channel c) {
        return mChannels.keySet().contains(c.getId())
                && (c.isBrowsable() || mBrowsableChannelCount == 0);
    }

    public Channel[] getChannelList(boolean browsableOnly) {
        if (mBrowsableChannelCount == 0 || !browsableOnly) {
            return mChannels.values().toArray(new Channel[0]);
        }

        Channel[] channels = new Channel[mBrowsableChannelCount];
        int index = 0;
        for (Channel channel : mChannels.values()) {
            if (channel.isBrowsable()) {
                channels[index++] = channel;
            }
        }
        return channels;
    }

    public Channel getCurrentChannel() {
        return mCurrentChannelId != Channel.INVALID_ID ? mChannels.get(mCurrentChannelId) : null;
    }

    public int size() {
        checkCursor();
        return mBrowsableChannelCount == 0 ? mCursor.getCount() : mBrowsableChannelCount;
    }

    public long getCurrentChannelId() {
        checkCursor();
        if (mCursor.getCount() < 1) {
            return Channel.INVALID_ID;
        }
        return mCursor.getLong(mIndexId);
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
                if (mTvInputManagerHelper.getInputState(getInputId()) == INPUT_STATE_DISCONNECTED) {
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
                if (mTvInputManagerHelper.getInputState(getInputId()) == INPUT_STATE_DISCONNECTED) {
                    continue;
                }
                mCurrentChannelId = mCursor.getLong(mIndexId);
                return true;
            }
        }
        mCursor.moveToPosition(oldPosition);
        return false;
    }

    public boolean moveToChannel(long id) {
        checkCursor();
        if (mCursor.getCount() == 0) {
            return false;
        }
        int position = mCursor.getPosition();
        mCursor.moveToFirst();
        do {
            if (mCursor.getLong(mIndexId) == id) {
                mCurrentChannelId = mCursor.getLong(mIndexId);
                return true;
            }
        } while (mCursor.moveToNext());
        mCursor.moveToPosition(position);
        return false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = mInput.buildChannelsUri(null);
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_INPUT_ID,
                TvContract.Channels.COLUMN_BROWSABLE };
        String sortOrder = mInput.buildChannelsSortOrder();
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
        mIndexDisplayNumber = mCursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        mIndexDisplayName = mCursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        mIndexPackageName = mCursor.getColumnIndex(TvContract.Channels.COLUMN_PACKAGE_NAME);
        mIndexInputId = mCursor.getColumnIndex(TvContract.Channels.COLUMN_INPUT_ID);
        mIndexBrowsable = mCursor.getColumnIndex(TvContract.Channels.COLUMN_BROWSABLE);
        mBrowsableChannelCount = 0;
        mChannels.clear();
        if (mCursor.getCount() > 0) {
            long firstBrowsableChannelId = Channel.INVALID_ID;

            mCursor.moveToFirst();
            do {
                Channel channel = new Channel.Builder()
                        .setId(mCursor.getLong(mIndexId))
                        .setInputId(mCursor.getString(mIndexInputId))
                        .setDisplayNumber(mCursor.getString(mIndexDisplayNumber))
                        .setDisplayName(mCursor.getString(mIndexDisplayName))
                        .setBrowsable(mCursor.getInt(mIndexBrowsable) == BROWSABLE)
                        .build();
                mChannels.put(channel.getId(), channel);
                if (mCursor.getInt(mIndexBrowsable) == BROWSABLE) {
                    ++mBrowsableChannelCount;
                    if (firstBrowsableChannelId == Channel.INVALID_ID) {
                        firstBrowsableChannelId = mCursor.getLong(mIndexId);
                    }
                }
            } while (mCursor.moveToNext());

            if (mCurrentChannelId == Channel.INVALID_ID || !moveToChannel(mCurrentChannelId)) {
                if (firstBrowsableChannelId == Channel.INVALID_ID) {
                    // If there's no browsable channel, we assume that all the channels are
                    // browsable.
                    mCursor.moveToFirst();
                } else {
                    moveToChannel(firstBrowsableChannelId);
                }
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

    private String getInputId() {
        return mCursor.getString(mIndexInputId);
    }

    private void checkCursor() {
        if (mCursor == null) {
            throw new IllegalStateException("Cursor not loaded");
        }
    }
}

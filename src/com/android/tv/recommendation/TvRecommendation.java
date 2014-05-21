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

package com.android.tv.recommendation;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.TvContract;

import com.android.tv.data.Channel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TvRecommendation {
    private static final long MIN_WATCH_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private static final UriMatcher sUriMatcher;
    private static final int MATCH_CHANNEL_ID = 1;
    private static final int MATCH_WATCHED_PROGRAM_ID = 2;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(TvContract.AUTHORITY, "channel/#", MATCH_CHANNEL_ID);
        sUriMatcher.addURI(TvContract.AUTHORITY, "watched_program/#", MATCH_WATCHED_PROGRAM_ID);
    }

    private final Map<Long, ChannelRecord> mChannelRecordMap;
    // TODO: Consider to define each observer rather than the list or observers.
    private final List<ContentObserver> mContentObservers;
    private final Handler mHandler;
    private final Context mContext;

    /**
     * Create a TV recommendation object.
     *
     * @param context The context to register {@link ContentObserver}s for
     * {@link android.provider.TvContract.Channels} and
     * {@link android.provider.TvContract.WatchedPrograms}.
     * @param handler The handler to run {@link android.database.ContentObserver#onChange(boolean)}
     * on, or null if none.
     */
    public TvRecommendation(Context context, Handler handler) {
        mContext = context;
        mChannelRecordMap = new ConcurrentHashMap<Long, ChannelRecord>();
        mContentObservers = new ArrayList<ContentObserver>();
        mHandler = handler;
        registerContentObservers();
        buildChannelRecordMap();
    }

    public void release() {
        unregisterContentObservers();
        mChannelRecordMap.clear();
    }

    /**
     * Get the channel list of recommendation up to {@code n} or the number of channels.
     *
     * @param n The number of channels will be recommended.
     * @return Top {@code n} channels recommended. If {@code n} is bigger than the number of
     * channels, the number of results could be less than {@code n}.
     */
    public ChannelRecord[] getRecommendedChannelList(int n) {
        if (n > mChannelRecordMap.size()) {
            n = mChannelRecordMap.size();
        }
        ChannelRecord[] allChannelRecords =
                mChannelRecordMap.values().toArray(new ChannelRecord[0]);
        Arrays.sort(allChannelRecords, new Comparator<ChannelRecord>() {
            @Override
            public int compare(ChannelRecord c1, ChannelRecord c2) {
                long diff = c1.getLastWatchedTimeMs() - c2.getLastWatchedTimeMs();
                return (diff == 0l) ? 0 : (diff < 0) ? 1 : -1;
            }
        });
        return Arrays.copyOfRange(allChannelRecords, 0, n);
    }

    private void registerContentObservers() {
        ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (sUriMatcher.match(uri) == MATCH_WATCHED_PROGRAM_ID) {
                    String[] projection = {
                            TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS,
                            TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS,
                            TvContract.WatchedPrograms.COLUMN_CHANNEL_ID };

                    Cursor cursor = null;
                    try {
                        cursor = mContext.getContentResolver().query(
                                uri, projection, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            updateLastWatchedTimeFromWatchedProgramCursor(cursor);
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            }
        };
        mContentObservers.add(observer);
        mContext.getContentResolver().registerContentObserver(
                TvContract.WatchedPrograms.CONTENT_URI, true, observer);

        observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (sUriMatcher.match(uri) == MATCH_CHANNEL_ID) {
                    long channelId = ContentUris.parseId(uri);
                    Cursor cursor = null;
                    try {
                        cursor = mContext.getContentResolver().query(
                                uri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            ChannelRecord oldChannelRecord = mChannelRecordMap.get(channelId);
                            ChannelRecord newChannelRecord =
                                    new ChannelRecord(Channel.fromCursor(cursor));
                            newChannelRecord.setLastWatchedTime(oldChannelRecord == null
                                    ? 0 : oldChannelRecord.getLastWatchedTimeMs());
                            mChannelRecordMap.put(channelId, newChannelRecord);
                        } else {
                            mChannelRecordMap.remove(channelId);
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            }
        };
        mContentObservers.add(observer);
        mContext.getContentResolver().registerContentObserver(
                TvContract.Channels.CONTENT_URI, true, observer);
    }

    private void unregisterContentObservers() {
        for (ContentObserver observer : mContentObservers) {
            mContext.getContentResolver().unregisterContentObserver(observer);
        }
        mContentObservers.clear();
    }

    private void buildChannelRecordMap() {
        // register channels into channel map.
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                    TvContract.Channels.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                int indexId = cursor.getColumnIndex(TvContract.Channels._ID);
                while (cursor.moveToNext()) {
                    mChannelRecordMap.put(cursor.getLong(indexId),
                            new ChannelRecord(Channel.fromCursor(cursor)));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        // update last watched time for channels.
        String[] projection = {
                TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS,
                TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS,
                TvContract.WatchedPrograms.COLUMN_CHANNEL_ID };

        try {
            cursor = mContext.getContentResolver().query(
                    TvContract.WatchedPrograms.CONTENT_URI, projection, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    updateLastWatchedTimeFromWatchedProgramCursor(cursor);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void updateLastWatchedTimeFromWatchedProgramCursor(Cursor cursor) {
        final int indexWatchStartTime = cursor.getColumnIndex(
                TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS);
        final int indexWatchEndTime = cursor.getColumnIndex(
                TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS);
        final int indexWatchChannelId = cursor.getColumnIndex(
                TvContract.WatchedPrograms.COLUMN_CHANNEL_ID);

        long watchEndTimeMs = cursor.getLong(indexWatchEndTime);
        long watchDurationMs = watchEndTimeMs - cursor.getLong(indexWatchStartTime);
        if (watchEndTimeMs != 0l && watchDurationMs > MIN_WATCH_DURATION_MS) {
            ChannelRecord channelRecord = mChannelRecordMap.get(
                    cursor.getLong(indexWatchChannelId));
            if (channelRecord != null && channelRecord.getLastWatchedTimeMs() < watchEndTimeMs) {
                channelRecord.setLastWatchedTime(watchEndTimeMs);
            }
        }
    }

    public static class ChannelRecord {
        private final Channel mChannel;
        private long mLastWatchedTimeMs;

        public ChannelRecord(Channel channel) {
            mChannel = channel;
            mLastWatchedTimeMs = 0l;
        }

        public Channel getChannel() {
            return mChannel;
        }

        public long getLastWatchedTimeMs() {
            return mLastWatchedTimeMs;
        }

        public void setLastWatchedTime(long timeMs) {
            mLastWatchedTimeMs = timeMs;
        }
    }
}

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
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.android.tv.data.Channel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TvRecommendation {
    private static final String TAG = "TvRecommendation";

    private static final UriMatcher sUriMatcher;
    private static final int MATCH_CHANNEL_ID = 1;
    private static final int MATCH_WATCHED_PROGRAM_ID = 2;

    private static final List<TvRecommenderWrapper> sTvRecommenders =
            new ArrayList<TvRecommenderWrapper>();

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(TvContract.AUTHORITY, "channel/#", MATCH_CHANNEL_ID);
        sUriMatcher.addURI(TvContract.AUTHORITY, "watched_program/#", MATCH_WATCHED_PROGRAM_ID);

        sTvRecommenders.add(new TvRecommenderWrapper(new RecentChannelRecommender()));
    }

    private final Map<Long, ChannelRecord> mChannelRecordMap;
    // TODO: Consider to define each observer rather than the list or observers.
    private final List<ContentObserver> mContentObservers;
    private final Handler mHandler;
    private final Context mContext;
    private final boolean mIncludeRecommendedOnly;

    /**
     * Create a TV recommendation object.
     *
     * @param context The context to register {@link ContentObserver}s for
     * {@link android.provider.TvContract.Channels} and
     * {@link android.provider.TvContract.WatchedPrograms}.
     * @param handler The handler to run {@link android.database.ContentObserver#onChange(boolean)}
     * on, or null if none.
     * @paran includeRecommendedOnly true to include only recommended results, or false.
     */
    public TvRecommendation(Context context, Handler handler, boolean includeRecommendedOnly) {
        mContext = context;
        mChannelRecordMap = new ConcurrentHashMap<Long, ChannelRecord>();
        mContentObservers = new ArrayList<ContentObserver>();
        mHandler = handler;
        mIncludeRecommendedOnly = includeRecommendedOnly;
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
     * @param size The number of channels that might be recommended.
     * @return Top {@code size} channels recommended. If {@code size} is bigger than the number of
     * channels, the number of results could be less than {@code size}.
     */
    // TODO: consider to change the return type from ChannelRecord[] to Channel[]
    public ChannelRecord[] getRecommendedChannelList(int size) {
        ArrayList<ChannelRecord> results = new ArrayList<ChannelRecord>();
        for (ChannelRecord cr : mChannelRecordMap.values()) {
            double maxScore = TvRecommender.NOT_RECOMMENDED;
            for (TvRecommenderWrapper recommender : sTvRecommenders) {
                double score = recommender.calculateScaledScore(cr);
                if (score > maxScore) {
                    maxScore = score;;
                }
            }
            cr.mScore = maxScore;
            if (!mIncludeRecommendedOnly || cr.mScore != TvRecommender.NOT_RECOMMENDED) {
                results.add(cr);
            }
        }
        ChannelRecord[] allChannelRecords = results.toArray(new ChannelRecord[0]);
        if (size > allChannelRecords.length) {
            size = allChannelRecords.length;
        }
        Arrays.sort(allChannelRecords);
        return Arrays.copyOfRange(allChannelRecords, 0, size);
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
                            ChannelRecord channelRecord =
                                    updateChannelRecordFromWatchedProgramCursor(cursor);
                            for (TvRecommenderWrapper recommender : sTvRecommenders) {
                                recommender.onNewWatchLog(channelRecord);
                            }
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
                            newChannelRecord.mLastWatchedTimeMs = (oldChannelRecord == null)
                                    ? 0 : oldChannelRecord.mLastWatchedTimeMs;
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
                    updateChannelRecordFromWatchedProgramCursor(cursor);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private final ChannelRecord updateChannelRecordFromWatchedProgramCursor(Cursor cursor) {
        final int indexWatchStartTime = cursor.getColumnIndex(
                TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS);
        final int indexWatchEndTime = cursor.getColumnIndex(
                TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS);
        final int indexWatchChannelId = cursor.getColumnIndex(
                TvContract.WatchedPrograms.COLUMN_CHANNEL_ID);

        long watchEndTimeMs = cursor.getLong(indexWatchEndTime);
        long watchDurationMs = watchEndTimeMs - cursor.getLong(indexWatchStartTime);
        ChannelRecord channelRecord = null;
        if (watchEndTimeMs != 0l) {
            channelRecord = mChannelRecordMap.get(
                    cursor.getLong(indexWatchChannelId));
            if (channelRecord != null && channelRecord.mLastWatchedTimeMs < watchEndTimeMs) {
                channelRecord.mLastWatchedTimeMs = watchEndTimeMs;
                channelRecord.mLastWatchDurationMs = watchDurationMs;
            }
        }
        return channelRecord;
    }

    public static class ChannelRecord implements Comparable<ChannelRecord> {
        private final Channel mChannel;
        private long mLastWatchedTimeMs;
        private long mLastWatchDurationMs;
        private double mScore;

        public ChannelRecord(Channel channel) {
            mChannel = channel;
            mLastWatchedTimeMs = 0l;
            mLastWatchDurationMs = 0;
        }

        public Channel getChannel() {
            return mChannel;
        }

        public long getLastWatchedTimeMs() {
            return mLastWatchedTimeMs;
        }

        public long getLastWatchDurationMs() {
            return mLastWatchDurationMs;
        }

        public double getRecommendationScore() {
            return mScore;
        }

        @Override
        public int compareTo(ChannelRecord another) {
            // Make Array.sort work in descending order.
            return (mScore == another.mScore) ? 0 : (mScore > another.mScore) ? -1 : 1;
        }
    }

    public static abstract class TvRecommender {
        public static final double NOT_RECOMMENDED = -1.0;

        /**
         * This will be called when a new watch log comes into WatchedPrograms table.
         */
        protected void onNewWatchLog(ChannelRecord channelRecord) {
        }

        /**
         * The implementation should return the calculated score for the given channel record.
         * The return value should be in the range of [0.0, 1.0] or NOT_RECOMMENDED for denoting
         * that it gives up to calculate the score for the channel.
         *
         * @param channelRecord The channel record which will be evaluated by this recommender.
         * @return The recommendation score
         */
        protected abstract double calculateScore(final ChannelRecord cr);
    }

    private static class TvRecommenderWrapper {
        private static final double DEFAULT_BASE_SCORE = 0.0;
        private static final double DEFAULT_WEIGHT = 1.0;

        private final TvRecommender mRecommender;
        // The minimum score of the TvRecommender unless it gives up to provide the score.
        private final double mBaseScore;
        // The weight of the recommender. The return-value of getScore() will be multiplied by
        // this value.
        private final double mWeight;

        public TvRecommenderWrapper(TvRecommender recommender) {
            this(recommender, DEFAULT_BASE_SCORE, DEFAULT_WEIGHT);
        }

        public TvRecommenderWrapper(TvRecommender recommender, double baseScore, double weight) {
            mRecommender = recommender;
            mBaseScore = baseScore;
            mWeight = weight;
        }

        /**
         * This returns the scaled score for the given channel record based on the returned value
         * of calculateScore().
         *
         * @param channelRecord The channel record which will be evaluated by the recommender.
         * @return Returns the scaled score (mBaseScore + score * mWeight) when calculateScore() is
         * in the range of [0.0, 1.0]. If calculateScore() returns NOT_RECOMMENDED or any negative
         * numbers, it returns NOT_RECOMMENDED. If calculateScore() returns more than 1.0, it
         * returns (mBaseScore + mWeight).
         */
        public double calculateScaledScore(final ChannelRecord channelRecord) {
            double score = mRecommender.calculateScore(channelRecord);
            if (score < 0.0) {
                if (score != TvRecommender.NOT_RECOMMENDED) {
                    Log.w(TAG, "Unexpected scroe (" + score + ") from the recommender"
                            + mRecommender);
                }
                // If the recommender gives up to calculate the score, return 0.0
                return TvRecommender.NOT_RECOMMENDED;
            } else if (score > 1.0) {
                Log.w(TAG, "Unexpected scroe (" + score + ") from the recommender"
                        + mRecommender);
                score = 1.0;
            }
            return mBaseScore + score * mWeight;
        }

        public void onNewWatchLog(ChannelRecord channelRecord) {
            mRecommender.onNewWatchLog(channelRecord);
        }
    }
}

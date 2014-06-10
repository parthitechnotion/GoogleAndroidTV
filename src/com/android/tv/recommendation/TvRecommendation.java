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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.android.tv.data.Channel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TvRecommendation {
    private static final String TAG = "TvRecommendation";

    private static final String PATH_INPUT = "input";

    private static final UriMatcher sUriMatcher;
    private static final int MATCH_CHANNEL = 1;
    private static final int MATCH_CHANNEL_ID = 2;
    private static final int MATCH_WATCHED_PROGRAM_ID = 3;
    private static final int MATCH_INPUT_PACKAGE_SERVICE_CHANNEL = 4;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(TvContract.AUTHORITY, "channel", MATCH_CHANNEL);
        sUriMatcher.addURI(TvContract.AUTHORITY, "channel/#", MATCH_CHANNEL_ID);
        sUriMatcher.addURI(TvContract.AUTHORITY, "watched_program/#", MATCH_WATCHED_PROGRAM_ID);
        sUriMatcher.addURI(TvContract.AUTHORITY, "input/*/*/channel",
                MATCH_INPUT_PACKAGE_SERVICE_CHANNEL);
    }

    private final List<TvRecommenderWrapper> mTvRecommenders;
    private Map<Long, ChannelRecord> mChannelRecordMap;
    // TODO: Consider to define each observer rather than the list or observers.
    private final Handler mHandler;
    private final ContentObserver mContentObserver;
    private final Context mContext;
    private final boolean mIncludeRecommendedOnly;

    /**
     * Create a TV recommendation object.
     *
     * @param context The context to register {@link ContentObserver}s for
     * {@link android.media.tv.TvContract.Channels} and
     * {@link android.media.tv.TvContract.WatchedPrograms}.
     * @param handler The handler to run {@link android.database.ContentObserver#onChange(boolean)}
     * on, or null if none.
     * @param includeRecommendedOnly true to include only recommended results, or false.
     */
    public TvRecommendation(Context context, Handler handler, boolean includeRecommendedOnly) {
        mContext = context;
        mChannelRecordMap = new ConcurrentHashMap<Long, ChannelRecord>();
        mHandler = handler;
        mContentObserver = createContentObserver();
        mIncludeRecommendedOnly = includeRecommendedOnly;
        mTvRecommenders = new ArrayList<TvRecommenderWrapper>();
        registerContentObservers();
        buildChannelRecordMap();
    }

    private ContentObserver createContentObserver() {
        return new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                int match = sUriMatcher.match(uri);
                if (match == MATCH_CHANNEL) {
                    Map<Long, ChannelRecord> channelRecordMap =
                            new ConcurrentHashMap<Long, ChannelRecord>();

                    Cursor c = null;
                    try {
                        c = mContext.getContentResolver().query(uri, null, null, null, null);
                        if (c != null) {
                            int channelIdIndex = c.getColumnIndex(Channels._ID);
                            long channelId;
                            while (c.moveToNext()) {
                                channelId = c.getLong(channelIdIndex);
                                ChannelRecord oldChannelRecord = mChannelRecordMap.get(channelId);
                                ChannelRecord newChannelRecord =
                                        new ChannelRecord(mContext, Channel.fromCursor(c));
                                newChannelRecord.mLastWatchedTimeMs = (oldChannelRecord == null)
                                        ? 0 : oldChannelRecord.mLastWatchedTimeMs;
                                channelRecordMap.put(channelId, newChannelRecord);
                            }
                        }
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                    mChannelRecordMap = channelRecordMap;
                } else if (match == MATCH_CHANNEL_ID) {
                    long channelId = ContentUris.parseId(uri);
                    Cursor cursor = null;
                    try {
                        cursor = mContext.getContentResolver().query(
                                uri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            ChannelRecord oldChannelRecord = mChannelRecordMap.get(channelId);
                            ChannelRecord newChannelRecord =
                                    new ChannelRecord(mContext, Channel.fromCursor(cursor));
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
                } else if (match == MATCH_INPUT_PACKAGE_SERVICE_CHANNEL) {
                    String packageName = TvContract.getPackageName(uri);
                    String serviceName = TvContract.getServiceName(uri);

                    Set<Long> channelIdSet = new HashSet<Long>();
                    for (ChannelRecord cr : mChannelRecordMap.values()) {
                        if (serviceName.equals(cr.mChannel.getServiceName())) {
                            channelIdSet.add(cr.mChannel.getId());
                        }
                    }

                    Uri inputUri = TvContract.buildChannelsUriForInput(
                            new ComponentName(packageName, serviceName), false);
                    Cursor c = null;
                    try {
                        c = mContext.getContentResolver().query(inputUri, null, null, null, null);
                        if (c != null) {
                            int channelIdIndex = c.getColumnIndex(Channels._ID);
                            long channelId;
                            while (c.moveToNext()) {
                                channelId = c.getLong(channelIdIndex);
                                ChannelRecord oldChannelRecord = mChannelRecordMap.get(channelId);
                                ChannelRecord newChannelRecord =
                                        new ChannelRecord(mContext, Channel.fromCursor(c));
                                newChannelRecord.mLastWatchedTimeMs = (oldChannelRecord == null)
                                        ? 0 : oldChannelRecord.mLastWatchedTimeMs;
                                mChannelRecordMap.put(channelId, newChannelRecord);
                                channelIdSet.remove(channelId);
                            }
                        }
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }

                    for (Long channelId : channelIdSet) {
                        mChannelRecordMap.remove(channelId);
                    }
                } else if (match == MATCH_WATCHED_PROGRAM_ID) {
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
                            for (TvRecommenderWrapper recommender : mTvRecommenders) {
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
    }

    public void release() {
        unregisterContentObservers();
        mChannelRecordMap.clear();
    }

    public void registerTvRecommender(TvRecommender recommender) {
        registerTvRecommender(recommender,
                TvRecommenderWrapper.DEFAULT_BASE_SCORE, TvRecommenderWrapper.DEFAULT_WEIGHT);
    }

    public void registerTvRecommender(TvRecommender recommender, double baseScore, double weight) {
        mTvRecommenders.add(new TvRecommenderWrapper(recommender, baseScore, weight));
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
            for (TvRecommenderWrapper recommender : mTvRecommenders) {
                double score = recommender.calculateScaledScore(cr);
                if (score > maxScore) {
                    maxScore = score;
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

    public ChannelRecord[] getRecommendedChannelList() {
        return getRecommendedChannelList(mChannelRecordMap.size());
    }

    private void registerContentObservers() {
        mContext.getContentResolver().registerContentObserver(
                TvContract.WatchedPrograms.CONTENT_URI, true, mContentObserver);
        mContext.getContentResolver().registerContentObserver(
                TvContract.Channels.CONTENT_URI, true, mContentObserver);
        mContext.getContentResolver().registerContentObserver(
                new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(TvContract.AUTHORITY).appendPath(PATH_INPUT).build(),
                true, mContentObserver);
    }

    private void unregisterContentObservers() {
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
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
                    ChannelRecord cr = new ChannelRecord(mContext, Channel.fromCursor(cursor));
                    mChannelRecordMap.put(cursor.getLong(indexId), cr);
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

    public static class ChannelRecord
            implements Comparable<ChannelRecord>, Channel.LoadLogoCallback {
        private final Channel mChannel;
        private final Uri mChannelUri;
        private long mLastWatchedTimeMs;
        private long mLastWatchDurationMs;
        private double mScore;

        public ChannelRecord(Context context, Channel channel) {
            mChannel = channel;
            mChannelUri = ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI,
                    channel.getId());
            mLastWatchedTimeMs = 0l;
            mLastWatchDurationMs = 0;
            mChannel.loadLogo(context, this);
        }

        public Channel getChannel() {
            return mChannel;
        }

        public Uri getChannelUri() {
            return mChannelUri;
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

        @Override
        public void onLoadLogoFinished(Channel channel, Bitmap logo) {
            // do nothing
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
         * @param cr The channel record which will be evaluated by this recommender.
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

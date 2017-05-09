/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.data.epg;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.location.Address;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvContract.Programs.Genres;
import android.media.tv.TvInputInfo;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.TvApplication;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ChannelLogoFetcher;
import com.android.tv.data.InternalDataUtils;
import com.android.tv.data.Lineup;
import com.android.tv.data.Program;
import com.android.tv.tuner.util.PostalCodeUtils;
import com.android.tv.util.LocationUtils;
import com.android.tv.util.RecurringRunner;
import com.android.tv.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * An utility class to fetch the EPG. This class isn't thread-safe.
 */
public class EpgFetcher {
    private static final String TAG = "EpgFetcher";
    private static final boolean DEBUG = false;

    private static final int MSG_FETCH_EPG = 1;
    private static final int MSG_FAST_FETCH_EPG = 2;

    private static final long EPG_PREFETCH_RECURRING_PERIOD_MS = TimeUnit.HOURS.toMillis(4);
    private static final long EPG_READER_INIT_WAIT_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long LOCATION_INIT_WAIT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long LOCATION_ERROR_WAIT_MS = TimeUnit.HOURS.toMillis(1);
    private static final long NO_INFO_FETCHED_WAIT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long PROGRAM_QUERY_DURATION = TimeUnit.DAYS.toMillis(30);

    private static final long PROGRAM_FETCH_SHORT_DURATION_SEC = TimeUnit.HOURS.toSeconds(3);
    private static final long PROGRAM_FETCH_LONG_DURATION_SEC = TimeUnit.DAYS.toSeconds(2)
            + EPG_PREFETCH_RECURRING_PERIOD_MS / 1000;

    // This equals log2(EPG_PREFETCH_RECURRING_PERIOD_MS / NO_INFO_FETCHED_WAIT_MS + 1),
    // since we will double waiting time every other trial, therefore this limit the maximum
    // waiting time less than half of EPG_PREFETCH_RECURRING_PERIOD_MS.
    private static final int NO_INFO_RETRY_LIMIT = 31 - Integer.numberOfLeadingZeros(
            (int) (EPG_PREFETCH_RECURRING_PERIOD_MS / NO_INFO_FETCHED_WAIT_MS + 1));

    private static final int BATCH_OPERATION_COUNT = 100;
    private static final int QUERY_CHANNEL_COUNT = 50;

    private static final String SUPPORTED_COUNTRY_CODE = Locale.US.getCountry();
    private static final String CONTENT_RATING_SEPARATOR = ",";

    // Value: Long
    private static final String KEY_LAST_UPDATED_EPG_TIMESTAMP =
            "com.android.tv.data.epg.EpgFetcher.LastUpdatedEpgTimestamp";
    // Value: String
    private static final String KEY_LAST_LINEUP_ID =
            "com.android.tv.data.epg.EpgFetcher.LastLineupId";

    private static EpgFetcher sInstance;

    private final Context mContext;
    private final ChannelDataManager mChannelDataManager;
    private final EpgReader mEpgReader;
    private EpgFetcherHandler mHandler;
    private RecurringRunner mRecurringRunner;
    private boolean mStarted;
    private boolean mScanningChannels;
    private int mFetchRetryCount;

    private long mLastEpgTimestamp = -1;
    // @GuardedBy("this")
    private String mLineupId;

    public static synchronized EpgFetcher getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new EpgFetcher(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Creates and returns {@link EpgReader}.
     */
    public static EpgReader createEpgReader(Context context) {
        return new StubEpgReader(context);
    }

    private EpgFetcher(Context context) {
        mContext = context;
        mEpgReader = new StubEpgReader(mContext);
        mChannelDataManager = TvApplication.getSingletons(context).getChannelDataManager();
        mChannelDataManager.addListener(new ChannelDataManager.Listener() {
            @Override
            public void onLoadFinished() {
                if (DEBUG) Log.d(TAG, "ChannelDataManager.onLoadFinished()");
                if (!mScanningChannels) {
                    handleChannelChanged();
                }
            }

            @Override
            public void onChannelListUpdated() {
                if (DEBUG) Log.d(TAG, "ChannelDataManager.onChannelListUpdated()");
                if (!mScanningChannels) {
                    handleChannelChanged();
                }
            }

            @Override
            public void onChannelBrowsableChanged() {
                if (DEBUG) Log.d(TAG, "ChannelDataManager.onChannelBrowsableChanged()");
                if (!mScanningChannels) {
                    handleChannelChanged();
                }
            }
        });
        // Warm up to get address, because the first call of getCurrentAddress is usually failed.
        try {
            LocationUtils.getCurrentAddress(mContext);
        } catch (SecurityException | IOException e) {
            // Do nothing
        }
    }

    private void handleChannelChanged() {
        if (mStarted) {
            if (needToStop()) {
                stop();
            }
        } else {
            if (canStart()) {
                start();
            }
        }
    }

    private boolean needToStop() {
        return !canStart();
    }

    private boolean canStart() {
        if (DEBUG) Log.d(TAG, "canStart()");
        boolean hasInternalTunerChannel = false;
        for (TvInputInfo input : TvApplication.getSingletons(mContext).getTvInputManagerHelper()
                .getTvInputInfos(true, true)) {
            String inputId = input.getId();
            if (Utils.isInternalTvInput(mContext, inputId)
                    && mChannelDataManager.getChannelCountForInput(inputId) > 0) {
                hasInternalTunerChannel = true;
                break;
            }
        }
        if (!hasInternalTunerChannel) {
            if (DEBUG) Log.d(TAG, "No internal tuner channels.");
            return false;
        }

        if (!TextUtils.isEmpty(getLastLineupId())) {
            return true;
        }
        if (!TextUtils.isEmpty(PostalCodeUtils.getLastPostalCode(mContext))) {
            return true;
        }
        try {
            Address address = LocationUtils.getCurrentAddress(mContext);
            if (address != null
                    && !TextUtils.equals(address.getCountryCode(), SUPPORTED_COUNTRY_CODE)) {
                Log.i(TAG, "Country not supported: " + address.getCountryCode());
                return false;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to get the current location", e);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "IO Exception when getting the current location", e);
        }
        return true;
    }

    /**
     * Starts fetching EPG.
     *
     * @param resetNextRunTime if true, next run time is reset, so EPG will be fetched
     *                        {@link #EPG_PREFETCH_RECURRING_PERIOD_MS} later.
     *                        otherwise, EPG is fetched when this method is called.
     */
    @MainThread
    private void startInternal(boolean resetNextRunTime) {
        if (DEBUG) Log.d(TAG, "start()");
        if (mStarted) {
            if (DEBUG) Log.d(TAG, "EpgFetcher thread already started.");
            return;
        }
        if (!canStart()) {
            return;
        }
        mStarted = true;
        if (DEBUG) Log.d(TAG, "Starting EpgFetcher thread.");
        HandlerThread handlerThread = new HandlerThread("EpgFetcher");
        handlerThread.start();
        mHandler = new EpgFetcherHandler(handlerThread.getLooper(), this);
        mRecurringRunner = new RecurringRunner(mContext, EPG_PREFETCH_RECURRING_PERIOD_MS,
                new EpgRunner(), null);
        mRecurringRunner.start(resetNextRunTime);
        if (DEBUG) Log.d(TAG, "EpgFetcher thread started successfully.");
    }

    @MainThread
    public void start() {
        if (System.currentTimeMillis() - getLastUpdatedEpgTimestamp() >
                EPG_PREFETCH_RECURRING_PERIOD_MS) {
            startImmediately(false);
        } else {
            startInternal(false);
        }
    }

    /**
     * Starts fetching EPG immediately if possible without waiting for the timer.
     *
     * @param clearStoredLineupId if true, stored lineup id will be clear before fetching EPG.
     */
    @MainThread
    public void startImmediately(boolean clearStoredLineupId) {
        startInternal(true);
        if (mStarted) {
            if (clearStoredLineupId) {
                if (DEBUG) Log.d(TAG, "Clear stored lineup id: " + mLineupId);
                setLastLineupId(null);
            }
            if (DEBUG) Log.d(TAG, "Starting fetcher immediately");
            postFetchRequest(true, 0);
        }
    }

    /**
     * Stops fetching EPG.
     */
    @MainThread
    public void stop() {
        if (DEBUG) Log.d(TAG, "stop()");
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mRecurringRunner.stop();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.getLooper().quit();
    }

    /**
     * Notifies EPG fetcher that channel scanning is started.
     */
    @MainThread
    public void onChannelScanStarted() {
        stop();
        mScanningChannels = true;
    }

    /**
     * Notifies EPG fetcher that channel scanning is finished.
     */
    @MainThread
    public void onChannelScanFinished() {
        mScanningChannels = false;
        start();
    }

    private void postFetchRequest(boolean fastFetch, long delay) {
        int msg = fastFetch ? MSG_FAST_FETCH_EPG : MSG_FETCH_EPG;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessageDelayed(msg, delay);
    }

    private void onFetchEpg() {
        onFetchEpg(false);
    }

    private void onFetchEpg(boolean fastFetch) {
        if (DEBUG) Log.d(TAG, "Start fetching EPG.");
        if (!mEpgReader.isAvailable()) {
            Log.i(TAG, "EPG reader is not temporarily available.");
            postFetchRequest(fastFetch, EPG_READER_INIT_WAIT_MS);
            return;
        }
        String lineupId = getLastLineupId();
        if (lineupId == null) {
            try {
                PostalCodeUtils.updatePostalCode(mContext);
            } catch (IOException e) {
                if (TextUtils.isEmpty(PostalCodeUtils.getLastPostalCode(mContext))) {
                    if (DEBUG) Log.d(TAG, "Couldn't get the current location.", e);
                    postFetchRequest(fastFetch, LOCATION_ERROR_WAIT_MS);
                    return;
                }
            } catch (SecurityException e) {
                if (TextUtils.isEmpty(PostalCodeUtils.getLastPostalCode(mContext))) {
                    Log.w(TAG, "No permission to get the current location.");
                    return;
                }
            } catch (PostalCodeUtils.NoPostalCodeException e) {
                Log.i(TAG, "Failed to get the current postal code.");
                postFetchRequest(fastFetch, LOCATION_INIT_WAIT_MS);
                return;
            }
            String postalCode = PostalCodeUtils.getLastPostalCode(mContext);
            if (DEBUG) Log.d(TAG, "The current postal code is " + postalCode);

            lineupId = pickLineupForPostalCode(postalCode);
            if (lineupId != null) {
                Log.i(TAG, "Selecting the lineup " + lineupId);
                setLastLineupId(lineupId);
            } else {
                Log.i(TAG, "Failed to get lineup id");
                retryFetchEpg(fastFetch);
                return;
            }
        }

        // Check the EPG Timestamp.
        long epgTimestamp = mEpgReader.getEpgTimestamp();
        if (epgTimestamp <= getLastUpdatedEpgTimestamp()) {
            if (DEBUG) Log.d(TAG, "No new EPG.");
            return;
        }

        List<Channel> channels = mEpgReader.getChannels(lineupId);
        if (channels.isEmpty()) {
            Log.i(TAG, "Failed to get EPG channels.");
            retryFetchEpg(fastFetch);
            return;
        }
        mFetchRetryCount = 0;
        if (!fastFetch) {
            for (Channel channel : channels) {
                if (!mStarted) {
                    break;
                }
                List<Program> programs = new ArrayList<>(mEpgReader.getPrograms(channel.getId()));
                Collections.sort(programs);
                Log.i(TAG, "Fetched " + programs.size() + " programs for channel " + channel);
                updateEpg(channel.getId(), programs);
            }
            setLastUpdatedEpgTimestamp(epgTimestamp);
        } else {
            handleFastFetch(channels, PROGRAM_FETCH_SHORT_DURATION_SEC);
            if (DEBUG) Log.d(TAG, "First fast fetch Done.");
            handleFastFetch(channels, PROGRAM_FETCH_LONG_DURATION_SEC);
            if (DEBUG) Log.d(TAG, "Second fast fetch Done.");
        }

        if (!fastFetch) {
            mHandler.removeMessages(MSG_FETCH_EPG);
        }
        if (DEBUG) Log.d(TAG, "Fetching EPG is finished.");
        // Start to fetch channel logos after epg fetching finished.
        ChannelLogoFetcher.startFetchingChannelLogos(mContext, channels);
    }

    private void retryFetchEpg(boolean fastFetch) {
        if (mFetchRetryCount < NO_INFO_RETRY_LIMIT) {
            postFetchRequest(fastFetch, NO_INFO_FETCHED_WAIT_MS * 1 << mFetchRetryCount);
            mFetchRetryCount++;
        } else {
            mFetchRetryCount = 0;
        }
    }

    private void handleFastFetch(List<Channel> channels, long duration) {
        List<Long> channelIds = new ArrayList<>(channels.size());
        for (Channel channel : channels) {
            channelIds.add(channel.getId());
        }
        Map<Long, List<Program>> allPrograms = new HashMap<>();
        List<Long> queryChannelIds = new ArrayList<>(QUERY_CHANNEL_COUNT);
        for (Long channelId : channelIds) {
            queryChannelIds.add(channelId);
            if (queryChannelIds.size() >= QUERY_CHANNEL_COUNT) {
                allPrograms.putAll(
                        new HashMap<>(mEpgReader.getPrograms(queryChannelIds, duration)));
                queryChannelIds.clear();
            }
        }
        if (!queryChannelIds.isEmpty()) {
            allPrograms.putAll(
                    new HashMap<>(mEpgReader.getPrograms(queryChannelIds, duration)));
        }
        for (Channel channel : channels) {
            List<Program> programs = allPrograms.get(channel.getId());
            if (programs == null) continue;
            Collections.sort(programs);
            Log.i(TAG, "Fast fetched " + programs.size() + " programs for channel " + channel);
            updateEpg(channel.getId(), programs);
        }
    }

    @Nullable
    private String pickLineupForPostalCode(String postalCode) {
        List<Lineup> lineups = mEpgReader.getLineups(postalCode);
        int maxCount = 0;
        String maxLineupId = null;
        for (Lineup lineup : lineups) {
            int count = getMatchedChannelCount(lineup.id);
            Log.i(TAG, lineup.name + " ("  + lineup.id + ") - " + count + " matches");
            if (count > maxCount) {
                maxCount = count;
                maxLineupId = lineup.id;
            }
        }
        return maxLineupId;
    }

    private int getMatchedChannelCount(String lineupId) {
        // Construct a list of display numbers for existing channels.
        List<Channel> channels = mChannelDataManager.getChannelList();
        if (channels.isEmpty()) {
            if (DEBUG) Log.d(TAG, "No existing channel to compare");
            return 0;
        }
        List<String> numbers = new ArrayList<>(channels.size());
        for (Channel c : channels) {
            // We only support local channels from physical tuners.
            if (c.isPhysicalTunerChannel()) {
                numbers.add(c.getDisplayNumber());
            }
        }

        numbers.retainAll(mEpgReader.getChannelNumbers(lineupId));
        return numbers.size();
    }

    private long getLastUpdatedEpgTimestamp() {
        if (mLastEpgTimestamp < 0) {
            mLastEpgTimestamp = PreferenceManager.getDefaultSharedPreferences(mContext).getLong(
                    KEY_LAST_UPDATED_EPG_TIMESTAMP, 0);
        }
        return mLastEpgTimestamp;
    }

    private void setLastUpdatedEpgTimestamp(long timestamp) {
        mLastEpgTimestamp = timestamp;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putLong(
                KEY_LAST_UPDATED_EPG_TIMESTAMP, timestamp).commit();
    }

    synchronized private String getLastLineupId() {
        if (mLineupId == null) {
            mLineupId = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getString(KEY_LAST_LINEUP_ID, null);
        }
        if (DEBUG) Log.d(TAG, "Last lineup is " + mLineupId);
        return mLineupId;
    }

    synchronized private void setLastLineupId(String lineupId) {
        mLineupId = lineupId;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(KEY_LAST_LINEUP_ID, lineupId).commit();
    }

    private boolean updateEpg(long channelId, List<Program> newPrograms) {
        final int fetchedProgramsCount = newPrograms.size();
        if (fetchedProgramsCount == 0) {
            return false;
        }
        boolean updated = false;
        long startTimeMs = System.currentTimeMillis();
        long endTimeMs = startTimeMs + PROGRAM_QUERY_DURATION;
        List<Program> oldPrograms = queryPrograms(channelId, startTimeMs, endTimeMs);
        int oldProgramsIndex = 0;
        int newProgramsIndex = 0;

        // Compare the new programs with old programs one by one and update/delete the old one
        // or insert new program if there is no matching program in the database.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        while (newProgramsIndex < fetchedProgramsCount) {
            // TODO: Extract to method and make test.
            Program oldProgram = oldProgramsIndex < oldPrograms.size()
                    ? oldPrograms.get(oldProgramsIndex) : null;
            Program newProgram = newPrograms.get(newProgramsIndex);
            boolean addNewProgram = false;
            if (oldProgram != null) {
                if (oldProgram.equals(newProgram)) {
                    // Exact match. No need to update. Move on to the next programs.
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (isSameTitleAndOverlap(oldProgram, newProgram)) {
                    // Partial match. Update the old program with the new one.
                    // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                    // could be application specific settings which belong to the old program.
                    ops.add(ContentProviderOperation.newUpdate(
                            TvContract.buildProgramUri(oldProgram.getId()))
                            .withValues(toContentValues(newProgram))
                            .build());
                    oldProgramsIndex++;
                    newProgramsIndex++;
                } else if (oldProgram.getEndTimeUtcMillis()
                        < newProgram.getEndTimeUtcMillis()) {
                    // No match. Remove the old program first to see if the next program in
                    // {@code oldPrograms} partially matches the new program.
                    ops.add(ContentProviderOperation.newDelete(
                            TvContract.buildProgramUri(oldProgram.getId()))
                            .build());
                    oldProgramsIndex++;
                } else {
                    // No match. The new program does not match any of the old programs. Insert
                    // it as a new program.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
            } else {
                // No old programs. Just insert new programs.
                addNewProgram = true;
                newProgramsIndex++;
            }
            if (addNewProgram) {
                ops.add(ContentProviderOperation
                        .newInsert(Programs.CONTENT_URI)
                        .withValues(toContentValues(newProgram))
                        .build());
            }
            // Throttle the batch operation not to cause TransactionTooLargeException.
            if (ops.size() > BATCH_OPERATION_COUNT || newProgramsIndex >= fetchedProgramsCount) {
                try {
                    if (DEBUG) {
                        int size = ops.size();
                        Log.d(TAG, "Running " + size + " operations for channel " + channelId);
                        for (int i = 0; i < size; ++i) {
                            Log.d(TAG, "Operation(" + i + "): " + ops.get(i));
                        }
                    }
                    mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                    updated = true;
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to insert programs.", e);
                    return updated;
                }
                ops.clear();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Updated " + fetchedProgramsCount + " programs for channel " + channelId);
        }
        return updated;
    }

    private List<Program> queryPrograms(long channelId, long startTimeMs, long endTimeMs) {
        try (Cursor c = mContext.getContentResolver().query(
                TvContract.buildProgramsUriForChannel(channelId, startTimeMs, endTimeMs),
                Program.PROJECTION, null, null, Programs.COLUMN_START_TIME_UTC_MILLIS)) {
            if (c == null) {
                return Collections.emptyList();
            }
            ArrayList<Program> programs = new ArrayList<>();
            while (c.moveToNext()) {
                programs.add(Program.fromCursor(c));
            }
            return programs;
        }
    }

    /**
     * Returns {@code true} if the {@code oldProgram} program needs to be updated with the
     * {@code newProgram} program.
     */
    private boolean isSameTitleAndOverlap(Program oldProgram, Program newProgram) {
        // NOTE: Here, we update the old program if it has the same title and overlaps with the
        // new program. The test logic is just an example and you can modify this. E.g. check
        // whether the both programs have the same program ID if your EPG supports any ID for
        // the programs.
        return Objects.equals(oldProgram.getTitle(), newProgram.getTitle())
                && oldProgram.getStartTimeUtcMillis() <= newProgram.getEndTimeUtcMillis()
                && newProgram.getStartTimeUtcMillis() <= oldProgram.getEndTimeUtcMillis();
    }

    @SuppressLint("InlinedApi")
    @SuppressWarnings("deprecation")
    private static ContentValues toContentValues(Program program) {
        ContentValues values = new ContentValues();
        values.put(Programs.COLUMN_CHANNEL_ID, program.getChannelId());
        putValue(values, Programs.COLUMN_TITLE, program.getTitle());
        putValue(values, Programs.COLUMN_EPISODE_TITLE, program.getEpisodeTitle());
        if (BuildCompat.isAtLeastN()) {
            putValue(values, Programs.COLUMN_SEASON_DISPLAY_NUMBER, program.getSeasonNumber());
            putValue(values, Programs.COLUMN_EPISODE_DISPLAY_NUMBER, program.getEpisodeNumber());
        } else {
            putValue(values, Programs.COLUMN_SEASON_NUMBER, program.getSeasonNumber());
            putValue(values, Programs.COLUMN_EPISODE_NUMBER, program.getEpisodeNumber());
        }
        putValue(values, Programs.COLUMN_SHORT_DESCRIPTION, program.getDescription());
        putValue(values, Programs.COLUMN_LONG_DESCRIPTION, program.getLongDescription());
        putValue(values, Programs.COLUMN_POSTER_ART_URI, program.getPosterArtUri());
        putValue(values, Programs.COLUMN_THUMBNAIL_URI, program.getThumbnailUri());
        String[] canonicalGenres = program.getCanonicalGenres();
        if (canonicalGenres != null && canonicalGenres.length > 0) {
            putValue(values, Programs.COLUMN_CANONICAL_GENRE, Genres.encode(canonicalGenres));
        } else {
            putValue(values, Programs.COLUMN_CANONICAL_GENRE, "");
        }
        TvContentRating[] ratings = program.getContentRatings();
        if (ratings != null && ratings.length > 0) {
            StringBuilder sb = new StringBuilder(ratings[0].flattenToString());
            for (int i = 1; i < ratings.length; ++i) {
                sb.append(CONTENT_RATING_SEPARATOR);
                sb.append(ratings[i].flattenToString());
            }
            putValue(values, Programs.COLUMN_CONTENT_RATING, sb.toString());
        } else {
            putValue(values, Programs.COLUMN_CONTENT_RATING, "");
        }
        values.put(Programs.COLUMN_START_TIME_UTC_MILLIS, program.getStartTimeUtcMillis());
        values.put(Programs.COLUMN_END_TIME_UTC_MILLIS, program.getEndTimeUtcMillis());
        putValue(values, Programs.COLUMN_INTERNAL_PROVIDER_DATA,
                InternalDataUtils.serializeInternalProviderData(program));
        return values;
    }

    private static void putValue(ContentValues contentValues, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            contentValues.putNull(key);
        } else {
            contentValues.put(key, value);
        }
    }

    private static void putValue(ContentValues contentValues, String key, byte[] value) {
        if (value == null || value.length == 0) {
            contentValues.putNull(key);
        } else {
            contentValues.put(key, value);
        }
    }

    private static class EpgFetcherHandler extends WeakHandler<EpgFetcher> {
        public EpgFetcherHandler (@NonNull Looper looper, EpgFetcher ref) {
            super(looper, ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull EpgFetcher epgFetcher) {
            switch (msg.what) {
                case MSG_FETCH_EPG:
                    epgFetcher.onFetchEpg();
                    break;
                case MSG_FAST_FETCH_EPG:
                    epgFetcher.onFetchEpg(true);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private class EpgRunner implements Runnable {
        @Override
        public void run() {
            postFetchRequest(false, 0);
        }
    }
}

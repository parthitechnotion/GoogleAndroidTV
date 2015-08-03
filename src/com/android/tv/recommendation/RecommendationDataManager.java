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

package com.android.tv.recommendation;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.TvInputCallback;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RecommendationDataManager {
    private static final String TAG = "RecommendationDataManager";

    private static final UriMatcher sUriMatcher;
    private static final int MATCH_CHANNEL = 1;
    private static final int MATCH_CHANNEL_ID = 2;
    private static final int MATCH_WATCHED_PROGRAM_ID = 3;
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(TvContract.AUTHORITY, "channel", MATCH_CHANNEL);
        sUriMatcher.addURI(TvContract.AUTHORITY, "channel/#", MATCH_CHANNEL_ID);
        sUriMatcher.addURI(TvContract.AUTHORITY, "watched_program/#", MATCH_WATCHED_PROGRAM_ID);
    }

    private static final int MSG_START = 1000;
    private static final int MSG_STOP = 1001;
    private static final int MSG_UPDATE_CHANNEL = 1002;
    private static final int MSG_UPDATE_CHANNELS = 1003;
    private static final int MSG_UPDATE_WATCH_HISTORY = 1004;
    private static final int MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED = 1005;
    private static final int MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED = 1006;

    private static final int MSG_FIRST = MSG_START;
    private static final int MSG_LAST = MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED;

    private static final int INVALID_INDEX = -1;

    private static RecommendationDataManager sManager;
    private final ContentObserver mContentObserver;
    private final Map<Long, ChannelRecord> mChannelRecordMap = new ConcurrentHashMap<>();
    private final Map<Long, ChannelRecord> mAvailableChannelRecordMap = new ConcurrentHashMap<>();

    private Context mContext;
    private boolean mStarted;
    private boolean mCancelLoadTask;
    private boolean mChannelRecordMapLoaded;
    private int mIndexWatchChannelId = -1;
    private int mIndexProgramTitle = -1;
    private int mIndexProgramStartTime = -1;
    private int mIndexProgramEndTime = -1;
    private int mIndexWatchStartTime = -1;
    private int mIndexWatchEndTime = -1;
    private TvInputManager mTvInputManager;
    private final Set<String> mInputs = new HashSet<>();

    private final HandlerThread mHandlerThread;

    @SuppressWarnings("unchecked")
    private final Handler mHandler;

    private final List<ListenerRecord> mListeners = new ArrayList<>();

    /**
     * Gets instance of RecommendationDataManager, and adds a {@link Listener}.
     * The listener methods will be called in the same thread as its caller of the method.
     * Note that {@link #release(Listener)} should be called when this manager is not needed
     * any more.
     */
    public synchronized static RecommendationDataManager acquireManager(
            Context context, @NonNull Listener listener) {
        if (sManager == null) {
            sManager = new RecommendationDataManager(context);
        }
        sManager.addListener(listener);
        sManager.start();
        return sManager;
    }

    /**
     * Removes the {@link Listener}, and releases RecommendationDataManager
     * if there are no listeners remained.
     */
    public void release(@NonNull Listener listener) {
        removeListener(listener);
        synchronized (mListeners) {
            if (mListeners.size() == 0) {
                stop();
            }
        }
    }
    private final TvInputCallback mInternalCallback =
            new TvInputCallback() {
                @Override
                public void onInputStateChanged(String inputId, int state) { }

                @Override
                public void onInputAdded(String inputId) {
                    if (!mStarted) {
                        return;
                    }
                    mInputs.add(inputId);
                    if (!mChannelRecordMapLoaded) {
                        return;
                    }
                    boolean channelRecordMapChanged = false;
                    for (ChannelRecord channelRecord : mChannelRecordMap.values()) {
                        if (channelRecord.getChannel().getInputId().equals(inputId)) {
                            channelRecord.setInputRemoved(false);
                            mAvailableChannelRecordMap.put(channelRecord.getChannel().getId(),
                                    channelRecord);
                            channelRecordMapChanged = true;
                        }
                    }
                    if (channelRecordMapChanged
                            && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
                        mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
                    }
                }

                @Override
                public void onInputRemoved(String inputId) {
                    if (!mStarted) {
                        return;
                    }
                    mInputs.remove(inputId);
                    if (!mChannelRecordMapLoaded) {
                        return;
                    }
                    boolean channelRecordMapChanged = false;
                    for (ChannelRecord channelRecord : mChannelRecordMap.values()) {
                        if (channelRecord.getChannel().getInputId().equals(inputId)) {
                            channelRecord.setInputRemoved(true);
                            mAvailableChannelRecordMap.remove(channelRecord.getChannel().getId());
                            channelRecordMapChanged = true;
                        }
                    }
                    if (channelRecordMapChanged
                            && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
                        mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
                    }
                }

                @Override
                public void onInputUpdated(String inputId) { }
            };

    private RecommendationDataManager(Context context) {
        mContext = context.getApplicationContext();
        mHandlerThread = new HandlerThread("RecommendationDataManager");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_START:
                        onStart();
                        break;
                    case MSG_STOP:
                        if (mStarted) {
                            onStop();
                        }
                        break;
                    case MSG_UPDATE_CHANNEL:
                        if (mStarted) {
                            onUpdateChannel((Uri) msg.obj);
                        }
                        break;
                    case MSG_UPDATE_CHANNELS:
                        if (mStarted) {
                            onUpdateChannels((Uri) msg.obj);
                        }
                        break;
                    case MSG_UPDATE_WATCH_HISTORY:
                        if (mStarted) {
                            onLoadWatchHistory((Uri) msg.obj);
                        }
                        break;
                    case MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED:
                        if (mStarted) {
                            onNotifyChannelRecordMapLoaded();
                        }
                        break;
                    case MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED:
                        if (mStarted) {
                            onNotifyChannelRecordMapChanged();
                        }
                        break;
                }
            }
        };
        mContentObserver = new RecommendationContentObserver(mHandler);
    }

    /**
     * Returns a {@link ChannelRecord} corresponds to the channel ID {@code ChannelId}.
     */
    public ChannelRecord getChannelRecord(long channelId) {
        return mAvailableChannelRecordMap.get(channelId);
    }

    /**
     * Returns the number of channels registered in ChannelRecord map.
     */
    public int getChannelRecordCount() {
        return mAvailableChannelRecordMap.size();
    }

    /**
     * Returns a Collection of ChannelRecords.
     */
    public Collection<ChannelRecord> getChannelRecords() {
        return Collections.unmodifiableCollection(mAvailableChannelRecordMap.values());
    }

    private void start() {
        mHandler.sendEmptyMessage(MSG_START);
    }

    private void stop() {
        for (int what = MSG_FIRST; what <= MSG_LAST; ++what) {
            mHandler.removeMessages(what);
        }
        mHandler.sendEmptyMessage(MSG_STOP);
        mHandlerThread.quitSafely();
        sManager = null;
    }

    private int getListenerIndexLocked(Listener listener) {
        for (int i = 0; i < mListeners.size(); ++i) {
            if (mListeners.get(i).mListener == listener) {
                return i;
            }
        }
        return INVALID_INDEX;
    }

    private void addListener(Listener listener) {
        synchronized (mListeners) {
            if (getListenerIndexLocked(listener) == INVALID_INDEX) {
                mListeners.add((new ListenerRecord(listener)));
            }
        }
    }

    private void removeListener(Listener listener) {
        synchronized (mListeners) {
            int idx = getListenerIndexLocked(listener);
            if (idx != INVALID_INDEX) {
                mListeners.remove(idx);
            }
        }
    }

    private void onStart() {
        if (!mStarted) {
            mStarted = true;
            mCancelLoadTask = false;
            mContext.getContentResolver().registerContentObserver(
                    TvContract.Channels.CONTENT_URI, true, mContentObserver);
            mContext.getContentResolver().registerContentObserver(
                    TvContract.WatchedPrograms.CONTENT_URI, true, mContentObserver);
            mHandler.obtainMessage(MSG_UPDATE_CHANNELS, TvContract.Channels.CONTENT_URI)
                    .sendToTarget();
            mHandler.obtainMessage(MSG_UPDATE_WATCH_HISTORY, TvContract.WatchedPrograms.CONTENT_URI)
                    .sendToTarget();
            mTvInputManager = (TvInputManager) mContext.getSystemService(Context.TV_INPUT_SERVICE);
            mTvInputManager.registerCallback(mInternalCallback, mHandler);
            for (TvInputInfo input : mTvInputManager.getTvInputList()) {
                mInputs.add(input.getId());
            }
        }
        if (mChannelRecordMapLoaded) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED);
        }
    }

    private void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        mCancelLoadTask = true;
        mChannelRecordMap.clear();
        mAvailableChannelRecordMap.clear();
        mInputs.clear();
        mTvInputManager.unregisterCallback(mInternalCallback);
        mStarted = false;
    }

    private void onUpdateChannel(Uri uri) {
        Channel channel = null;
        try (Cursor cursor = mContext.getContentResolver().query(uri, Channel.PROJECTION,
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                    channel = Channel.fromCursor(cursor);
            }
        }
        boolean isChannelRecordMapChanged = false;
        if (channel == null) {
            long channelId = ContentUris.parseId(uri);
            mChannelRecordMap.remove(channelId);
            isChannelRecordMapChanged = mAvailableChannelRecordMap.remove(channelId) != null;
        } else if (updateChannelRecordMapFromChannel(channel)) {
            isChannelRecordMapChanged = true;
        }
        if (isChannelRecordMapChanged && mChannelRecordMapLoaded
                && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
        }
    }

    private void onUpdateChannels(Uri uri) {
        List<Channel> channels = new ArrayList<>();
        try (Cursor cursor = mContext.getContentResolver().query(uri, Channel.PROJECTION,
                null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (mCancelLoadTask) {
                        return;
                    }
                    channels.add(Channel.fromCursor(cursor));
                }
            }
        }
        boolean isChannelRecordMapChanged = false;
        Set<Long> removedChannelIdSet = new HashSet<>(mChannelRecordMap.keySet());
        // Builds removedChannelIdSet.
        for (Channel channel : channels) {
            if (updateChannelRecordMapFromChannel(channel)) {
                isChannelRecordMapChanged = true;
            }
            removedChannelIdSet.remove(channel.getId());
        }

        if (!removedChannelIdSet.isEmpty()) {
            for (Long channelId : removedChannelIdSet) {
                mChannelRecordMap.remove(channelId);
                if (mAvailableChannelRecordMap.remove(channelId) != null) {
                    isChannelRecordMapChanged = true;
                }
            }
        }
        if (isChannelRecordMapChanged && mChannelRecordMapLoaded
                && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
        }
    }

    private void onLoadWatchHistory(Uri uri) {
        List<WatchedProgram> history = new ArrayList<>();
        try (Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToLast()) {
                do {
                    if (mCancelLoadTask) {
                        return;
                    }
                    history.add(createWatchedProgramFromWatchedProgramCursor(cursor));
                } while (cursor.moveToPrevious());
            }
        }
        for (WatchedProgram watchedProgram : history) {
            final ChannelRecord channelRecord =
                    updateChannelRecordFromWatchedProgram(watchedProgram);
            if (mChannelRecordMapLoaded && channelRecord != null) {
                synchronized (mListeners) {
                    for (ListenerRecord l : mListeners) {
                        l.postNewWatchLog(channelRecord);
                    }
                }
            }
        }
        if (!mChannelRecordMapLoaded) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED);
        }
    }

    private WatchedProgram createWatchedProgramFromWatchedProgramCursor(Cursor cursor) {
        // Have to initiate the indexes of WatchedProgram Columns.
        if (mIndexWatchChannelId == -1) {
            mIndexWatchChannelId = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_CHANNEL_ID);
            mIndexProgramTitle = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_TITLE);
            mIndexProgramStartTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_START_TIME_UTC_MILLIS);
            mIndexProgramEndTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_END_TIME_UTC_MILLIS);
            mIndexWatchStartTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS);
            mIndexWatchEndTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS);
        }

        Program program = new Program.Builder()
                .setChannelId(cursor.getLong(mIndexWatchChannelId))
                .setTitle(cursor.getString(mIndexProgramTitle))
                .setStartTimeUtcMillis(cursor.getLong(mIndexProgramStartTime))
                .setEndTimeUtcMillis(cursor.getLong(mIndexProgramEndTime))
                .build();

        return new WatchedProgram(program,
                cursor.getLong(mIndexWatchStartTime),
                cursor.getLong(mIndexWatchEndTime));
    }

    private void onNotifyChannelRecordMapLoaded() {
        mChannelRecordMapLoaded = true;
        synchronized (mListeners) {
            for (ListenerRecord l : mListeners) {
                l.postChannelRecordLoaded();
            }
        }
    }

    private void onNotifyChannelRecordMapChanged() {
        synchronized (mListeners) {
            for (ListenerRecord l : mListeners) {
                l.postChannelRecordChanged();
            }
        }
    }

    /**
     * Returns true if ChannelRecords are added into mChannelRecordMap or removed from it.
     */
    private boolean updateChannelRecordMapFromChannel(Channel channel) {
        if (!channel.isBrowsable()) {
            mChannelRecordMap.remove(channel.getId());
            return mAvailableChannelRecordMap.remove(channel.getId()) != null;
        }
        ChannelRecord channelRecord = mChannelRecordMap.get(channel.getId());
        boolean inputRemoved = !mInputs.contains(channel.getInputId());
        if (channelRecord == null) {
            ChannelRecord record = new ChannelRecord(mContext, channel, inputRemoved);
            mChannelRecordMap.put(channel.getId(), record);
            if (!inputRemoved) {
                mAvailableChannelRecordMap.put(channel.getId(), record);
                return true;
            }
            return false;
        }
        boolean oldInputRemoved = channelRecord.isInputRemoved();
        channelRecord.setChannel(channel, inputRemoved);
        return oldInputRemoved != inputRemoved;
    }

    private ChannelRecord updateChannelRecordFromWatchedProgram(WatchedProgram program) {
        ChannelRecord channelRecord = null;
        if (program != null && program.getWatchEndTimeMs() != 0l) {
            channelRecord = mChannelRecordMap.get(program.getProgram().getChannelId());
            if (channelRecord != null
                    && channelRecord.getLastWatchEndTimeMs() < program.getWatchEndTimeMs()) {
                channelRecord.logWatchHistory(program);
            }
        }
        return channelRecord;
    }

    private class RecommendationContentObserver extends ContentObserver {
        public RecommendationContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(final boolean selfChange, final Uri uri) {
            switch (sUriMatcher.match(uri)) {
                case MATCH_CHANNEL:
                    if (!mHandler.hasMessages(MSG_UPDATE_CHANNELS, TvContract.Channels.CONTENT_URI)) {
                        mHandler.obtainMessage(MSG_UPDATE_CHANNELS, TvContract.Channels.CONTENT_URI)
                                .sendToTarget();
                    }
                    break;
                case MATCH_CHANNEL_ID:
                    mHandler.obtainMessage(MSG_UPDATE_CHANNEL, uri).sendToTarget();
                    break;
                case MATCH_WATCHED_PROGRAM_ID:
                    if (!mHandler.hasMessages(MSG_UPDATE_WATCH_HISTORY,
                            TvContract.WatchedPrograms.CONTENT_URI)) {
                        mHandler.obtainMessage(MSG_UPDATE_WATCH_HISTORY, uri).sendToTarget();
                    }
                    break;
            }
        }
    }

    /**
     * A listener interface to receive notification about the recommendation data.
     */
    public interface Listener {
        /**
         * Called when loading channel record map from database is finished.
         * It will be called after RecommendationDataManager.start() is finished.
         */
        void onChannelRecordLoaded();

        /**
         * Called when a new watch log is added into the corresponding channelRecord.
         *
         * @param channelRecord The channel record corresponds to the new watch log.
         */
        void onNewWatchLog(ChannelRecord channelRecord);

        /**
         * Called when the channel record map changes.
         */
        void onChannelRecordChanged();
    }

    private static class ListenerRecord {
        private Listener mListener;
        private final Handler mHandler;

        public ListenerRecord(Listener listener) {
            mHandler = new Handler();
            mListener = listener;
        }

        public void postChannelRecordLoaded() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onChannelRecordLoaded();
                    }
                }
            });
        }

        public void postNewWatchLog(final ChannelRecord channelRecord) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onNewWatchLog(channelRecord);
                    }
                }
            });
        }

        public void postChannelRecordChanged() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onChannelRecordChanged();
                    }
                }
            });
        }
    }
}

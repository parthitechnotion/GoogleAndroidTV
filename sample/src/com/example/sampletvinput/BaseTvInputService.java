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

package com.example.sampletvinput;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.KeyEvent;
import android.view.Surface;

import java.util.List;

abstract public class BaseTvInputService extends TvInputService {
    private static final String TAG = "BaseTvInputService";
    private static final boolean DEBUG = true;

    private final LongSparseArray<String> mChannelMap = new LongSparseArray<String>();
    private final Handler mProgramUpdateHandler = new Handler();

    // mNumberOfChannels and mSamples will be set in createSampleChannels().
    protected int mNumberOfChannels;
    protected List<ChannelInfo> mChannels;

    private boolean mAvailable = true;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate();

        buildChannelMap();
        setAvailable(mAvailable);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public TvInputSessionImpl onCreateSession() {
        if (DEBUG) Log.d(TAG, "onCreateSession()");
        return new BaseTvInputSessionImpl();
    }

    abstract public List<ChannelInfo> createSampleChannels();

    abstract public boolean setDataSource(MediaPlayer player, String channelNumber);

    private void buildChannelMap() {
        Uri uri = TvContract.buildChannelsUriForInput(new ComponentName(this, this.getClass()),
                false);
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER
        };
        Cursor cursor = null;
        try {
            do {
                cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    break;
                }
                if (DEBUG) Log.d(TAG, "Couldn't find the channel list. Inserting new channels...");
                // Insert channels into the database. This needs to be done only for the first time.
                ContentValues values = new ContentValues();
                values.put(Channels.COLUMN_SERVICE_NAME, this.getClass().getName());
                for (ChannelInfo info : createSampleChannels()) {
                    values.put(Channels.COLUMN_DISPLAY_NUMBER, info.getNumber());
                    values.put(Channels.COLUMN_DISPLAY_NAME, info.getName());
                    getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
                }
            } while (true);

            while (cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                String channelNumber = cursor.getString(1);
                if (DEBUG) Log.d(TAG, "Channel mapping: ID(" + channelId + ") -> " + channelNumber);
                mChannelMap.put(channelId, channelNumber);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getChannelNumber(Uri channelUri) {
        String channelNumber = mChannelMap.get(ContentUris.parseId(channelUri));
        if (channelNumber == null) {
            throw new IllegalArgumentException("Unknown channel: " + channelUri);
        }
        return channelNumber;
    }

    class BaseTvInputSessionImpl extends TvInputSessionImpl {
        private MediaPlayer mPlayer;
        private float mVolume;
        private boolean mMute;

        protected BaseTvInputSessionImpl() {
            mPlayer = new MediaPlayer();
            mVolume = 1.0f;
            mMute = false;
        }

        @Override
        public void onRelease() {
            if (mPlayer != null) {
                mPlayer.release();
                mPlayer = null;
            }
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (DEBUG) Log.d(TAG, "onSetSurface(" + surface + ")");
            mPlayer.setSurface(surface);
            return true;
        }

        @Override
        public void onSetVolume(float volume) {
            if (DEBUG) Log.d(TAG, "onSetVolume(" + volume + ")");
            mVolume = volume;
            mPlayer.setVolume(volume, volume);
        }

        @Override
        public boolean onTune(Uri channelUri) {
            if (DEBUG) Log.d(TAG, "tune(" + channelUri + ")");

            mPlayer.reset();
            if (!setDataSource(mPlayer, getChannelNumber(channelUri))) {
                if (DEBUG) Log.d(TAG, "Failed to set the data source");
                return false;
            }
            try {
                mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer player) {
                        if (mPlayer != null && !mPlayer.isPlaying()) {
                            mPlayer.start();
                        }
                    }
                });
                mPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    @Override
                    public void onVideoSizeChanged(MediaPlayer player, int width, int height) {
                        if (mPlayer != null) {
                            dispatchVideoSizeChanged(width, height);
                        }
                    }
                });
                mPlayer.prepareAsync();
            } catch (IllegalStateException e1) {
                return false;
            }
            mPlayer.start();

            try {
                // Delete existing program information of the channel.
                Uri uri = TvContract.buildProgramsUriForChannel(channelUri);
                getContentResolver().delete(uri, null, null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Fail to get id from uri: " + channelUri);
                getContentResolver().delete(TvContract.Programs.CONTENT_URI, null, null);
            }

            // Create empty program information and insert it into the database.
            // Delay intentionally to see whether the updated program information dynamically
            // replaces the previous one on the channel banner (for testing). This is to simulate
            // the actual case where we get parsed program data only after tuning is done.
            final long DELAY_FOR_TESTING_IN_MILLIS = 1000; // 1 second
            mProgramUpdateHandler.postDelayed(
                    new AddProgramRunnable(channelUri, "Program Title", "Program Description"),
                    DELAY_FOR_TESTING_IN_MILLIS);
            return true;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_M) {
                mMute = !mMute;
                if (mMute) {
                    mPlayer.setVolume(0.0f, 0.0f);
                } else {
                    mPlayer.setVolume(mVolume, mVolume);
                }
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_A) {
                // It simulates availability changes such as HDMI cable plug-off/plug-in.
                // The availability is toggled whenever 'a' key is dispatched from a TV app.
                mAvailable = !mAvailable;
                setAvailable(mAvailable);
                return true;
            }
            return false;
        }

        private class AddProgramRunnable implements Runnable {
            private static final int DEFAULT_PROGRAM_DURATION_IN_MILLIS = 30000; // 5 minutes
            private final Uri mChannelUri;
            private final String mTitle;
            private final String mDescription;

            public AddProgramRunnable(Uri channelUri, String title, String description) {
                mChannelUri = channelUri;
                mTitle = title;
                mDescription = description;
            }

            @Override
            public void run() {
                int duration = -1;
                if (mPlayer != null) {
                    duration = mPlayer.getDuration();
                }
                if (duration == -1) {
                    duration = DEFAULT_PROGRAM_DURATION_IN_MILLIS;
                }
                long time = System.currentTimeMillis();
                ContentValues values = new ContentValues();
                values.put(Programs.COLUMN_CHANNEL_ID, ContentUris.parseId(mChannelUri));
                values.put(Programs.COLUMN_TITLE, mTitle);
                values.put(Programs.COLUMN_SHORT_DESCRIPTION, mDescription);
                values.put(Programs.COLUMN_START_TIME_UTC_MILLIS, time);
                values.put(Programs.COLUMN_END_TIME_UTC_MILLIS, time + duration);
                getContentResolver().insert(TvContract.Programs.CONTENT_URI, values);
            }
        }
    }

    static final class ChannelInfo {
        private final String mNumber;
        private final String mName;

        public ChannelInfo(String number, String name) {
            mNumber = number;
            mName = name;
        }

        public String getNumber() {
            return mNumber;
        }

        public String getName() {
            return mName;
        }
    }
}

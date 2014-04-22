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

import android.content.ContentUris;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.provider.TvContract;
import android.tv.TvInputService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;

import com.android.tv.Channel;
import com.android.tv.Program;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

abstract public class BaseLocalTvInputService extends TvInputService {
    private static final String TAG = "SampleTvInputService";

    class BaseLocalTvInputSessionImpl extends TvInputSessionImpl {
        private MediaPlayer mPlayer;
        private float mVolume;
        private boolean mMute;

        protected BaseLocalTvInputSessionImpl() {
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
            Log.d(TAG, "onSetSurface(" + surface + ")");
            mPlayer.setSurface(surface);
            return true;
        }

        @Override
        public void onSetVolume(float volume) {
            Log.d(TAG, "onSetVolume(" + volume + ")");
            mVolume = volume;
            mPlayer.setVolume(volume, volume);
        }

        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
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
            }
            return false;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            Log.d(TAG, "tune(" + channelUri + ")");
            Sample sample = mChannelToSampleMap.get(channelUri);
            if (sample == null) {
                Log.e(TAG, channelUri + " does not exist on the channel map");
                return false;
            }
            AssetFileDescriptor afd = getResources().openRawResourceFd(sample.getResourceId());
            if (afd == null) {
                return false;
            }

            mPlayer.reset();
            try {
                mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                        afd.getDeclaredLength());
                mPlayer.prepare();
                afd.close();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPlayer.setLooping(true);
            mPlayer.start();

            try {
                String[] selectionArgs = new String[] {
                        String.valueOf(ContentUris.parseId(channelUri)) };
                // Delete existing program information of the channel.
                String selection = TvContract.Programs.CHANNEL_ID + " = ?";
                getContentResolver().delete(TvContract.Programs.CONTENT_URI, selection,
                        selectionArgs);
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
                    new AddProgramRunnable(channelUri, sample.getProgramTitle()),
                    DELAY_FOR_TESTING_IN_MILLIS);
            return true;
        }

        private class AddProgramRunnable implements Runnable {
            private static final int DEFAULT_PROGRAM_LENGTH_IN_MILLIS = 5 * 60 * 1000;
            private final Uri mChannelUri;
            private final String mTitle;

            public AddProgramRunnable(Uri channelUri, String title) {
                mChannelUri = channelUri;
                mTitle = title;
            }

            @Override
            public void run() {
                int duration = -1;
                if (mPlayer != null) {
                    duration = mPlayer.getDuration();
                }
                if (duration == -1) {
                    duration = DEFAULT_PROGRAM_LENGTH_IN_MILLIS;
                }
                long currentTimeSec = System.currentTimeMillis() / 1000;
                Program program = new Program.Builder()
                        .setChannelId(ContentUris.parseId(mChannelUri))
                        .setTitle(mTitle)
                        .setStartTimeUtcMillis(currentTimeSec)
                        .setEndTimeUtcMillis(currentTimeSec + duration / 1000)
                        .build();
                getContentResolver().insert(TvContract.Programs.CONTENT_URI,
                        program.toContentValues());
            }
        }
    }

    private final Map<Uri, Sample> mChannelToSampleMap = new HashMap<Uri, Sample>();
    private final Handler mProgramUpdateHandler = new Handler();

    // mNumberOfChannels and mSamples will be set in createSampleChannels().
    protected int mNumberOfChannels;
    protected Sample[] mSamples;

    private boolean mAvailable = true;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        createSampleChannels();
        loadChannelMap();
        setAvailable(mAvailable);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public TvInputSessionImpl onCreateSession() {
        return new BaseLocalTvInputSessionImpl();
    }

    protected void loadChannelMap() {
        String[] projection = {TvContract.Channels._ID};
        String selection = TvContract.Channels.SERVICE_NAME + " = ?";
        String[] selectionArgs = new String[] {
            getServiceName()
        };
        String order = null;
        Cursor cursor = getContentResolver().query(TvContract.Channels.CONTENT_URI, projection,
                selection, selectionArgs, order);

        if (cursor == null || cursor.getCount() < 1) {
            Log.d(TAG, "Couldn't find the channel list. Perform auto-scan.");
            if (cursor != null) {
                cursor.close();
            }
            scan();
            return;
        }

        int index = 0;
        while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            Uri uri = ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, id);
            Log.d(TAG, "Channel mapping " + id + " to " + uri);
            mChannelToSampleMap.put(uri, mSamples[index++ % mSamples.length]);
        }
    }

    // Perform (fake) channel scan and push the result into the database.
    private void scan() {
        // Generate dummy channels.
        for (int i = 1; i < mNumberOfChannels + 1; i++) {
            Channel channel = new Channel.Builder()
                    .setDisplayName("CH-" + i)
                    .setServiceName(getServiceName())
                    .setDisplayNumber(String.valueOf(i))
                    .build();
            Uri uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI,
                    channel.toContentValues());
            mChannelToSampleMap.put(uri, mSamples[(i - 1) % mSamples.length]);
        }
    }

    abstract void createSampleChannels();

    protected static class Sample {
        private final int mResourceId;
        private final String mProgramTitle;

        public Sample(int resourceId, String programTitle) {
            mResourceId = resourceId;
            mProgramTitle = programTitle;
        }

        public int getResourceId() {
            return mResourceId;
        }

        public String getProgramTitle() {
            return mProgramTitle;
        }
    }

    private String getServiceName() {
        return this.getClass().getName();
    }
}

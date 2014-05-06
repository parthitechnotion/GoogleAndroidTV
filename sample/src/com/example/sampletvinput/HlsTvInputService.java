package com.example.sampletvinput;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.provider.TvContract;
import android.provider.TvContract.Channels;
import android.provider.TvContract.Programs;
import android.tv.TvInputService;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HlsTvInputService extends TvInputService {
    private static final String TAG = "HlsTvInputService";
    private static final String SERVICE_NAME = HlsTvInputService.class.getName();

    private NetworkStateReceiver mNetworkStateReceiver;
    private static final String[] mSamples = {
        "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8",
        "http://devimages.apple.com/iphone/samples/bipbop/gear4/prog_index.m3u8",
        "http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8",
        "http://qthttp.apple.com.edgesuite.net/1010qwoeiuryfg/sl.m3u8"
    };
    private final Map<Uri, String> mChannelToSampleMap = new HashMap<Uri, String>();
    private final Handler mProgramUpdateHandler = new Handler();

    private class NetworkStateReceiver extends BroadcastReceiver {
        private final int DURATION_MONITOR = 30000; // 30 seconds
        private boolean mIsConnected;
        private boolean mIsRegistered;
        private final Handler mUnregisterHandler = new Handler();
        private final Runnable mUnregister = new Runnable() {
            @Override
            public void run() {
                unregister();
            }
        };

        public NetworkStateReceiver() {
            // Cache the latest network status.
            update();
        }

        public boolean isConnected() {
            // Now that we know you're interested in, turn on the monitor for some time.
            if (mIsRegistered) {
                mUnregisterHandler.removeCallbacks(mUnregister);
            } else {
                IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
                registerReceiver(this, filter);
                mIsRegistered = true;
            }
            mUnregisterHandler.postDelayed(mUnregister, DURATION_MONITOR);

            update();
            return mIsConnected;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }

        public void unregister() {
            if (mIsRegistered) {
                unregisterReceiver(this);
                mIsRegistered = false;
            }
        }

        private void update() {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            mIsConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            setAvailable(mIsConnected);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        loadChannelMap();
        mNetworkStateReceiver = new NetworkStateReceiver();
        setAvailable(mNetworkStateReceiver.isConnected());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        if (mNetworkStateReceiver != null) {
            mNetworkStateReceiver.unregister();
        }
    }

    @Override
    public TvInputSessionImpl onCreateSession() {
        return new MyTvInputSessionImpl();
    }

    private void loadChannelMap() {
        Uri uri = TvContract.buildChannelsUriForInput(new ComponentName(this, this.getClass()));
        String[] projection = { TvContract.Channels._ID };

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor == null || cursor.getCount() < 1) {
                Log.d(TAG, "Couldn't find the channel list. Perform auto-scan.");
                scan();
                return;
            }

            int index = 0;
            while (cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                Uri channelUri = ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI,
                        channelId);
                Log.d(TAG, "Channel mapping " + channelId + " to " + channelUri);
                mChannelToSampleMap.put(channelUri, mSamples[index++ % mSamples.length]);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // Perform channel scan and push the result into the database.
    private void scan() {
        int index = 0;
        ContentValues values = new ContentValues();
        values.put(Channels.SERVICE_NAME, SERVICE_NAME);
        values.put(Channels.DISPLAY_NUMBER, "1-1");
        values.put(Channels.DISPLAY_NAME, "ABC-SD");
        Uri uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
        mChannelToSampleMap.put(uri, mSamples[index++]);

        values.put(Channels.DISPLAY_NUMBER, "1-2");
        values.put(Channels.DISPLAY_NAME, "ABC-HD");
        uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
        mChannelToSampleMap.put(uri, mSamples[index++]);

        values.put(Channels.DISPLAY_NUMBER, "2-1");
        values.put(Channels.DISPLAY_NAME, "DEF");
        uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
        mChannelToSampleMap.put(uri, mSamples[index++]);

        values.put(Channels.DISPLAY_NUMBER, "3-1");
        values.put(Channels.DISPLAY_NAME, "GHI");
        uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
        mChannelToSampleMap.put(uri, mSamples[index++]);
    }

    private class MyTvInputSessionImpl extends TvInputSessionImpl {
        private MediaPlayer mPlayer;

        protected MyTvInputSessionImpl() {
            mPlayer = new MediaPlayer();
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
            mPlayer.setVolume(volume, volume);
        }

        @Override
        public boolean onTune(Uri channelUri) {
            Log.d(TAG, "onTune(" + channelUri + ")");
            String dataSource = mChannelToSampleMap.get(channelUri);
            if (dataSource == null) {
                Log.e(TAG, channelUri + " does not exist on the channel map");
                return false;
            }

            mPlayer.reset();
            try {
                Log.d(TAG, "Setting data source to " + dataSource);
                mPlayer.setDataSource(dataSource);
                mPlayer.prepare();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPlayer.start();

            // Delete all existing program information.
            getContentResolver().delete(TvContract.Programs.CONTENT_URI, null, null);
            // Create empty program information and insert it into the database.
            // Delay intentionally to see whether the updated program information dynamically replaces
            // the previous one on the channel banner (for testing). This is to simulate the actual case
            // where we get parsed program data only after tuning is done.
            final long DELAY_FOR_TESTING_IN_MILLIS = 3000; // 3 second
            mProgramUpdateHandler.postDelayed(new AddProgramRunnable(channelUri),
                    DELAY_FOR_TESTING_IN_MILLIS);
            return true;
        }

        private class AddProgramRunnable implements Runnable {
            private static final int DEFAULT_PROGRAM_LENGTH_IN_MILLIS = 5 * 60 * 1000;
            private final Uri mChannelUri;

            public AddProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
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
                long time = System.currentTimeMillis();
                ContentValues values = new ContentValues();
                values.put(Programs.CHANNEL_ID, ContentUris.parseId(mChannelUri));
                values.put(Programs.TITLE, "No Program Information.");
                values.put(Programs.START_TIME_UTC_MILLIS, time);
                values.put(Programs.END_TIME_UTC_MILLIS, time + duration);
                getContentResolver().insert(TvContract.Programs.CONTENT_URI, values);
            }
        }
    }
}

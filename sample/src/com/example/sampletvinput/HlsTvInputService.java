package com.example.sampletvinput;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HlsTvInputService extends BaseTvInputService {
    private static final String CHANNEL_1_NUMBER = "2-1";
    private static final String CHANNEL_2_NUMBER = "2-2";
    private static final String CHANNEL_3_NUMBER = "3-1";
    private static final String CHANNEL_4_NUMBER = "4-1";
    private static final String CHANNEL_1_NAME = "NTSC(SD)";
    private static final String CHANNEL_2_NAME = "NTSC(HD)";
    private static final String CHANNEL_3_NAME = "BUNNY";
    private static final String CHANNEL_4_NAME = "APPLE";
    private static final String SOURCE_1 =
            "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8";
    private static final String SOURCE_2 =
            "http://devimages.apple.com/iphone/samples/bipbop/gear4/prog_index.m3u8";
    private static final String SOURCE_3 =
            "http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8";
    private static final String SOURCE_4 =
            "http://qthttp.apple.com.edgesuite.net/1010qwoeiuryfg/sl.m3u8";

    private NetworkStateReceiver mNetworkStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mNetworkStateReceiver = new NetworkStateReceiver();
        setAvailable(mNetworkStateReceiver.isConnected());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNetworkStateReceiver != null) {
            mNetworkStateReceiver.unregister();
        }
    }

    @Override
    public List<ChannelInfo> createSampleChannels() {
        List<ChannelInfo> list = new ArrayList<ChannelInfo>();
        list.add(new ChannelInfo(CHANNEL_1_NUMBER, CHANNEL_1_NAME));
        list.add(new ChannelInfo(CHANNEL_2_NUMBER, CHANNEL_2_NAME));
        list.add(new ChannelInfo(CHANNEL_3_NUMBER, CHANNEL_3_NAME));
        list.add(new ChannelInfo(CHANNEL_4_NUMBER, CHANNEL_4_NAME));
        return list;
    }

    @Override
    public boolean setDataSource(MediaPlayer player, String channelNumber) {
        String dataSource;
        if (CHANNEL_1_NUMBER.equals(channelNumber)) {
            dataSource = SOURCE_1;
        } else if (CHANNEL_2_NUMBER.equals(channelNumber)) {
            dataSource = SOURCE_2;
        } else if (CHANNEL_3_NUMBER.equals(channelNumber)) {
            dataSource = SOURCE_3;
        } else if (CHANNEL_4_NUMBER.equals(channelNumber)) {
            dataSource = SOURCE_4;
        } else {
            throw new IllegalArgumentException("Unknown channel number: " + channelNumber);
        }

        try {
            player.setDataSource(dataSource);
        } catch (IllegalArgumentException | SecurityException | IllegalStateException
                | IOException e) {
            return false;
        }
        return true;
    }

    private class NetworkStateReceiver extends BroadcastReceiver {
        private static final int DURATION_MONITOR = 30000; // 30 seconds

        private final Handler mUnregisterHandler = new Handler();

        private final Runnable mUnregister = new Runnable() {
            @Override
            public void run() {
                unregister();
            }
        };

        private boolean mIsConnected;
        private boolean mIsRegistered;

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
}

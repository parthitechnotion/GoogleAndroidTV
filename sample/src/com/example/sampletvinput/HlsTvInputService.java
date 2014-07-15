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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class HlsTvInputService extends BaseTvInputService {
    private static final String TAG = "HlsTvInputService";

    private static List<ChannelInfo> sSampleChannels = null;

    private NetworkStateReceiver mNetworkStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mNetworkStateReceiver = new NetworkStateReceiver();
        // TODO: Uncomment or remove when a new API design is locked down.
        // setAvailable(mNetworkStateReceiver.isConnected());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNetworkStateReceiver != null) {
            mNetworkStateReceiver.unregister();
        }
    }

    public static List<ChannelInfo> createSampleChannelsStatic(Context context) {
        synchronized (HlsTvInputService.class) {
            if (sSampleChannels != null) {
                return sSampleChannels;
            }
            sSampleChannels = new ArrayList<ChannelInfo>();
            try {
                InputStream is = context.getResources().openRawResource(R.raw.hls_channels);
                sSampleChannels = ChannelXMLParser.parseChannelXML(is);
            } catch (XmlPullParserException | IOException e) {
                // TODO: Disable this service.
                Log.w(TAG, "failed to load channels.");
            }
            return sSampleChannels;
        }
    }

    @Override
    public List<ChannelInfo> createSampleChannels() {
        return createSampleChannelsStatic(this);
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
            // TODO: Uncomment or remove when a new API design is locked down.
            // setAvailable(mIsConnected);
        }
    }
}

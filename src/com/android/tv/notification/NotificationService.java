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

package com.android.tv.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.data.Program;
import com.android.tv.recommendation.RecentChannelRecommender;
import com.android.tv.recommendation.TvRecommendation;
import com.android.tv.recommendation.TvRecommendation.ChannelRecord;
import com.android.tv.util.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A local service for notify recommendation at home launcher.
 */
public class NotificationService extends Service {
    public static final String TAG = "NotificationService";
    public static final String ACTION_SHOW_RECOMMENDATION =
            "com.android.tv.notification.ACTION_SHOW_RECOMMENDATION";
    public static final String ACTION_HIDE_RECOMMENDATION =
            "com.android.tv.notification.ACTION_HIDE_RECOMMENDATION";

    private static final String NOTIFY_TAG = "tv_recommendation";
    // TODO: find out proper number of notifications and whether to make it dynamically
    // configurable from system property or etc.
    private static final int NOTIFICATION_COUNT = 1;

    private static final int MSG_SHOW_RECOMMENDATION = 0;
    private static final int MSG_UPDATE_RECOMMENDATION = 1;
    private static final int MSG_HIDE_RECOMMENDATION = 2;

    private TvRecommendation mTvRecommendation;
    private NotificationManager mNotificationManager;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    public NotificationService() {
        mHandlerThread = new HandlerThread("tv notification");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SHOW_RECOMMENDATION: {
                        showRecommendation();
                        break;
                    }
                    case MSG_UPDATE_RECOMMENDATION: {
                        sendNotification((ChannelRecord) msg.obj, msg.arg1);
                        break;
                    }
                    case MSG_HIDE_RECOMMENDATION: {
                        removeMessages(MSG_UPDATE_RECOMMENDATION);
                        hideRecommendation();
                        break;
                    }
                    default: {
                        super.handleMessage(msg);
                    }
                }
            }
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mTvRecommendation = new TvRecommendation(this, mHandler, true);
        // TODO: implement proper recommenders and register them.
        mTvRecommendation.registerTvRecommender(new RecentChannelRecommender());
        mNotificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SHOW_RECOMMENDATION.equals(action)) {
                mHandler.obtainMessage(MSG_SHOW_RECOMMENDATION).sendToTarget();;
            } else if (ACTION_HIDE_RECOMMENDATION.equals(action)) {
                mHandler.obtainMessage(MSG_HIDE_RECOMMENDATION).sendToTarget();;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showRecommendation() {
        // TODO: Since we are not displaying the program which doesn't have its poster art image,
        // consider to get many recommendation results so that we can increase the chance to show
        // the recommendation.
        ChannelRecord[] channelRecords =
                mTvRecommendation.getRecommendedChannelList(NOTIFICATION_COUNT);
        for (int i = 0; i < channelRecords.length; ++i) {
            sendNotification(channelRecords[i], i);
        }
    }

    private void hideRecommendation() {
        for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
            mNotificationManager.cancel(NOTIFY_TAG, i);
        }
    }

    private void sendNotification(ChannelRecord cr, int notifyId) {
        mNotificationManager.cancel(NOTIFY_TAG, notifyId);
        Intent intent = new Intent(Intent.ACTION_VIEW, cr.getChannelUri());
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Program program = Utils.getCurrentProgram(this, cr.getChannelUri());
        String largeIconUriStr = program.getPosterArtUri();
        if (!TextUtils.isEmpty(largeIconUriStr)) {
            InputStream is = null;
            try {
                is = new URL(largeIconUriStr).openStream();
            } catch (MalformedURLException e) {
                try {
                    is = getContentResolver().openInputStream(Uri.parse(largeIconUriStr));
                } catch (FileNotFoundException ex) {
                    Log.i(TAG, "Unable to load uri: " + largeIconUriStr);
                }
            } catch (IOException e) {
                Log.i(TAG, "Failed to open stream: " + largeIconUriStr);
            }
            if (is != null) {
                Bitmap largeIconBitmap = BitmapFactory.decodeStream(is);
                if (largeIconBitmap == null) {
                    Log.e(TAG, "Failed to decode logo image for " + largeIconUriStr);
                } else {
                    Notification notification = new Notification.Builder(this)
                            .setContentIntent(notificationIntent)
                            .setContentTitle(program.getTitle())
                            .setContentText(program.getDescription())
                            .setAutoCancel(true)
                            .setLargeIcon(largeIconBitmap)
                            .setSmallIcon(R.drawable.app_icon)
                            .setCategory(Notification.CATEGORY_RECOMMENDATION)
                            .build();
                    // TODO: Set background image.
                    //notification.extras.putString(
                    //        Notification.EXTRA_BACKGROUND_IMAGE_URI, largeIconUriStr);
                    mNotificationManager.notify(NOTIFY_TAG, notifyId, notification);
                }
            }
        }
        Message msg = mHandler.obtainMessage(MSG_UPDATE_RECOMMENDATION, notifyId, 0, cr);
        mHandler.sendMessageDelayed(
                msg, program.getEndTimeUtcMillis() - System.currentTimeMillis());
    }
}

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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.android.tv.R;
import com.android.tv.data.Program;
import com.android.tv.recommendation.RecentChannelRecommender;
import com.android.tv.recommendation.TvRecommendation;
import com.android.tv.recommendation.TvRecommendation.ChannelRecord;
import com.android.tv.util.Utils;

public class NotificationService extends IntentService {
    private static final String TAG = NotificationService.class.getName();
    private static final String NOTIFY_TAG = "tv_recommendation";
    // TODO: find out proper number of notifications and whether to make it dynamically
    // configurable from system property or etc.
    private static final int NOTIFICATION_COUNT = 2;

    private TvRecommendation mTvRecommendation;
    private NotificationManager mNotificationManager;
    private final Handler mHandler;

    public NotificationService() {
        super("TV Notification");
        mHandler = new Handler();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mTvRecommendation = new TvRecommendation(this, mHandler, true);
        mTvRecommendation.registerTvRecommender(new RecentChannelRecommender());
        mNotificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        ChannelRecord[] channelRecords =
                mTvRecommendation.getRecommendedChannelList(NOTIFICATION_COUNT);
        for (ChannelRecord cr : channelRecords) {
            sendNotification(cr);
        }
    }

    public void sendNotification(ChannelRecord cr) {
        Intent intent = new Intent(Intent.ACTION_VIEW, cr.getChannelUri());
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Program program = Utils.getCurrentProgram(this, cr.getChannelUri());
        // TODO: provide large icon.
        Notification notification = new Notification.Builder(this)
                .setContentIntent(notificationIntent)
                .setContentTitle(program.getTitle())
                .setContentText(program.getDescription())
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.app_icon)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .build();
        mNotificationManager.notify(NOTIFY_TAG, (int) cr.getChannel().getId(), notification);
    }
}

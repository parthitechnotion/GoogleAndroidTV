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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/*
 * Notification Broadcast Receiver
 * Start notification service
 */
public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = NotificationReceiver.class.getName();
    private static final int SERVICE_REQUEST_CODE = 7151940;  // a random number
    private static final int ONE_SECOND_MILLIS = 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        startAutoLoadAlarm(context);
    }

    public static void startAutoLoadAlarm(Context context) {
        Intent notificationService = new Intent(context, NotificationService.class);

        // FLAG_ACTIVITY_BROUGHT_TO_FRONT is singleTask launch mode.
        notificationService.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // FLAG_UPDATE_CURRENT flag indicates that if the described
        // PendingIntent already exists, then keep it but replace its extra
        // data with what is in this new intent.
        PendingIntent pendingIntent = PendingIntent.getService(context, SERVICE_REQUEST_CODE,
                notificationService, PendingIntent.FLAG_UPDATE_CURRENT);

        // The Alarm Manager is intended for cases where we want to have
        // application code run at a specific time, even if the application is
        // not currently running.
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Schedule a repeating alarm.
        // RTC : This alarm does not wake the device up; if it goes off while
        // the device is asleep, it will not be delivered until the next time
        // the device wakes up.
        // System.currentTimeMillis() postpones the time to send notifications.
        // The reason that Notifications wasn't posted immediately is we set up
        // the alarm at boot time, but the network or other stuff were not been
        // set up yet
        // TODO: confirm the update time. Currently update every 15 minutes.
        alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + ONE_SECOND_MILLIS,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);
    }
}

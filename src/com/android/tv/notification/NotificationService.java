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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.data.Program;
import com.android.tv.recommendation.SampleRecommender;
import com.android.tv.recommendation.TvRecommendation;
import com.android.tv.recommendation.TvRecommendation.ChannelRecord;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.BitmapUtils;
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
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationService";

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

    private static final long RECOMMENDATION_RETRY_TIME_MS = 5 * 60 * 1000;  // 5 min
    private static final long RECOMMENDATION_THRESHOLD_LEFT_TIME_MS = 10 * 60 * 1000;  // 10 min
    private static final int RECOMMENDATION_THRESHOLD_PROGRESS = 90;  // 90%
    private static final int MAX_PROGRAM_UPDATE_COUNT = 20;

    private TvInputManager mTvInputManager;
    private TvInputManagerHelper mTvInputManagerHelper;
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
                        removeMessages(MSG_UPDATE_RECOMMENDATION);
                        mTvInputManagerHelper.update();
                        showRecommendation();
                        break;
                    }
                    case MSG_UPDATE_RECOMMENDATION: {
                        mTvInputManagerHelper.update();
                        if (!sendNotification((ChannelRecord) msg.obj, msg.arg1)) {
                            obtainMessage(MSG_HIDE_RECOMMENDATION).sendToTarget();
                            obtainMessage(MSG_SHOW_RECOMMENDATION).sendToTarget();
                        }
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
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate();
        mTvRecommendation = new TvRecommendation(this, mHandler, true);
        // TODO: implement proper recommenders and register them.
        mTvRecommendation.registerTvRecommender(new SampleRecommender(this));
        mNotificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        mTvInputManagerHelper = new TvInputManagerHelper(mTvInputManager);
        mTvInputManagerHelper.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand");
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SHOW_RECOMMENDATION.equals(action)) {
                mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
                mHandler.removeMessages(MSG_HIDE_RECOMMENDATION);
                mHandler.obtainMessage(MSG_SHOW_RECOMMENDATION).sendToTarget();;
            } else if (ACTION_HIDE_RECOMMENDATION.equals(action)) {
                mHandler.removeMessages(MSG_SHOW_RECOMMENDATION);
                mHandler.removeMessages(MSG_HIDE_RECOMMENDATION);
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
        if (DEBUG) Log.d(TAG, "showRecommendation");
        ChannelRecord[] channelRecords = mTvRecommendation.getRecommendedChannelList();
        int notifyId = 0;
        for (ChannelRecord cr : channelRecords) {
            if (sendNotification(cr, notifyId)) {
                ++notifyId;
                if (notifyId >= NOTIFICATION_COUNT) {
                    break;
                }
            }
        }
        if (notifyId < NOTIFICATION_COUNT) {
            Message msg = mHandler.obtainMessage(MSG_SHOW_RECOMMENDATION);
            mHandler.sendMessageDelayed(msg, RECOMMENDATION_RETRY_TIME_MS);
        }
    }

    private void hideRecommendation() {
        if (DEBUG) Log.d(TAG, "hideRecommendation");
        for (int i = 0; i < NOTIFICATION_COUNT; ++i) {
            mNotificationManager.cancel(NOTIFY_TAG, i);
        }
    }

    private boolean sendNotification(ChannelRecord cr, int notifyId) {
        if (DEBUG) Log.d(TAG, "sendNotification (" + cr.getChannel().getDisplayName()
                + " notifyId=" + notifyId + ")");
        Intent intent = new Intent(Intent.ACTION_VIEW, cr.getChannelUri());
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // TODO: Move some checking logic into TvRecommendation.
        String inputId = Utils.getInputIdForChannel(this, cr.getChannelUri());
        if (!mTvInputManagerHelper.isAvailable(inputId)) {
            return false;
        }
        TvInputInfo inputInfo = mTvInputManagerHelper.getTvInputInfo(inputId);
        if (inputInfo == null) {
            return false;
        }
        String inputDisplayName = Utils.getDisplayNameForInput(this, inputInfo);

        Program program = Utils.getCurrentProgram(this, cr.getChannelUri());
        if (program == null) {
            return false;
        }
        long programDurationMs = program.getEndTimeUtcMillis() - program.getStartTimeUtcMillis();
        long programLeftTimsMs = System.currentTimeMillis() - program.getStartTimeUtcMillis();
        int programProgress = (int) (programLeftTimsMs * 100 / programDurationMs);
        String posterArtUriStr = program.getPosterArtUri();
        if (TextUtils.isEmpty(posterArtUriStr)) {
            return false;
        }
        InputStream is = null;
        try {
            is = new URL(posterArtUriStr).openStream();
        } catch (MalformedURLException e) {
            try {
                is = getContentResolver().openInputStream(Uri.parse(posterArtUriStr));
            } catch (FileNotFoundException ex) {
                Log.i(TAG, "Unable to load uri: " + posterArtUriStr);
            }
        } catch (IOException e) {
            Log.i(TAG, "Failed to open stream: " + posterArtUriStr);
        }
        if (is == null) {
            return false;
        }
        Bitmap posterArtBitmap = BitmapFactory.decodeStream(is);
        if (posterArtBitmap == null) {
            Log.e(TAG, "Failed to decode logo image for " + posterArtUriStr);
            return false;
        }
        Bitmap channelLogo = cr.getChannel().getLogo();
        Bitmap largeIconBitmap = (channelLogo == null) ? posterArtBitmap
                : overlayChannelLogo(cr.getChannel().getLogo(), posterArtBitmap);
        Notification notification = new Notification.Builder(this)
                .setContentIntent(notificationIntent)
                .setContentTitle(program.getTitle())
                .setContentText(inputDisplayName + " " + cr.getChannel().getDisplayName())
                .setAutoCancel(true)
                .setLargeIcon(largeIconBitmap)
                .setSmallIcon(R.drawable.ic_launcher_s)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setProgress(100, programProgress, false)
                .build();
        notification.color =
                getResources().getColor(R.color.recommendation_card_background);
        if (!TextUtils.isEmpty(program.getThumbnailUri())) {
            notification.extras.putString(Notification.EXTRA_BACKGROUND_IMAGE_URI,
                    program.getThumbnailUri());
        }
        if (programProgress < RECOMMENDATION_THRESHOLD_PROGRESS
                || programLeftTimsMs > RECOMMENDATION_THRESHOLD_LEFT_TIME_MS) {
            mNotificationManager.notify(NOTIFY_TAG, notifyId, notification);
            Message msg = mHandler.obtainMessage(
                    MSG_UPDATE_RECOMMENDATION, notifyId, 0, cr);
            // TODO: Need to decide we want to update the program progress or not.
            mHandler.sendMessageDelayed(msg, programDurationMs / MAX_PROGRAM_UPDATE_COUNT);
            return true;
        }
        return false;
    }

    private Bitmap overlayChannelLogo(Bitmap logo, Bitmap background) {
        final int NOTIF_CARD_IMG_HEIGHT =
                getResources().getDimensionPixelSize(R.dimen.notif_card_img_height);
        final int NOTIF_CARD_IMG_MAX_WIDTH =
                getResources().getDimensionPixelSize(R.dimen.notif_card_img_max_width);
        final int NOTIF_CARD_IMG_MIN_WIDTH =
                getResources().getDimensionPixelSize(R.dimen.notif_card_img_min_width);
        final int NOTIF_CH_LOGO_MAX_HEIGHT =
                getResources().getDimensionPixelSize(R.dimen.notif_ch_logo_max_height);
        final int NOTIF_CH_LOGO_MAX_WIDTH =
                getResources().getDimensionPixelSize(R.dimen.notif_ch_logo_max_width);
        final int NOTIF_CH_LOGO_PADDING_LEFT =
                getResources().getDimensionPixelSize(R.dimen.notif_ch_logo_padding_left);
        final int NOTIF_CH_LOGO_PADDING_BOTTOM =
                getResources().getDimensionPixelSize(R.dimen.notif_ch_logo_padding_bottom);

        Bitmap result = BitmapUtils.scaleBitmap(
                background, Integer.MAX_VALUE, NOTIF_CARD_IMG_HEIGHT);
        Bitmap scaledLogo = BitmapUtils.scaleBitmap(
                logo, NOTIF_CH_LOGO_MAX_WIDTH, NOTIF_CH_LOGO_MAX_HEIGHT);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(result, new Matrix(), null);
        Rect rect = new Rect();
        if (result.getWidth() < NOTIF_CARD_IMG_MIN_WIDTH) {
            // TODO: check the positions.
            rect.left = NOTIF_CH_LOGO_PADDING_LEFT;
            rect.right = rect.left + scaledLogo.getWidth();
            rect.bottom = result.getHeight() - NOTIF_CH_LOGO_PADDING_BOTTOM;
            rect.top = rect.bottom - scaledLogo.getHeight();
        } else if (result.getWidth() < NOTIF_CARD_IMG_MAX_WIDTH) {
            rect.left = NOTIF_CH_LOGO_PADDING_LEFT;
            rect.right = rect.left + scaledLogo.getWidth();
            rect.bottom = result.getHeight() - NOTIF_CH_LOGO_PADDING_BOTTOM;
            rect.top = rect.bottom - scaledLogo.getHeight();
        } else {
            int marginLeft = (result.getWidth() - NOTIF_CARD_IMG_MAX_WIDTH) / 2;
            rect.left = NOTIF_CH_LOGO_PADDING_LEFT + marginLeft;
            rect.right = rect.left + scaledLogo.getWidth() + marginLeft;
            rect.bottom = result.getHeight() - NOTIF_CH_LOGO_PADDING_BOTTOM;
            rect.top = rect.bottom - scaledLogo.getHeight();
        }
        Paint paint = new Paint();
        paint.setAlpha(getResources().getInteger(R.integer.notif_card_ch_logo_alpha));
        canvas.drawBitmap(scaledLogo, null, rect, paint);
        return result;
    }
}

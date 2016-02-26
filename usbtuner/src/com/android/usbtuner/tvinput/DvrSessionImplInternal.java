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

package com.android.usbtuner.tvinput;

import android.content.ContentUris;
import android.content.Context;
import android.media.MediaDataSource;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.android.tv.common.recording.RecordingCapability;
import com.android.usbtuner.DvbDeviceAccessor;
import com.android.usbtuner.TunerHal;
import com.android.usbtuner.UsbTunerDataSource;
import com.android.usbtuner.data.PsipData;
import com.android.usbtuner.data.TunerChannel;
import com.android.usbtuner.exoplayer.CacheManager;
import com.android.usbtuner.exoplayer.DvrStorageManager;
import com.android.usbtuner.exoplayer.RecordSampleSourceExtractor;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Implements a DVR feature.
 */
public class DvrSessionImplInternal implements PlaybackCacheListener, EventDetector.EventListener,
        Handler.Callback {
    private static String TAG = "DvrSessionImplInternal";
    private static final boolean DEBUG = true;  // STOPSHIP(DVR)


    private static final int MSG_START_RECORDING = 1;
    private static final int MSG_STOP_RECORDING = 2;
    private static final int MSG_DELETE_RECORDING = 3;
    private static final int MSG_RELEASE = 4;
    private final String mInputId;
    private RecordingCapability mCapabilities;

    public RecordingCapability getCapabilities() {
        return mCapabilities;
    }

    @IntDef({STATE_IDLE, STATE_RECORDING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DvrSessionState {}
    private static final int STATE_IDLE = 1;
    private static final int STATE_RECORDING = 2;

    private static final long CHANNEL_ID_NONE = -1;

    private final Context mContext;
    private final ChannelDataManager mChannelDataManager;
    private final Handler mHandler;
    private final CountDownLatch mReleaseLatch = new CountDownLatch(1);

    private TunerHal mTunerHal;
    private UsbTunerDataSource mTunerSource;
    private CacheManager mCacheManager;
    private RecordSampleSourceExtractor mRecorder;
    private DvrEventListener mDvrEventListener;
    @DvrSessionState private int mSessionState = STATE_IDLE;

    // For event notification to LiveChannels
    public interface DvrEventListener {
        void onRecordStarted(Uri mediaUri);
        void onRecordUnexpectedlyStopped(Uri mediaUri, int reason);
        void onDeleted(Uri mediaUri);
        void onDeleteFailed(Uri mediaUri, int reason);
    }

    public DvrSessionImplInternal(Context context, String inputId, ChannelDataManager dataManager) {
        mContext = context;
        mInputId = inputId;
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper(), this);
        mChannelDataManager = dataManager;
        mChannelDataManager.checkDataVersion(context);
        mCapabilities = new DvbDeviceAccessor(context).getRecordingCapability(mInputId);
        if (DEBUG) Log.d(TAG, mCapabilities.toString());
    }

    // PlaybackCacheListener
    @Override
    public void onCacheStartTimeChanged(long startTimeMs) {
    }

    @Override
    public void onCacheStateChanged(boolean available) {
    }

    @Override
    public void onDiskTooSlow() {
    }

    // EventDetector.EventListener
    @Override
    public void onChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime) {
        mChannelDataManager.notifyChannelDetected(channel, channelArrivedAtFirstTime);
    }

    @Override
    public void onEventDetected(TunerChannel channel, List<PsipData.EitItem> items) {
        mChannelDataManager.notifyEventDetected(channel, items);
    }

    public void setDvrEventListener(DvrEventListener listener) {
        mDvrEventListener = listener;
    }

    public void startRecording(Uri channelUri, Uri mediaUri) {
        mHandler.obtainMessage(
                MSG_START_RECORDING, new Pair<>(channelUri, mediaUri)).sendToTarget();
    }

    public void stopRecording() {
        mHandler.sendEmptyMessage(MSG_STOP_RECORDING);
    }

    public void deleteRecording(Uri mediaUri) {
        mHandler.obtainMessage(MSG_DELETE_RECORDING, mediaUri).sendToTarget();
    }

    public void release() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendEmptyMessage(MSG_RELEASE);
        try {
            mReleaseLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Couldn't wait for finish of MSG_RELEASE", e);
        } finally {
            mHandler.getLooper().quitSafely();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_START_RECORDING: {
                Pair<Uri, Uri> params = (Pair<Uri, Uri>) msg.obj;
                if(onStartRecording(params.first, params.second)) {
                    Toast.makeText(mContext, "USB TV tuner: onStart is called",
                            Toast.LENGTH_SHORT).show();
                    if (mDvrEventListener != null) {
                        mDvrEventListener.onRecordStarted(params.second);
                    }
                }
                else {
                    // TODO: apply reason
                    if (mDvrEventListener != null) {
                        mDvrEventListener.onRecordUnexpectedlyStopped(params.second, 0);
                    }
                }
                return true;
            }
            case MSG_STOP_RECORDING: {
                onStopRecording();
                Toast.makeText(mContext, "USB TV tuner: onStopRecord is called",
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            case MSG_DELETE_RECORDING: {
                Uri toDelete = (Uri) msg.obj;
                onDeleteRecording(toDelete);
                return true;
            }
            case MSG_RELEASE: {
                onRelease();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private TunerChannel getChannel(Uri channelUri) {
        long channelId;
        try {
            channelId = ContentUris.parseId(channelUri);
        } catch (UnsupportedOperationException | NumberFormatException e) {
            channelId = CHANNEL_ID_NONE;
        }
        return (channelId == CHANNEL_ID_NONE) ? null : mChannelDataManager.getChannel(channelId);
    }

    private File getMediaDir(Uri mediaUri) {
        String mediaPath = mediaUri.getPath();
        if (mediaPath == null || mediaPath.length() == 0) {
            return null;
        }
        return new File(mContext.getCacheDir().getAbsolutePath() + "/recording" +
                mediaUri.getPath());
    }

    private void resetRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        if (mCacheManager != null) {
            mCacheManager.close();
            mCacheManager = null;
        }
        if (mTunerSource != null) {
            mTunerSource.stopStream();
            mTunerSource = null;
        }
        if (mTunerHal != null) {
            try {
                mTunerHal.close();
            } catch (Exception ex) {
                Log.e(TAG, "Error on closing tuner HAL.", ex);
            }
            mTunerHal = null;
        }
    }

    private boolean onStartRecording(Uri channelUri, Uri mediaUri) {
        if (mSessionState != STATE_IDLE) {
            return false;
        }
        TunerChannel channel = getChannel(channelUri);
        if (channel == null) {
            Log.w(TAG, "Failed to start recording. Couldn't find the channel for " + channelUri);
            return false;
        }
        mTunerHal = TunerHal.getInstance(mContext);
        if (mTunerHal == null) {
            Log.w(TAG, "Failed to start recording. Couldn't open a DVB device");
            resetRecorder();
            return false;
        }
        mTunerSource = new UsbTunerDataSource(mTunerHal, this);
        if (!mTunerSource.tuneToChannel(channel)) {
            Log.w(TAG, "Failed to start recording. Couldn't tune to the channel for " + channel);
            resetRecorder();
            return false;
        }
        File mediaDir = getMediaDir(mediaUri);
        if (mediaDir == null) {
            Log.w(TAG, "Failed to start recording. mediaUri is not provided properly " +
                    mediaUri.toString());
            resetRecorder();
            return false;
        }
        mTunerSource.startStream();
        mCacheManager = new CacheManager(new DvrStorageManager(mediaDir, true));
        mRecorder = new RecordSampleSourceExtractor((MediaDataSource) mTunerSource,
                mCacheManager, this);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.w(TAG, "Failed to start recording. Couldn't prepare a extractor");
            resetRecorder();
            return false;
        }
        mSessionState = STATE_RECORDING;
        return true;
    }

    private void onStopRecording() {
        // TODO: notify the recording result to LiveChannels
        if (mSessionState != STATE_RECORDING) {
            return;
        }
        resetRecorder();
        mSessionState = STATE_IDLE;
    }

    private void onDeleteRecording(Uri mediaUri) {
        // TODO: notify the deletion result to LiveChannels
        File mediaDir = getMediaDir(mediaUri);
        if (mediaDir == null) {
            return;
        }
        for(File file: mediaDir.listFiles()) {
            file.delete();
        }
        mediaDir.delete();
    }

    private void onRelease() {
        // Current recording will be canceled.
        onStopRecording();
        mReleaseLatch.countDown();
    }
}

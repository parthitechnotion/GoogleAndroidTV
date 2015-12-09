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

import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.android.tv.common.dvr.DvrTvInputService;
import com.android.usbtuner.exoplayer.CacheManager;
import com.android.usbtuner.exoplayer.TrickplayStorageManager;
import com.android.usbtuner.util.SystemPropertiesProxy;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link UsbTunerTvInputService} serves TV channels coming from a usb tuner device.
 */
public class UsbTunerTvInputService extends DvrTvInputService
        implements AudioCapabilitiesReceiver.Listener {
    private static final String TAG = "UsbTunerTvInputService";
    private static final boolean DEBUG = false;


    private static final String MAX_CACHE_SIZE_KEY = "usbtuner.cachesize_mbytes";
    private static final int MAX_CACHE_SIZE_DEF = 2 * 1024;  // 2GB
    private static final int MIN_CACHE_SIZE_DEF = 256;  // 256MB

    private List<TvInputSessionImpl> mTvInputSessions;
    private ChannelDataManager mChannelDataManager;
    private AudioCapabilitiesReceiver mAudioCapabilitiesReceiver;
    private AudioCapabilities mAudioCapabilities;
    private CacheManager mCacheManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }
        mTvInputSessions = new CopyOnWriteArrayList<>();
        mChannelDataManager = new ChannelDataManager(getApplicationContext());
        mAudioCapabilitiesReceiver = new AudioCapabilitiesReceiver(getApplicationContext(), this);
        mAudioCapabilitiesReceiver.register();
        maybeInitCacheManager();
    }

    private void maybeInitCacheManager() {
        int maxCacheSizeMb = SystemPropertiesProxy.getInt(MAX_CACHE_SIZE_KEY, MAX_CACHE_SIZE_DEF);
        if (maxCacheSizeMb >= MIN_CACHE_SIZE_DEF) {
            boolean useExternalStorage = Environment.MEDIA_MOUNTED.equals(
                    Environment.getExternalStorageState()) &&
                    Environment.isExternalStorageRemovable();
            if (DEBUG) {
                Log.d(TAG, "useExternalStorage for trickplay: " + useExternalStorage);
            }
            boolean allowToUseInternalStorage = true;
            if (useExternalStorage || allowToUseInternalStorage) {
                File baseDir = useExternalStorage ? getExternalCacheDir() : getCacheDir();
                mCacheManager = new CacheManager(
                        new TrickplayStorageManager(getApplicationContext(), baseDir,
                                1024L * 1024 * maxCacheSizeMb));
            }
        }
        if (mCacheManager == null) {
            Log.i(TAG, "Trickplay is disabled");
        } else {
            Log.i(TAG, "Trickplay is enabled");
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "onDestroy");
        }
        super.onDestroy();
        mChannelDataManager.release();
        mAudioCapabilitiesReceiver.unregister();
        if (mCacheManager != null) {
            mCacheManager.close();
        }
    }

    @Override
    public DvrSession onCreateDvrSession(String inputId) {
        return new DvrSessionImpl(this, mChannelDataManager);
    }

    @Override
    public DvrTvInputService.PlaybackSession onCreatePlaybackSession(String inputId) {
        if (DEBUG) {
            Log.d(TAG, "onCreateSession");
        }
        final TvInputSessionImpl session = new TvInputSessionImpl(
                this, mChannelDataManager, mCacheManager);
        mTvInputSessions.add(session);
        session.notifyAudioCapabilitiesChanged(mAudioCapabilities);
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                // STOPSHIP(DVR): Session methods cannot be called inside onCreatePlaybackSession.
                // If DvrSession is added in API. we can call them inside onCreatePlaybackSession.
                session.setOverlayViewEnabled(true);
            }
        });
        return session;
    }

    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        mAudioCapabilities = audioCapabilities;
        for (TvInputSessionImpl session : mTvInputSessions) {
            if (session.isReleased()) {
                mTvInputSessions.remove(session);
            } else {
                session.notifyAudioCapabilitiesChanged(audioCapabilities);
            }
        }
    }
}

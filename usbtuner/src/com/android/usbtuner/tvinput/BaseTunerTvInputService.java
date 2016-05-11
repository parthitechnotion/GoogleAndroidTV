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

import android.os.Handler;
import android.util.Log;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.android.tv.common.recording.RecordingTvInputService;
import com.android.tv.common.recording.TvRecording;
import com.android.usbtuner.exoplayer.CacheManager;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@link BaseTunerTvInputService} serves TV channels coming from a usb tuner device.
 */
public abstract class BaseTunerTvInputService extends RecordingTvInputService
        implements AudioCapabilitiesReceiver.Listener {
    private static final String TAG = "BaseTunerTvInputService";
    private static final boolean DEBUG = false;

    // WeakContainer for {@link TvInputSessionImpl}
    private final Set<TvInputSessionImpl> mTvInputSessions = Collections.newSetFromMap(
            new WeakHashMap<TvInputSessionImpl, Boolean>());
    private ChannelDataManager mChannelDataManager;
    private AudioCapabilitiesReceiver mAudioCapabilitiesReceiver;
    private AudioCapabilities mAudioCapabilities;
    protected CacheManager mCacheManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }
        mChannelDataManager = new ChannelDataManager(getApplicationContext());
        mAudioCapabilitiesReceiver = new AudioCapabilitiesReceiver(getApplicationContext(), this);
        mAudioCapabilitiesReceiver.register();
        maybeInitCacheManager();
        if (mCacheManager == null) {
            Log.i(TAG, "Trickplay is disabled");
        } else {
            Log.i(TAG, "Trickplay is enabled");
        }
    }

    protected abstract void maybeInitCacheManager();

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
    public TvRecording.RecordingSession onCreateDvrSession(String inputId) {
        return new RecordingSessionImpl(this, inputId, mChannelDataManager);
    }

    @Override
    public RecordingTvInputService.PlaybackSession onCreatePlaybackSession(String inputId) {
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
            if (!session.isReleased()) {
                session.notifyAudioCapabilitiesChanged(audioCapabilities);
            }
        }
    }
}

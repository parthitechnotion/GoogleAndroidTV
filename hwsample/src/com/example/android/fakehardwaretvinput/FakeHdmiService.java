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

package com.example.android.fakehardwaretvinput;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.tv.ITvInputManager;
import android.media.tv.TvInputHardwareInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

/**
 * Demonstrates a simple HDMI input.
 *
 * Note that the service doesn't need to communicate with HdmiControlService directly unless it is
 * dealing with custom CEC or MHL signals.
 */
public class FakeHdmiService extends TvInputService {
    private static final boolean DEBUG = true;
    private static final String TAG = FakeHdmiService.class.getSimpleName();
    private static final int[] COLORS = { 0xFFFF0000, 0xFF00FF00, 0xFF0000FF };

    private final SparseArray<TvInputInfo> mInputMap = new SparseArray<TvInputInfo>();
    private ITvInputManager mManager = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = ITvInputManager.Stub.asInterface(
                ServiceManager.getService(Context.TV_INPUT_SERVICE));
    }

    @Override
    public Session onCreateSession(String inputId) {
        TvInputInfo info = null;
        for (int i = 0; i < mInputMap.size(); ++i) {
            if (mInputMap.valueAt(i).getId().equals(inputId)) {
                info = mInputMap.valueAt(i);
                break;
            }
        }
        if (info == null) {
            throw new IllegalArgumentException("Unknown inputId: " + inputId
                    + " ; this should not happen.");
        }
        return new HdmiInputSessionImpl(info);
    }

    @Override
    public TvInputInfo onHardwareAdded(TvInputHardwareInfo hardwareInfo) {
        if (hardwareInfo.getType() != TvInputHardwareInfo.TV_INPUT_TYPE_HDMI) {
            return null;
        }
        if (mInputMap.indexOfKey(hardwareInfo.getDeviceId()) >= 0) {
            Log.e(TAG, "Already created TvInputInfo for deviceId="
                    + hardwareInfo.getDeviceId());
            return null;
        }
        ResolveInfo ri = getPackageManager().resolveService(
                new Intent(SERVICE_INTERFACE).setClass(this, getClass()),
                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        TvInputInfo info = null;
        try {
            info = TvInputInfo.createTvInputInfo(this, ri, hardwareInfo,
                    "HDMI " + hardwareInfo.getHdmiPortId(), null);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error while creating TvInputInfo", e);
            return null;
        }
        mInputMap.put(hardwareInfo.getDeviceId(), info);

        if (DEBUG) Log.d(TAG, "onHardwareAdded returns " + info);
        return info;
    }

    @Override
    public String onHardwareRemoved(TvInputHardwareInfo hardwareInfo) {
        int deviceId = hardwareInfo.getDeviceId();
        TvInputInfo info = mInputMap.get(deviceId);
        if (info == null) {
            if (DEBUG) Log.d(TAG, "TvInputInfo for deviceId=" + deviceId + " does not exist.");
            return null;
        }
        mInputMap.remove(deviceId);
        if (DEBUG) Log.d(TAG, "onHardwareRemoved returns " + info.getId());
        return info.getId();
    }

    private class HdmiInputSessionImpl extends Session {
        private final TvInputInfo mInfo;
        private final Object mImplLock = new Object();
        private Surface mSurface = null;
        private final Paint mTextPaint = new Paint();
        private final Handler mHandler = new Handler();
        private final String mLabel;

        private final Runnable mDrawTask = new Runnable() {
            private int mIndex = 0;

            @Override
            public void run() {
                synchronized (mImplLock) {
                    if (mSurface != null) {
                        Canvas c = mSurface.lockCanvas(null);
                        c.drawColor(COLORS[mIndex]);
                        c.drawText(mLabel, 0f, 0f, mTextPaint);
                        mSurface.unlockCanvasAndPost(c);
                    }
                }
                ++mIndex;
                if (mIndex >= COLORS.length) {
                    mIndex = 0;
                }
                mHandler.postDelayed(this, 1000);
            }
        };

        HdmiInputSessionImpl(TvInputInfo info) {
            mInfo = info;
            mLabel = info.loadLabel(FakeHdmiService.this).toString();
            mTextPaint.setColor(0xFF000000);
            mHandler.post(mDrawTask);
        }

        @Override
        public void onRelease() {
            mHandler.removeCallbacks(mDrawTask);
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            synchronized (mImplLock) {
                mSurface = surface;
                return true;
            }
        }

        @Override
        public void onSetStreamVolume(float volume) {
            // No-op
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            // No-op
        }
    }
}

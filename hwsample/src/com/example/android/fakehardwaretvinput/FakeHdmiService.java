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
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.hdmi.HdmiCecDeviceInfo;
import android.hardware.hdmi.IHdmiControlService;
import android.media.tv.ITvInputManager;
import android.media.tv.TvContentRating;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    private static final int[] COLORS = {
          0xFF888888, 0xFF999999, 0xFFAAAAAA, 0xFFBBBBBB, 0xFFAAAAAA, 0xFF999999};
    private static final int[] ICONS = { R.drawable.fake_icon0, R.drawable.fake_icon1,
          R.drawable.fake_icon2, R.drawable.fake_icon3 };

    private ITvInputManager mManager = null;
    private IHdmiControlService mHdmiControlService = null;

    private final SparseArray<String> mHardwareInputIdMap = new SparseArray<String>();
    private final SparseArray<String> mCecInputIdMap = new SparseArray<String>();
    private final Map<String, TvInputInfo> mInputMap = new HashMap<String, TvInputInfo>();
    private ResolveInfo mResolveInfo;
    private final Random mRandom = new Random();

    private static class PortInfo {
        private final int mPortId;
        private final int mHardwareDeviceId;
        private final int mCecLogicalAddress;

        PortInfo(int portId, int hardwareDeviceId, int cecLogicalAddress) {
            this.mPortId = portId;
            this.mHardwareDeviceId = hardwareDeviceId;
            this.mCecLogicalAddress = cecLogicalAddress;
        }
    }
    private final SparseArray<PortInfo> mPortInfos = new SparseArray<PortInfo>();

    @Override
    public void onCreate() {
        super.onCreate();
        mResolveInfo = getPackageManager().resolveService(
                new Intent(SERVICE_INTERFACE).setClass(this, getClass()),
                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        mManager = ITvInputManager.Stub.asInterface(
                ServiceManager.getService(Context.TV_INPUT_SERVICE));
        mHdmiControlService = IHdmiControlService.Stub.asInterface(
                ServiceManager.getService(Context.HDMI_CONTROL_SERVICE));
    }

    @Override
    public Session onCreateSession(String inputId) {
        TvInputInfo info = mInputMap.get(inputId);
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
        int deviceId = hardwareInfo.getDeviceId();
        if (mHardwareInputIdMap.indexOfKey(deviceId) >= 0) {
            Log.e(TAG, "Already created TvInputInfo for deviceId=" + deviceId);
            return null;
        }
        int portId = hardwareInfo.getHdmiPortId();
        if (portId < 0) {
            Log.e(TAG, "Failed to get HDMI port for deviceId=" + deviceId);
            return null;
        }
        if (mPortInfos.get(portId) != null) {
            Log.e(TAG, "Already have port " + portId + " for deviceId=" + deviceId);
            return null;
        }
        TvInputInfo info = null;
        try {
            info = TvInputInfo.createTvInputInfo(this, mResolveInfo, hardwareInfo,
                    "HDMI " + hardwareInfo.getHdmiPortId(), null);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error while creating TvInputInfo", e);
            return null;
        }
        mHardwareInputIdMap.put(deviceId, info.getId());
        mInputMap.put(info.getId(), info);
        mPortInfos.put(portId, new PortInfo(portId, deviceId, -1));
        if (DEBUG) Log.d(TAG, "onHardwareAdded returns " + info);
        return info;
    }

    @Override
    public String onHardwareRemoved(TvInputHardwareInfo hardwareInfo) {
        int deviceId = hardwareInfo.getDeviceId();
        String inputId = mHardwareInputIdMap.get(deviceId);
        if (inputId == null) {
            if (DEBUG) Log.d(TAG, "TvInputInfo for deviceId=" + deviceId + " does not exist.");
            return null;
        }
        int portId = getPortInfoForDeviceId(deviceId);
        if (portId == -1) {
            Log.w(TAG, "Port not exists for deviceId=" + deviceId);
        }
        mPortInfos.remove(portId);
        mHardwareInputIdMap.remove(deviceId);
        mInputMap.remove(inputId);
        if (DEBUG) Log.d(TAG, "onHardwareRemoved returns " + inputId);
        return inputId;
    }

    private int getPortInfoForDeviceId(int deviceId) {
        for (int i = 0; i < mPortInfos.size(); i++) {
            PortInfo portInfo = mPortInfos.valueAt(i);
            if (portInfo.mHardwareDeviceId == deviceId) {
                return portInfo.mPortId;
            }
        }
        return -1;
    }

    @Override
    public TvInputInfo onHdmiCecDeviceAdded(HdmiCecDeviceInfo cecDeviceInfo) {
        int logicalAddress = cecDeviceInfo.getLogicalAddress();
        if (mCecInputIdMap.indexOfKey(logicalAddress) >= 0) {
            Log.e(TAG, "Already created TvInputInfo for logicalAddress=" + logicalAddress);
            return null;
        }
        int portId = cecDeviceInfo.getPortId();
        if (portId < 0) {
            Log.e(TAG, "Failed to get HDMI port for logicalAddress=" + logicalAddress);
            return null;
        }
        PortInfo portInfo = mPortInfos.get(portId);
        if (portInfo == null) {
            Log.e(TAG, "Unknown HDMI port " + portId + " for logicalAddress=" + logicalAddress);
            return null;
        }
        TvInputInfo parentInfo =
                mInputMap.get(mHardwareInputIdMap.get(portInfo.mHardwareDeviceId));
        TvInputInfo info = null;
        try {
            info = TvInputInfo.createTvInputInfo(this, mResolveInfo, cecDeviceInfo,
                    parentInfo.getId(), cecDeviceInfo.getDisplayName(),
                    Uri.parse("android.resource://" + getPackageName() + "/"
                          + ICONS[mRandom.nextInt(ICONS.length)]));
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error while creating TvInputInfo", e);
            return null;
        }
        mCecInputIdMap.put(logicalAddress, info.getId());
        mInputMap.put(info.getId(), info);
        if (DEBUG) Log.d(TAG, "onHdmiCecDeviceAdded returns " + info);
        return info;
    }

    @Override
    public String onHdmiCecDeviceRemoved(HdmiCecDeviceInfo cecDeviceInfo) {
        int logicalAddress = cecDeviceInfo.getLogicalAddress();
        String inputId = mCecInputIdMap.get(logicalAddress);
        if (inputId == null) {
            if (DEBUG) {
                Log.d(TAG, "TvInputInfo for logicalAddress=" + logicalAddress + " does not exist.");
            }
            return null;
        }
        mCecInputIdMap.remove(logicalAddress);
        mInputMap.remove(inputId);
        if (DEBUG) Log.d(TAG, "onHdmiCecDeviceRemoved returns " + inputId);
        return inputId;
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
                        c.drawText(mLabel, 100f, 200f, mTextPaint);
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
            mTextPaint.setColor(Color.BLACK);
            mTextPaint.setTextSize(200);
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

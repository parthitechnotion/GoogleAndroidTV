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

package com.android.usbtuner;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.support.annotation.IntDef;
import android.util.Log;

import com.android.usbtuner.DvbDeviceAccessor.DvbDeviceInfoWrapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * An interface to handle a hardware USB tuner device.
 */
public class UsbTunerInterface {
    private static final String TAG = "UsbTunerInterface";
    private static final boolean DEBUG = false;

    @IntDef({FILTER_TYPE_OTHER, FILTER_TYPE_AUDIO, FILTER_TYPE_VIDEO, FILTER_TYPE_PCR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FilterType {}
    public static final int FILTER_TYPE_OTHER = 0;
    public static final int FILTER_TYPE_AUDIO = 1;
    public static final int FILTER_TYPE_VIDEO = 2;
    public static final int FILTER_TYPE_PCR = 3;

    private static final int PID_PAT = 0;
    private static final int PID_ATSC_SI_BASE = 0x1ffb;
    private static final int DEFAULT_VSB_TUNE_TIMEOUT_MS = 2000;
    private static final int DEFAULT_QAM_TUNE_TIMEOUT_MS = 4000; // Some device takes time for
                                                                 // QAM256 tuning.

    private static final Set<DvbDeviceInfoWrapper> sUsedDvbDevices = new TreeSet<>();
    private static final Object sLock = new Object();

    private final DvbDeviceAccessor mDvbDeviceAccessor;
    private boolean mIsStreaming;
    private int mFrequency;
    private String mModulation;
    private DvbDeviceInfoWrapper mDvbDeviceInfo;

    static {
        System.loadLibrary("usbtuner_jni");
    }

    public UsbTunerInterface(Context context) {
        mDvbDeviceAccessor = new DvbDeviceAccessor(context);
        mIsStreaming = false;
        mFrequency = -1;
        mModulation = null;
        mDvbDeviceInfo = null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    private native void nativeFinalize(long deviceId);

    /**
     * Acquires the first available tuner device. If there is a tuner device that is available, the
     * tuner device will be locked to the current instance.
     *
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    public boolean openFirstAvailable() {
        List<DvbDeviceInfoWrapper> deviceInfoList = mDvbDeviceAccessor.getDvbDeviceList();
        if (deviceInfoList == null || deviceInfoList.isEmpty()) {
            Log.e(TAG, "There's no dvb device attached");
            return false;
        }
        synchronized (sLock) {
            for (DvbDeviceInfoWrapper deviceInfo : deviceInfoList) {
                if (!sUsedDvbDevices.contains(deviceInfo)) {
                    if (DEBUG) {
                        Log.d(TAG, "Available device info: " + deviceInfo);
                    }
                    mDvbDeviceInfo = deviceInfo;
                    sUsedDvbDevices.add(deviceInfo);
                    return true;
                }
            }
        }
        Log.e(TAG, "There's no available dvb devices");
        return false;
    }

    /**
     * Acquires the tuner device. The requested device will be locked to the current instance if
     * it's not acquired by others.
     *
     * @param deviceInfo a tuner device to open
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    public boolean open(DvbDeviceInfoWrapper deviceInfo) {
        if (deviceInfo == null) {
            Log.e(TAG, "Device info should not be null");
            return false;
        }
        if (mDvbDeviceInfo != null) {
            Log.e(TAG, "Already acquired");
            return false;
        }
        List<DvbDeviceInfoWrapper> deviceInfoList = mDvbDeviceAccessor.getDvbDeviceList();
        if (deviceInfoList == null || deviceInfoList.isEmpty()) {
            Log.e(TAG, "There's no dvb device attached");
            return false;
        }
        for (DvbDeviceInfoWrapper deviceInfoWrapper : deviceInfoList) {
            if (deviceInfoWrapper.compareTo(deviceInfo) == 0) {
                synchronized (sLock) {
                    if (sUsedDvbDevices.contains(deviceInfo)) {
                        Log.e(TAG, deviceInfo + " is already taken");
                        return false;
                    }
                    sUsedDvbDevices.add(deviceInfo);
                }
                if (DEBUG) {
                    Log.d(TAG, "Available device info: " + deviceInfo);
                }
                mDvbDeviceInfo = deviceInfo;
                return true;
            }
        }
        Log.e(TAG, "There's no such dvb device attached");
        return false;
    }

    /**
     * Releases the already acquired tuner device. This should be called after closing the tuner
     * device if it's opened for tuning.
     */
    public void close() {
        if (mDvbDeviceInfo != null) {
            if (mIsStreaming) {
                stopTune();
            }
            nativeFinalize(mDvbDeviceInfo.getId());
        }
        synchronized (sLock) {
            if (mDvbDeviceInfo != null) {
                sUsedDvbDevices.remove(mDvbDeviceInfo);
            }
        }
        mDvbDeviceInfo = null;
    }

    /**
     * Sets the tuner channel. This should be called after acquiring a tuner device.
     *
     * @param frequency a frequency of the channel to tune to
     * @param modulation a modulation method of the channel to tune to
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    public boolean tuneAtsc(int frequency, String modulation) {
        if (mDvbDeviceInfo == null) {
            Log.e(TAG, "There's no available device");
            return false;
        }
        if (mIsStreaming) {
            nativeCloseAllPidFilters(mDvbDeviceInfo.getId());
            mIsStreaming = false;
        }

        // When tuning to a new channel in the same frequency, there's no need to stop current tuner
        // device completely and the only thing necessary for tuning is reopening pid filters.
        if (mFrequency == frequency && Objects.equals(mModulation, modulation)) {
            addTunerPidFilter(PID_PAT, FILTER_TYPE_OTHER);
            addTunerPidFilter(PID_ATSC_SI_BASE, FILTER_TYPE_OTHER);
            mIsStreaming = true;
            return true;
        }
        int timeout_ms = modulation.equals("8VSB") ? DEFAULT_VSB_TUNE_TIMEOUT_MS
                : DEFAULT_QAM_TUNE_TIMEOUT_MS;
        if (nativeTuneAtsc(mDvbDeviceInfo.getId(), frequency, modulation, timeout_ms)) {
            addTunerPidFilter(PID_PAT, FILTER_TYPE_OTHER);
            addTunerPidFilter(PID_ATSC_SI_BASE, FILTER_TYPE_OTHER);
            mFrequency = frequency;
            mModulation = modulation;
            mIsStreaming = true;
            return true;
        }
        return false;
    }

    private native boolean nativeTuneAtsc(long deviceId, int frequency, String modulation,
            int timeout_ms);

    /**
     * Sets a pid filter. This should be set after setting a channel.
     *
     * @param pid a pid number to be added to filter list
     * @param filterType a type of pid. Must be one of (FILTER_TYPE_XXX)
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    public boolean addTunerPidFilter(int pid, @FilterType int filterType) {
        if (mDvbDeviceInfo == null) {
            Log.e(TAG, "There's no available device");
            return false;
        }
        if (pid >= 0 && pid <= 0x1fff) {
            nativeAddPidFilter(mDvbDeviceInfo.getId(), pid, filterType);
            return true;
        }
        return false;
    }

    private native void nativeAddPidFilter(long deviceId, int pid, @FilterType int filterType);
    private native void nativeCloseAllPidFilters(long deviceId);

    /**
     * Stops the tuner stream.
     */
    public void stopStreaming() {
        if (mDvbDeviceInfo != null && mIsStreaming) {
            nativeCloseAllPidFilters(mDvbDeviceInfo.getId());
        }
        mIsStreaming = false;
    }

    private native void nativeStopTune(long deviceId);

    /**
     * This method must be called after {@link UsbTunerInterface#tuneAtsc} and before
     * {@link UsbTunerInterface#stopStreaming}. Writes at most maxSize TS frames in a buffer
     * provided by the user. The frames employ MPEG encoding.
     *
     * @param javaBuffer a buffer to write the video data in
     * @param javaBufferSize the max amount of bytes to write in this buffer. Usually this number
     *            should be equal to the length of the buffer.
     * @return the amount of bytes written in the buffer. Note that this value could be 0 if no new
     *         frames have been obtained since the last call.
     */
    public int readTsStream(byte[] javaBuffer, int javaBufferSize) {
        if (mDvbDeviceInfo != null) {
            return nativeWriteInBuffer(mDvbDeviceInfo.getId(), javaBuffer, javaBufferSize);
        } else {
            return 0;
        }
    }

    private native int nativeWriteInBuffer(long deviceId, byte[] javaBuffer, int javaBufferSize);

    // Call from native
    private int openDvbFrontEndFd() {
        if (mDvbDeviceInfo != null) {
            ParcelFileDescriptor descriptor = mDvbDeviceAccessor.openDvbDevice(
                    mDvbDeviceInfo, DvbDeviceAccessor.DVB_DEVICE_FRONTEND);
            if (descriptor != null) {
                return descriptor.detachFd();
            }
        }
        return -1;
    }

    private int openDvbDemuxFd() {
        if (mDvbDeviceInfo != null) {
            ParcelFileDescriptor descriptor = mDvbDeviceAccessor.openDvbDevice(
                    mDvbDeviceInfo, DvbDeviceAccessor.DVB_DEVICE_DEMUX);
            if (descriptor != null) {
                return descriptor.detachFd();
            }
        }
        return -1;
    }

    private int openDvbDvrFd() {
        if (mDvbDeviceInfo != null) {
            ParcelFileDescriptor descriptor = mDvbDeviceAccessor.openDvbDevice(
                    mDvbDeviceInfo, DvbDeviceAccessor.DVB_DEVICE_DVR);
            if (descriptor != null) {
                return descriptor.detachFd();
            }
        }
        return -1;
    }

    /**
     * Closes the opened tuner device. This method should be called after tuning successfully.
     */
    public void stopTune() {
        if (mDvbDeviceInfo != null) {
            nativeStopTune(mDvbDeviceInfo.getId());
        }
        mIsStreaming = false;
        mFrequency = -1;
        mModulation = null;
    }
}

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

package com.android.usbtuner.exoplayer.ac3;

import android.media.MediaFormat;

import com.google.android.exoplayer.audio.AudioTrack;

import java.nio.ByteBuffer;

/**
 * {@link AudioTrack} wrapper class for trickplay operations including FF/RW.
 * FF/RW trickplay operations do not need framework {@link AudioTrack}.
 * This wrapper class will do nothing in disabled status for those operations.
 */
public class AudioTrackWrapper {
    private final AudioTrack mAudioTrack = new AudioTrack();
    private int mAudioSessionID;
    private boolean mIsEnabled;

    AudioTrackWrapper() {
        mIsEnabled = true;
    }

    public void resetSessionId() {
        mAudioSessionID = AudioTrack.SESSION_ID_NOT_SET;
    }

    public boolean isInitialized() {
        if (!mIsEnabled) {
            return false;
        }
        return mAudioTrack.isInitialized();
    }

    public void restart() {
        if (mAudioTrack.isInitialized()) {
            mAudioTrack.release();
        }
        mIsEnabled = true;
        resetSessionId();
    }

    public void release()  {
        if (mAudioSessionID != AudioTrack.SESSION_ID_NOT_SET) {
            mAudioTrack.release();
        }
    }

    public void initialize() throws AudioTrack.InitializationException {
        if (!mIsEnabled) {
            return;
        }
        if (mAudioSessionID != AudioTrack.SESSION_ID_NOT_SET) {
            mAudioTrack.initialize(mAudioSessionID);
        } else {
            mAudioSessionID = mAudioTrack.initialize();
        }
        return;
    }

    public void reset() {
        if (!mIsEnabled) {
            return;
        }
        mAudioTrack.reset();
    }

    public boolean isEnded() {
        if (!mIsEnabled) {
            return true;
        }
        return !mAudioTrack.hasPendingData() || !mAudioTrack.hasEnoughDataToBeginPlayback();
    }

    public boolean isReady() {
        if (!mIsEnabled) {
            return false;
        }
        return mAudioTrack.hasPendingData();
    }

    public void play() {
        if (!mIsEnabled) {
            return;
        }
        mAudioTrack.play();
    }

    public void pause() {
        if (!mIsEnabled) {
            return;
        }
        mAudioTrack.pause();
    }

    public void setVolume(float volume) {
        if (!mIsEnabled) {
            return;
        }
        mAudioTrack.setVolume(volume);
    }

    public void reconfigure(MediaFormat format) {
        if (!mIsEnabled) {
            return;
        }
        mAudioTrack.reconfigure(format);
    }

    public void handleDiscontinuity() {
        if (!mIsEnabled) {
            return;
        }
        mAudioTrack.handleDiscontinuity();
    }

    public int handleBuffer(ByteBuffer buffer, int offset, int size, long presentationTimeUs)
            throws AudioTrack.WriteException {
        if (!mIsEnabled) {
            return AudioTrack.RESULT_BUFFER_CONSUMED;
        }
        return mAudioTrack.handleBuffer(buffer, offset, size, presentationTimeUs);
    }

    public void setStatus(boolean enable) {
        if (enable == mIsEnabled) {
            return;
        }
        mAudioTrack.reset();
        mIsEnabled = enable;
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    // This should be used only in case of being enabled.
    public long getCurrentPositionUs(boolean isEnded) {
        return mAudioTrack.getCurrentPositionUs(isEnded);
    }
}

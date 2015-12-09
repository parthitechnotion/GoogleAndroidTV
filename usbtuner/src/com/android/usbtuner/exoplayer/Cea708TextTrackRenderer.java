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

package com.android.usbtuner.exoplayer;

import android.util.Log;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;
import com.android.usbtuner.cc.Cea708Parser;
import com.android.usbtuner.cc.Cea708Parser.OnCea708ParserListener;
import com.android.usbtuner.data.Cea708Data.CaptionEvent;

import java.io.IOException;

/**
 * A {@link TrackRenderer} for CEA-708 textual subtitles.
 */
public class Cea708TextTrackRenderer extends TrackRenderer implements OnCea708ParserListener {
    private static final String TAG = "Cea708TextTrackRenderer";
    private static final boolean DEBUG = false;

    public static final int MSG_SERVICE_NUMBER = 1;

    // According to CEA-708B, the maximum value of closed caption bandwidth is 9600bps.
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 9600 / 8;

    private SampleSource mSource;
    private SampleHolder mSampleHolder;
    private int mServiceNumber;
    private boolean mSourceStateReady;
    private boolean mInputStreamEnded;
    private long mCurrentPositionUs;
    private long mPresentationTimeUs;
    private int mTrackIndex;
    private Cea708Parser mCea708Parser;
    private CcListener mCcListener;

    public interface CcListener {
        void emitEvent(CaptionEvent captionEvent);
        void discoverServiceNumber(int serviceNumber);
    }

    public Cea708TextTrackRenderer(SampleSource source) {
        mSource = source;
        mSampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
        mSampleHolder.replaceBuffer(DEFAULT_INPUT_BUFFER_SIZE);
    }

    @Override
    protected boolean isTimeSource() {
        return false;
    }

    private boolean handlesMimeType(String mimeType) {
        return mimeType.equals(MpegTsSampleSourceExtractor.MIMETYPE_TEXT_CEA_708);
    }

    @Override
    protected int doPrepare(long positionUs) throws ExoPlaybackException {
        try {
            boolean sourcePrepared = mSource.prepare(positionUs);
            if (!sourcePrepared) {
                return TrackRenderer.STATE_UNPREPARED;
            }
        } catch (IOException e) {
            throw new ExoPlaybackException(e);
        }
        int trackCount = mSource.getTrackCount();
        for (int i = 0; i < trackCount; ++i) {
            TrackInfo trackInfo = mSource.getTrackInfo(i);
            if (handlesMimeType(trackInfo.mimeType)) {
                mTrackIndex = i;
                clearDecodeState();
                return TrackRenderer.STATE_PREPARED;
            }
        }
        return TrackRenderer.STATE_IGNORE;
    }

    @Override
    protected void onEnabled(long positionUs, boolean joining) {
        mSource.enable(mTrackIndex, positionUs);
        mSourceStateReady = false;
        mInputStreamEnded = false;
        mPresentationTimeUs = positionUs;
        mCurrentPositionUs = Long.MIN_VALUE;
    }

    @Override
    protected void onDisabled() {
        mSource.disable(mTrackIndex);
    }

    @Override
    protected void onReleased() {
        mSource.release();
        mCea708Parser = null;
    }

    @Override
    protected boolean isEnded() {
        return mInputStreamEnded;
    }

    @Override
    protected boolean isReady() {
        return mSourceStateReady;
    }

    @Override
    protected void doSomeWork(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        try {
            boolean continueBuffering = mSource.continueBuffering(positionUs);
            if (mSourceStateReady != continueBuffering) {
                mSourceStateReady = continueBuffering;
                if (DEBUG) {
                    Log.d(TAG, "mSourceStateReady: " + mSourceStateReady);
                }
            }
            mPresentationTimeUs = positionUs;
            if (!mInputStreamEnded) {
                processOutput();
                feedInputBuffer();
            }
        } catch (IOException e) {
            throw new ExoPlaybackException(e);
        }
    }

    private boolean processOutput() {
        if (mInputStreamEnded) {
            return false;
        }
        return mCea708Parser != null && mCea708Parser.processClosedCaptions(mPresentationTimeUs);
    }

    private boolean feedInputBuffer() throws IOException, ExoPlaybackException {
        if (mInputStreamEnded) {
            return false;
        }
        mSampleHolder.data.clear();
        mSampleHolder.size = 0;
        int result = mSource.readData(mTrackIndex, mPresentationTimeUs, null, mSampleHolder, false);
        switch (result) {
            case SampleSource.NOTHING_READ: {
                return false;
            }
            case SampleSource.DISCONTINUITY_READ: {
                if (DEBUG) {
                    Log.d(TAG, "Read discontinuity happened");
                }

                // TODO: handle input discontinuity for trickplay.
                clearDecodeState();
                return true;
            }
            case SampleSource.FORMAT_READ: {
                if (DEBUG) {
                    Log.i(TAG, "Format was read again");
                }
                return true;
            }
            case SampleSource.END_OF_STREAM: {
                if (DEBUG) {
                    Log.i(TAG, "End of stream from SampleSource");
                }
                mInputStreamEnded = true;
                return false;
            }
            case SampleSource.SAMPLE_READ: {
                mSampleHolder.data.flip();
                if (mCea708Parser != null) {
                    mCea708Parser.parseClosedCaption(mSampleHolder.data, mSampleHolder.timeUs);
                }
                return true;
            }
        }
        return false;
    }

    private void clearDecodeState() {
        mCea708Parser = new Cea708Parser();
        mCea708Parser.setListener(this);
        mCea708Parser.setListenServiceNumber(mServiceNumber);
    }

    @Override
    protected long getDurationUs() {
        return mSource.getTrackInfo(mTrackIndex).durationUs;
    }

    @Override
    protected long getCurrentPositionUs() {
        mCurrentPositionUs = Math.max(mCurrentPositionUs, mPresentationTimeUs);
        return mCurrentPositionUs;
    }

    @Override
    protected long getBufferedPositionUs() {
        long positionUs = mSource.getBufferedPositionUs();
        return positionUs == UNKNOWN_TIME_US || positionUs == END_OF_TRACK_US
                ? positionUs : Math.max(positionUs, getCurrentPositionUs());
    }

    @Override
    protected void seekTo(long currentPositionUs) throws ExoPlaybackException {
        mSource.seekToUs(currentPositionUs);
        mSourceStateReady = false;
        mInputStreamEnded = false;
        mPresentationTimeUs = currentPositionUs;
        mCurrentPositionUs = Long.MIN_VALUE;
    }

    @Override
    protected void onStarted() {
        // do nothing.
    }

    @Override
    protected void onStopped() {
        // do nothing.
    }

    private void setServiceNumber(int serviceNumber) {
        mServiceNumber = serviceNumber;
        if (mCea708Parser != null) {
            mCea708Parser.setListenServiceNumber(serviceNumber);
        }
    }

    @Override
    public void emitEvent(CaptionEvent event) {
        if (mCcListener != null) {
            mCcListener.emitEvent(event);
        }
    }

    @Override
    public void discoverServiceNumber(int serviceNumber) {
        if (mCcListener != null) {
            mCcListener.discoverServiceNumber(serviceNumber);
        }
    }

    public void setCcListener(CcListener ccListener) {
        mCcListener = ccListener;
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        if (messageType == MSG_SERVICE_NUMBER) {
            setServiceNumber((int) message);
        } else {
            super.handleMessage(messageType, message);
        }
    }
}

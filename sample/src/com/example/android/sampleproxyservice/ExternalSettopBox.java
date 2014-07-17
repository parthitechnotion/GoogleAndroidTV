package com.example.android.sampleproxyservice;

import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

/**
 * Represents a external set-top box connected to a pass-through TV input.
 */
public class ExternalSettopBox {
    private static final String TAG = ExternalSettopBox.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final String DEFAULT_URL =
            "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8";
    private static ExternalSettopBox sInstance;

    private MediaPlayer mPlayer;
    private String mUrl;
    private Surface mSurface;

    private ExternalSettopBox() {
        mUrl = DEFAULT_URL;
    }

    public synchronized static ExternalSettopBox getInstance() {
        if (sInstance == null) {
            sInstance = new ExternalSettopBox();
        }
        return sInstance;
    }

    public synchronized void setSurface(Surface surface) {
        if (DEBUG) Log.d(TAG, "setSurface(" + surface + ")");
        mSurface = surface;
        resetPlayback();
    }

    public synchronized void setVolume(float volume) {
        if (DEBUG) Log.d(TAG, "setVolume(" + volume + ")");
        mPlayer.setVolume(volume, volume);
    }

    public synchronized void tune(String url) {
        if (DEBUG) Log.d(TAG, "tune(" + url + ")");
        mUrl = url;
        resetPlayback();
    }

    private void resetPlayback() {
        if (DEBUG) Log.d(TAG, "resetPlayback(" + mUrl + ")");
        if (mPlayer != null) {
            if (DEBUG) Log.d(TAG, "Stopping");
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        try {
            if (mSurface != null) {
                if (DEBUG) Log.d(TAG, "Starting " + mUrl);
                mPlayer = new MediaPlayer();
                mPlayer.setSurface(mSurface);
                mPlayer.setDataSource(mUrl);
                mPlayer.prepare();
                mPlayer.setLooping(true);
                mPlayer.start();
            }
        } catch (IllegalArgumentException | SecurityException | IllegalStateException
                | IOException e) {
            Log.e(TAG, "Failed to play: " + mUrl);
        }
    }
}

package com.android.tv.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;
import android.tv.TvView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import com.android.internal.util.Preconditions;
import com.android.tv.data.Channel;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

public class TunableTvView extends TvView {
    private static final boolean DEBUG = true;
    private static final String TAG = "TunableTvView";

    private static final int DELAY_FOR_SURFACE_RELEASE = 300;
    private static final int MSG_TUNE = 0;

    private float mVolume;
    private long mChannelId = Channel.INVALID_ID;
    private TvInputManagerHelper mInputManagerHelper;
    private boolean mStarted;
    private TvInputInfo mInputInfo;
    private TvInputManager.Session mSession;
    private OnTuneListener mOnTuneListener;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TUNE:
                    Preconditions.checkState(mChannelId != Channel.INVALID_ID);
                    Preconditions.checkNotNull(mSession);

                    mSession.tune(Utils.getChannelUri(mChannelId));
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onTuned(true, mChannelId);
                    }
                    break;
            }
        }
    };

    private final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

        @Override
        public void surfaceCreated(SurfaceHolder holder) { }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                if (DEBUG) Log.d(TAG, "Sleep to wait destroying a surface");
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
                if (DEBUG) Log.d(TAG, "Wake up from sleeping");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private final TvInputManager.SessionCallback mSessionCallback =
            new TvInputManager.SessionCallback() {
                @Override
                public void onSessionCreated(TvInputManager.Session session) {
                    if (session != null) {
                        mSession = session;
                        mSession.setVolume(mVolume);
                        mHandler.sendEmptyMessage(MSG_TUNE);
                    } else {
                        Log.w(TAG, "Failed to create a session");
                        long channelId = mChannelId;
                        mChannelId = Channel.INVALID_ID;
                        mInputInfo = null;
                        mSession = null;
                        if (mOnTuneListener != null) {
                            mOnTuneListener.onTuned(false, channelId);
                            mOnTuneListener = null;
                        }
                    }
                }

                @Override
                public void onSessionReleased(TvInputManager.Session session) {
                    Log.w(TAG, "Session is released by crash");
                    long channelId = mChannelId;
                    mChannelId = Channel.INVALID_ID;
                    mInputInfo = null;
                    mSession = null;
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onUnexpectedStop(channelId);
                        mOnTuneListener = null;
                    }
                }
            };

    public TunableTvView(Context context) {
        this(context, null, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(mSurfaceHolderCallback);
    }

    public void start(TvInputManagerHelper tvInputManagerHelper) {
        mInputManagerHelper = tvInputManagerHelper;
        if (mStarted) {
            return;
        }
        mStarted = true;
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mHandler.removeMessages(MSG_TUNE);
        mStarted = false;
        mSession = null;
        unbindTvInput();
        mChannelId = Channel.INVALID_ID;
        mInputInfo = null;
        mOnTuneListener = null;
    }

    public boolean isPlaying() {
        return mStarted;
    }

    public boolean tuneTo(long channelId, OnTuneListener listener) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG) Log.d(TAG, "tuneTo " + channelId);
        mHandler.removeMessages(MSG_TUNE);
        String inputId = Utils.getInputIdForChannel(getContext(), channelId);
        TvInputInfo inputInfo = mInputManagerHelper.getTvInputInfo(inputId);
        if (inputInfo == null || !mInputManagerHelper.isAvailable(inputInfo)) {
            return false;
        }
        mOnTuneListener = listener;
        mChannelId = channelId;
        if (!inputInfo.equals(mInputInfo)) {
            mSession = null;
            unbindTvInput();

            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mInputInfo = inputInfo;
            bindTvInput(mInputInfo.getId(), mSessionCallback);
            // mChannelId will be tuned after onSessionCreated.
        } else {
            if (mSession == null) {
                // onSessionCreated is not called yet. MSG_TUNE will be sent in onSessionCreated.
            } else {
                mHandler.sendEmptyMessage(MSG_TUNE);
            }
        }
        return true;
    }

    public TvInputInfo getCurrentTvInputInfo() {
        return mInputInfo;
    }

    public long getCurrentChannelId() {
        return mChannelId;
    }

    public void setVolume(float volume) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG) Log.d(TAG, "setVolume " + volume);
        mVolume = volume;
        if (mSession != null) {
            mSession.setVolume(volume);
        }
    }

    public interface OnTuneListener {
        void onTuned(boolean success, long channelId);
        void onUnexpectedStop(long channelId);
    }
}

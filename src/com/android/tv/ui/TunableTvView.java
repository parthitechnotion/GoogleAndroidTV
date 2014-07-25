package com.android.tv.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.StreamInfo;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.List;

public class TunableTvView extends TvView implements StreamInfo {
    private static final boolean DEBUG = true;
    private static final String TAG = "TunableTvView";

    private static final int DELAY_FOR_SURFACE_RELEASE = 300;
    public static final String PERMISSION_RECEIVE_INPUT_EVENT =
            "android.permission.RECEIVE_INPUT_EVENT";

    private long mChannelId = Channel.INVALID_ID;
    private TvInputManagerHelper mInputManagerHelper;
    private boolean mStarted;
    private TvInputInfo mInputInfo;
    private OnTuneListener mOnTuneListener;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
    private int mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
    private boolean mHasClosedCaption = false;
    private boolean mIsVideoAvailable;
    private int mVideoUnavailableReason;
    private SurfaceView mSurface;
    private boolean mCanReceiveInputEvent;

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

    private final TvInputListener mListener =
            new TvInputListener() {
                @Override
                public void onError(String inputId, int errorCode) {
                    if (errorCode == TvView.ERROR_BUSY) {
                        Log.w(TAG, "Failed to bind an input");
                        long channelId = mChannelId;
                        mChannelId = Channel.INVALID_ID;
                        mInputInfo = null;
                        mCanReceiveInputEvent = false;
                        if (mOnTuneListener != null) {
                            mOnTuneListener.onTuned(false, channelId);
                            mOnTuneListener = null;
                        }
                    } else if (errorCode == TvView.ERROR_TV_INPUT_DISCONNECTED) {
                        Log.w(TAG, "Session is released by crash");
                        long channelId = mChannelId;
                        mChannelId = Channel.INVALID_ID;
                        mInputInfo = null;
                        mCanReceiveInputEvent = false;
                        if (mOnTuneListener != null) {
                            mOnTuneListener.onUnexpectedStop(channelId);
                            mOnTuneListener = null;
                        }
                    }
                }

                @Override
                public void onChannelRetuned(String inputId, Uri channelUri) {
                    if (DEBUG) {
                        Log.d(TAG, "onChannelRetuned(inputId=" + inputId + ", channelUri="
                                + channelUri + ")");
                    }
                    // TODO: update {@code mChannelId}.
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onChannelChanged(channelUri);
                    }
                }

                @Override
                public void onTrackInfoChanged(String inputId, List<TvTrackInfo> tracks) {
                    for (TvTrackInfo track : tracks) {
                        int type = track.getInt(TvTrackInfo.KEY_TYPE);
                        boolean selected = track.getBoolean(TvTrackInfo.KEY_IS_SELECTED);
                        if (type == TvTrackInfo.VALUE_TYPE_VIDEO && selected) {
                            mVideoWidth = track.getInt(TvTrackInfo.KEY_WIDTH);
                            mVideoHeight = track.getInt(TvTrackInfo.KEY_HEIGHT);
                            mVideoFormat = Utils.getVideoDefinitionLevelFromSize(
                                    mVideoWidth, mVideoHeight);
                        } else if (type == TvTrackInfo.VALUE_TYPE_AUDIO && selected) {
                            mAudioChannelCount = track.getInt(TvTrackInfo.KEY_CHANNEL_COUNT);
                        } else if (type == TvTrackInfo.VALUE_TYPE_SUBTITLE) {
                            mHasClosedCaption = true;
                        }
                    }
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }

                @Override
                public void onVideoAvailable(String inputId) {
                    mIsVideoAvailable = true;
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }

                @Override
                public void onVideoUnavailable(String inputId, int reason) {
                    mIsVideoAvailable = false;
                    mVideoUnavailableReason = reason;
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
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
        for (int i = 0; i < getChildCount(); ++i) {
            if (getChildAt(i) instanceof SurfaceView) {
                mSurface = (SurfaceView) getChildAt(i);
                mSurface.getHolder().addCallback(mSurfaceHolderCallback);
                return;
            }
        }
        throw new RuntimeException("TvView does not have SurfaceView.");
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
        mStarted = false;
        reset();
        mChannelId = Channel.INVALID_ID;
        mInputInfo = null;
        mCanReceiveInputEvent = false;
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
        mVideoWidth = 0;
        mVideoHeight = 0;
        mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
        mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
        mHasClosedCaption = false;
        String inputId = Utils.getInputIdForChannel(getContext(), channelId);
        TvInputInfo inputInfo = mInputManagerHelper.getTvInputInfo(inputId);
        if (inputInfo == null
                || mInputManagerHelper.getInputState(inputInfo) ==
                        TvInputManager.INPUT_STATE_DISCONNECTED) {
            return false;
        }
        mOnTuneListener = listener;
        mChannelId = channelId;
        if (!inputInfo.equals(mInputInfo)) {
            reset();
            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mInputInfo = inputInfo;
            mCanReceiveInputEvent = mContext.getPackageManager().checkPermission(
                    PERMISSION_RECEIVE_INPUT_EVENT, mInputInfo.getComponent().getPackageName())
                            == PackageManager.PERMISSION_GRANTED;
        }
        setTvInputListener(mListener);
        tune(mInputInfo.getId(), Utils.getChannelUri(mChannelId));
        if (mOnTuneListener != null) {
            // TODO: Add a callback for tune complete and call onTuned when it was successful.
            mOnTuneListener.onTuned(true, mChannelId);
        }
        return true;
    }

    @Override
    public TvInputInfo getCurrentTvInputInfo() {
        return mInputInfo;
    }

    public long getCurrentChannelId() {
        return mChannelId;
    }

    public void setPip(boolean isPip) {
        mSurface.setZOrderMediaOverlay(isPip);
    }

    @Override
    public void setStreamVolume(float volume) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG)
            Log.d(TAG, "setStreamVolume " + volume);
        super.setStreamVolume(volume);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mCanReceiveInputEvent && super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mCanReceiveInputEvent && super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mCanReceiveInputEvent && super.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return mCanReceiveInputEvent && super.dispatchGenericMotionEvent(event);
    }

    public interface OnTuneListener {
        void onTuned(boolean success, long channelId);
        void onUnexpectedStop(long channelId);
        void onStreamInfoChanged(StreamInfo info);
        void onChannelChanged(Uri channel);
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public int getVideoDefinitionLevel() {
        return mVideoFormat;
    }

    @Override
    public int getAudioChannelCount() {
        return mAudioChannelCount;
    }

    @Override
    public boolean hasClosedCaption() {
        return mHasClosedCaption;
    }

    @Override
    public boolean isVideoAvailable() {
        return mIsVideoAvailable;
    }

    @Override
    public int getVideoUnavailableReason() {
        return mVideoUnavailableReason;
    }
}

package com.android.tv.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.TvView.OnUnhandledInputEventListener;
import android.media.tv.TvView.TvInputListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.StreamInfo;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.List;

public class TunableTvView extends FrameLayout implements StreamInfo {
    // STOPSHIP: Turn debugging off
    private static final boolean DEBUG = true;
    private static final String TAG = "TunableTvView";

    private static final int DELAY_FOR_SURFACE_RELEASE = 300;
    public static final String PERMISSION_RECEIVE_INPUT_EVENT =
            "android.permission.RECEIVE_INPUT_EVENT";

    private final TvView mTvView;
    private final SurfaceView mSurfaceView;
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
    private boolean mCanReceiveInputEvent;
    private boolean mIsMuted;
    private float mVolume;

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
                    if (errorCode == TvView.ERROR_INPUT_NOT_CONNECTED) {
                        Log.w(TAG, "Failed to bind an input");
                        long channelId = mChannelId;
                        mChannelId = Channel.INVALID_ID;
                        mInputInfo = null;
                        mCanReceiveInputEvent = false;
                        if (mOnTuneListener != null) {
                            mOnTuneListener.onTuned(false, channelId);
                            mOnTuneListener = null;
                        }
                    } else if (errorCode == TvView.ERROR_INPUT_DISCONNECTED) {
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
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }

                @Override
                public void onTrackSelectionChanged(String inputId,
                        List<TvTrackInfo> selectedTracks) {
                    for (TvTrackInfo track : selectedTracks) {
                        int type = track.getType();
                        if (type == TvTrackInfo.TYPE_VIDEO) {
                            mVideoWidth = track.getVideoWidth();
                            mVideoHeight = track.getVideoHeight();
                            mVideoFormat = Utils.getVideoDefinitionLevelFromSize(
                                    mVideoWidth, mVideoHeight);
                        } else if (type == TvTrackInfo.TYPE_AUDIO) {
                            mAudioChannelCount = track.getAudioChannelCount();
                        } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
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
                    unblock();
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }

                @Override
                public void onVideoUnavailable(String inputId, int reason) {
                    mIsVideoAvailable = false;
                    mVideoUnavailableReason = reason;
                    block(reason);
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }
            };

    public TunableTvView(Context context) {
        this(context, null);
    }

    public TunableTvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        inflate(getContext(), R.layout.tunable_tv_view, this);

        mTvView = (TvView) findViewById(R.id.tv_view);
        mSurfaceView = findSurfaceView(mTvView);
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
        mTvView.reset();
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
            mTvView.reset();
            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mInputInfo = inputInfo;
            mCanReceiveInputEvent = getContext().getPackageManager().checkPermission(
                    PERMISSION_RECEIVE_INPUT_EVENT, mInputInfo.getComponent().getPackageName())
                            == PackageManager.PERMISSION_GRANTED;
        }
        mTvView.setTvInputListener(mListener);
        mTvView.tune(mInputInfo.getId(), Utils.getChannelUri(mChannelId));
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
        mSurfaceView.setZOrderMediaOverlay(isPip);
    }

    public void setStreamVolume(float volume) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG) Log.d(TAG, "setStreamVolume " + volume);
        mVolume = volume;
        if (!mIsMuted) {
            mTvView.setStreamVolume(volume);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return mCanReceiveInputEvent && mTvView.dispatchGenericMotionEvent(event);
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

    public void setOnUnhandledInputEventListener(OnUnhandledInputEventListener listener) {
        mTvView.setOnUnhandledInputEventListener(listener);
    }

    public List<TvTrackInfo> getTracks() {
        return mTvView.getTracks();
    }

    public void selectTrack(TvTrackInfo track) {
        mTvView.selectTrack(track);
    }

    private void block(int reason) {
        hideBlock();
        switch (reason) {
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN:
            default:
                findViewById(R.id.block_reason_unknown).setVisibility(VISIBLE);
                break;
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING:
                findViewById(R.id.block_reason_tune).setVisibility(VISIBLE);
                break;
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL:
                findViewById(R.id.block_reason_weak_signal).setVisibility(VISIBLE);
                break;
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING:
                findViewById(R.id.block_reason_buffering).setVisibility(VISIBLE);
                break;
        }
        mute();
    }

    private void unblock() {
        hideBlock();
        unmute();
    }

    private void mute() {
        mIsMuted = true;
        mTvView.setStreamVolume(0);
    }

    private void unmute() {
        mIsMuted = false;
        mTvView.setStreamVolume(mVolume);
    }

    private SurfaceView findSurfaceView(ViewGroup view) {
        for (int i = 0; i < view.getChildCount(); ++i) {
            if (view.getChildAt(i) instanceof SurfaceView) {
                SurfaceView surfaceView = (SurfaceView) mTvView.getChildAt(i);
                surfaceView.getHolder().addCallback(mSurfaceHolderCallback);
                return surfaceView;
            }
        }
        throw new RuntimeException("TvView does not have SurfaceView.");
    }

    private void hideBlock() {
        findViewById(R.id.block_reason_unknown).setVisibility(GONE);
        findViewById(R.id.block_reason_tune).setVisibility(GONE);
        findViewById(R.id.block_reason_weak_signal).setVisibility(GONE);
        findViewById(R.id.block_reason_buffering).setVisibility(GONE);
    }
}

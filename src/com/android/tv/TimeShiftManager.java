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

package com.android.tv;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Range;

import com.android.tv.data.Channel;
import com.android.tv.data.OnCurrentProgramUpdatedListener;
import com.android.tv.data.Program;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.TimeShiftListener;
import com.android.tv.util.AsyncDbTask;
import com.android.tv.util.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * A class which manages the time shift feature in Live Channels. It consists of two parts.
 * {@link PlayController} controls the playback such as play/pause, rewind and fast-forward using
 * {@link TunableTvView} which communicates with TvInputService through
 * {@link android.media.tv.TvInputService.Session}.
 * {@link ProgramManager} loads programs of the current channel in the background.
 */
public class TimeShiftManager {
    private static final String TAG = "TimeShiftManager";
    private static final boolean DEBUG = false;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PLAY_STATUS_PAUSED, PLAY_STATUS_PLAYING})
    public @interface PlayStatus {}
    public static final int PLAY_STATUS_PAUSED  = 0;
    public static final int PLAY_STATUS_PLAYING = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PLAY_SPEED_1X, PLAY_SPEED_2X, PLAY_SPEED_3X, PLAY_SPEED_4X, PLAY_SPEED_5X})
    public @interface PlaySpeed{}
    public static final int PLAY_SPEED_1X = 1;
    public static final int PLAY_SPEED_2X = 2;
    public static final int PLAY_SPEED_3X = 3;
    public static final int PLAY_SPEED_4X = 4;
    public static final int PLAY_SPEED_5X = 5;

    private static final int SHORT_PROGRAM_THRESHOLD_MILLIS = 46 * 60 * 1000;  // 46 mins.
    private static final int[] SHORT_PROGRAM_SPEED_FACTORS = new int[] {2, 4, 12, 48};
    private static final int[] LONG_PROGRAM_SPEED_FACTORS = new int[] {2, 8, 32, 128};

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PLAY_DIRECTION_FORWARD, PLAY_DIRECTION_BACKWARD})
    public @interface PlayDirection{}
    public static final int PLAY_DIRECTION_FORWARD  = 0;
    public static final int PLAY_DIRECTION_BACKWARD = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TIME_SHIFT_ACTION_ID_PLAY, TIME_SHIFT_ACTION_ID_PAUSE, TIME_SHIFT_ACTION_ID_REWIND,
        TIME_SHIFT_ACTION_ID_FAST_FORWARD, TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS,
        TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT})
    public @interface TimeShiftActionId{}
    public static final int TIME_SHIFT_ACTION_ID_PLAY = 1;
    public static final int TIME_SHIFT_ACTION_ID_PAUSE = 1 << 1;
    public static final int TIME_SHIFT_ACTION_ID_REWIND = 1 << 2;
    public static final int TIME_SHIFT_ACTION_ID_FAST_FORWARD = 1 << 3;
    public static final int TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS = 1 << 4;
    public static final int TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT = 1 << 5;

    private static final int MSG_GET_CURRENT_POSITION = 1000;
    private static final int MSG_PREFETCH_PROGRAM = 1001;
    private static final long REQUEST_CURRENT_POSITION_INTERVAL = TimeUnit.SECONDS.toMillis(1);
    private static final long MAX_DUMMY_PROGRAM_DURATION = TimeUnit.MINUTES.toMillis(30);
    @VisibleForTesting
    static final long INVALID_TIME = -1;
    private static final long PREFETCH_TIME_OFFSET_FROM_PROGRAM_END = TimeUnit.MINUTES.toMillis(1);
    private static final long PREFETCH_DURATION_FOR_NEXT = TimeUnit.HOURS.toMillis(2);

    @VisibleForTesting
    static final long REQUEST_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(3);

    /**
     * If the user presses the {@link android.view.KeyEvent#KEYCODE_MEDIA_PREVIOUS} button within
     * this threshold from the program start time, the play position moves to the start of the
     * previous program.
     * Otherwise, the play position moves to the start of the current program.
     * This value is specified in the UX document.
     */
    private static final long PROGRAM_START_TIME_THRESHOLD = TimeUnit.SECONDS.toMillis(3);
    /**
     * If the current position enters within this range from the recording start time, rewind action
     * and jump to previous action is disabled.
     * Similarly, if the current position enters within this range from the current system time,
     * fast forward action and jump to next action is disabled.
     * It must be three times longer than {@link #REQUEST_CURRENT_POSITION_INTERVAL} at least.
     */
    private static final long DISABLE_ACTION_THRESHOLD = 3 * REQUEST_CURRENT_POSITION_INTERVAL;
    /**
     * If the current position goes out of this range from the recording start time, rewind action
     * and jump to previous action is enabled.
     * Similarly, if the current position goes out of this range from the current system time,
     * fast forward action and jump to next action is enabled.
     * Enable threshold and disable threshold must be different because the current position
     * does not have the continuous value. It changes every one second.
     */
    private static final long ENABLE_ACTION_THRESHOLD =
            DISABLE_ACTION_THRESHOLD + 3 * REQUEST_CURRENT_POSITION_INTERVAL;
    /**
     * The current position sent from TIS can not be exactly the same as the current system time
     * due to the elapsed time to pass the message from TIS to Live Channels.
     * So the boundary threshold is necessary.
     * The same goes for the recording start time.
     * It must be three times longer than {@link #REQUEST_CURRENT_POSITION_INTERVAL} at least.
     */
    private static final long RECORDING_BOUNDARY_THRESHOLD = 3 * REQUEST_CURRENT_POSITION_INTERVAL;

    private final PlayController mPlayController;
    private final ProgramManager mProgramManager;
    @VisibleForTesting
    final CurrentPositionMediator mCurrentPositionMediator = new CurrentPositionMediator();

    private Listener mListener;
    private final OnCurrentProgramUpdatedListener mOnCurrentProgramUpdatedListener;
    private int mEnabledActionIds = TIME_SHIFT_ACTION_ID_PLAY | TIME_SHIFT_ACTION_ID_PAUSE
            | TIME_SHIFT_ACTION_ID_REWIND | TIME_SHIFT_ACTION_ID_FAST_FORWARD
            | TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS | TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT;
    private int mLastActionId = 0;

    // TODO: Remove these variables once API level 23 is available.
    private final Context mContext;

    private Program mCurrentProgram;
    // This variable is used to block notification while changing the availability status.
    private boolean mNotificationEnabled;

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_CURRENT_POSITION:
                    mPlayController.handleGetCurrentPosition();
                    break;
                case MSG_PREFETCH_PROGRAM:
                    mProgramManager.prefetchPrograms();
                    break;
            }
        }
    };

    public TimeShiftManager(Context context, TunableTvView tvView,
            ProgramDataManager programDataManager,
            OnCurrentProgramUpdatedListener onCurrentProgramUpdatedListener) {
        mContext = context;
        mPlayController = new PlayController(tvView);
        mProgramManager = new ProgramManager(programDataManager);
        mOnCurrentProgramUpdatedListener = onCurrentProgramUpdatedListener;
        tvView.setOnScreenBlockedListener(new TunableTvView.OnScreenBlockingChangedListener() {
            @Override
            public void onScreenBlockingChanged(boolean blocked) {
                onAvailabilityChanged();
            }
        });
    }

    /**
     * Sets a listener which will receive events from this class.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Checks if the trick play is available for the current channel.
     */
    public boolean isAvailable() {
        return mPlayController.isAvailable();
    }

    /**
     * Returns the current time position in milliseconds.
     */
    public long getCurrentPositionMs() {
        return mCurrentPositionMediator.mCurrentPositionMs;
    }

    void setCurrentPositionMs(long currentTimeMs) {
        mCurrentPositionMediator.onCurrentPositionChanged(currentTimeMs);
    }

    /**
     * Returns the start time of the recording in milliseconds.
     */
    public long getRecordStartTimeMs() {
        long oldestProgramStartTime = mProgramManager.getOldestProgramStartTime();
        return oldestProgramStartTime == INVALID_TIME ? INVALID_TIME
                : mPlayController.mRecordStartTimeMs;
    }

    /**
     * Plays the media.
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void play() {
        if (!isActionEnabled(TIME_SHIFT_ACTION_ID_PLAY)) {
            return;
        }
        mLastActionId = TIME_SHIFT_ACTION_ID_PLAY;
        mPlayController.play();
        updateActions();
    }

    /**
     * Pauses the playback.
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void pause() {
        if (!isActionEnabled(TIME_SHIFT_ACTION_ID_PAUSE)) {
            return;
        }
        mLastActionId = TIME_SHIFT_ACTION_ID_PAUSE;
        mPlayController.pause();
        updateActions();
    }

    /**
     * Toggles the playing and paused state.
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void togglePlayPause() {
        mPlayController.togglePlayPause();
    }

    /**
     * Plays the media in backward direction. The playback speed is increased by 1x each time
     * this is called. The range of the speed is from 2x to 5x.
     * If the playing position is considered the same as the record start time, it does nothing
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void rewind() {
        if (!isActionEnabled(TIME_SHIFT_ACTION_ID_REWIND)) {
            return;
        }
        mLastActionId = TIME_SHIFT_ACTION_ID_REWIND;
        mPlayController.rewind();
        updateActions();
    }

    /**
     * Plays the media in forward direction. The playback speed is increased by 1x each time
     * this is called. The range of the speed is from 2x to 5x.
     * If the playing position is the same as the current time, it does nothing.
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void fastForward() {
        if (!isActionEnabled(TIME_SHIFT_ACTION_ID_FAST_FORWARD)) {
            return;
        }
        mLastActionId = TIME_SHIFT_ACTION_ID_FAST_FORWARD;
        mPlayController.fastForward();
        updateActions();
    }

    /**
     * Jumps to the start of the current program.
     * If the currently playing position is within 3 seconds
     * (={@link #PROGRAM_START_TIME_THRESHOLD})from the start time of the program, it goes to
     * the start of the previous program if exists.
     * If the playing position is the same as the record start time, it does nothing.
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void jumpToPrevious() {
        if (!isActionEnabled(TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS)) {
            return;
        }
        Program program = mProgramManager.getProgramAt(
                mCurrentPositionMediator.mCurrentPositionMs - PROGRAM_START_TIME_THRESHOLD);
        if (program == null) {
            return;
        }
        long seekPosition =
                Math.max(program.getStartTimeUtcMillis(), mPlayController.mRecordStartTimeMs);
        mLastActionId = TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS;
        mPlayController.seekTo(seekPosition);
        mCurrentPositionMediator.onSeekRequested(seekPosition);
        updateActions();
    }

    /**
     * Jumps to the start of the next program if exists.
     * If there's no next program, it jumps to the current system time and shows the live TV.
     * If the playing position is considered the same as the current time, it does nothing.
     *
     * @throws IllegalStateException if the trick play is not available.
     */
    public void jumpToNext() {
        if (!isActionEnabled(TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT)) {
            return;
        }
        Program currentProgram = mProgramManager.getProgramAt(
                mCurrentPositionMediator.mCurrentPositionMs);
        if (currentProgram == null) {
            return;
        }
        Program nextProgram = mProgramManager.getProgramAt(currentProgram.getEndTimeUtcMillis());
        long currentTimeMs = System.currentTimeMillis();
        mLastActionId = TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT;
        if (nextProgram == null || nextProgram.getStartTimeUtcMillis() > currentTimeMs) {
            mPlayController.seekTo(currentTimeMs);
            if (mPlayController.isForwarding()) {
                // The current position will be the current system time from now.
                mPlayController.mIsPlayOffsetChanged = false;
                mCurrentPositionMediator.initialize(currentTimeMs);
            } else {
                // The current position would not be the current system time.
                // So need to wait for the correct time from TIS.
                mCurrentPositionMediator.onSeekRequested(currentTimeMs);
            }
        } else {
            mPlayController.seekTo(nextProgram.getStartTimeUtcMillis());
            mCurrentPositionMediator.onSeekRequested(nextProgram.getStartTimeUtcMillis());
        }
        updateActions();
    }

    /**
     * Returns the playback status. The value is PLAY_STATUS_PAUSED or PLAY_STATUS_PLAYING.
     */
    @PlayStatus public int getPlayStatus() {
        return mPlayController.mPlayStatus;
    }

    /**
     * Returns the displayed playback speed. The value is one of PLAY_SPEED_1X, PLAY_SPEED_2X,
     * PLAY_SPEED_3X, PLAY_SPEED_4X and PLAY_SPEED_5X.
     */
    @PlaySpeed public int getDisplayedPlaySpeed() {
        return mPlayController.mDisplayedPlaySpeed;
    }

    /**
     * Returns the playback speed. The value is PLAY_DIRECTION_FORWARD or PLAY_DIRECTION_BACKWARD.
     */
    @PlayDirection public int getPlayDirection() {
        return mPlayController.mPlayDirection;
    }

    /**
     * Returns the ID of the last action..
     */
    @TimeShiftActionId public int getLastActionId() {
        return mLastActionId;
    }

    /**
     * Enables or disables the time-shift actions.
     */
    @VisibleForTesting
    void enableAction(@TimeShiftActionId int actionId, boolean enable) {
        int oldEnabledActionIds = mEnabledActionIds;
        if (enable) {
            mEnabledActionIds |= actionId;
        } else {
            mEnabledActionIds &= ~actionId;
        }
        if (mNotificationEnabled && mListener != null
                && oldEnabledActionIds != mEnabledActionIds) {
            mListener.onActionEnabledChanged(actionId, enable);
        }
    }

    public boolean isActionEnabled(@TimeShiftActionId int actionId) {
        return (mEnabledActionIds & actionId) == actionId;
    }

    private void updateActions() {
        if (isAvailable()) {
            enableAction(TIME_SHIFT_ACTION_ID_PLAY, true);
            enableAction(TIME_SHIFT_ACTION_ID_PAUSE, true);
            // Rewind action and jump to previous action.
            long threshold = isActionEnabled(TIME_SHIFT_ACTION_ID_REWIND)
                    ? DISABLE_ACTION_THRESHOLD : ENABLE_ACTION_THRESHOLD;
            boolean enabled = mCurrentPositionMediator.mCurrentPositionMs
                    - mPlayController.mRecordStartTimeMs > threshold;
            enableAction(TIME_SHIFT_ACTION_ID_REWIND, enabled);
            enableAction(TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS, enabled);
            // Fast forward action and jump to next action
            threshold = isActionEnabled(TIME_SHIFT_ACTION_ID_FAST_FORWARD)
                    ? DISABLE_ACTION_THRESHOLD : ENABLE_ACTION_THRESHOLD;
            enabled = System.currentTimeMillis() - mCurrentPositionMediator.mCurrentPositionMs
                    > threshold;
            enableAction(TIME_SHIFT_ACTION_ID_FAST_FORWARD, enabled);
            enableAction(TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT, enabled);
        } else {
            enableAction(TIME_SHIFT_ACTION_ID_PLAY, false);
            enableAction(TIME_SHIFT_ACTION_ID_PAUSE, false);
            enableAction(TIME_SHIFT_ACTION_ID_REWIND, false);
            enableAction(TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS, false);
            enableAction(TIME_SHIFT_ACTION_ID_FAST_FORWARD, false);
            enableAction(TIME_SHIFT_ACTION_ID_PLAY, false);
        }
    }

    private void updateCurrentProgram() {
        Program currentProgram = getProgramAt(mCurrentPositionMediator.mCurrentPositionMs);
        if (!Program.isValid(currentProgram)) {
            currentProgram = null;
        }
        if (!Objects.equals(mCurrentProgram, currentProgram)) {
            if (DEBUG) Log.d(TAG, "Current program has been updated. " + currentProgram);
            mCurrentProgram = currentProgram;
            if (mNotificationEnabled && mOnCurrentProgramUpdatedListener != null) {
                Channel channel = mPlayController.getCurrentChannel();
                if (channel != null) {
                    mOnCurrentProgramUpdatedListener.onCurrentProgramUpdated(channel.getId(),
                            mCurrentProgram);
                    mPlayController.onCurrentProgramChanged();
                }
            }
        }
    }

    /**
     * Returns {@code true} if the trick play is available and it's playing to the forward direction
     * with normal speed, otherwise {@code false}.
     */
    public boolean isNormalPlaying() {
        return mPlayController.isAvailable()
                && mPlayController.mPlayStatus == PLAY_STATUS_PLAYING
                && mPlayController.mPlayDirection == PLAY_DIRECTION_FORWARD
                && mPlayController.mDisplayedPlaySpeed == PLAY_SPEED_1X;
    }

    /**
     * Checks if the trick play is available and it's playback status is paused.
     */
    public boolean isPaused() {
        return mPlayController.isAvailable() && mPlayController.mPlayStatus == PLAY_STATUS_PAUSED;
    }

    /**
     * Returns the program which airs at the given time.
     */
    @NonNull
    public Program getProgramAt(long timeMs) {
        Program program = mProgramManager.getProgramAt(timeMs);
        if (program == null) {
            // Guard just in case when the program prefetch handler doesn't work on time.
            mProgramManager.addDummyProgramsAt(timeMs);
            program = mProgramManager.getProgramAt(timeMs);
        }
        return program;
    }

    void onAvailabilityChanged() {
        mProgramManager.onAvailabilityChanged(mPlayController.isAvailable(),
                mPlayController.getCurrentChannel(), mPlayController.mRecordStartTimeMs);
        updateActions();
        // Availability change notification should be always sent
        // even if mNotificationEnabled is false.
        if (mListener != null) {
            mListener.onAvailabilityChanged();
        }
    }

    void onRecordStartTimeChanged() {
        if (mPlayController.isAvailable()) {
            mProgramManager.onRecordStartTimeChanged(mPlayController.mRecordStartTimeMs);
        }
        updateActions();
        if (mNotificationEnabled && mListener != null) {
            mListener.onRecordStartTimeChanged();
        }
    }

    void onCurrentPositionChanged() {
        updateActions();
        updateCurrentProgram();
        if (mNotificationEnabled && mListener != null) {
            mListener.onCurrentPositionChanged();
        }
    }

    void onPlayStatusChanged(@PlayStatus int status) {
        if (mNotificationEnabled && mListener != null) {
            mListener.onPlayStatusChanged(status);
        }
    }

    void onProgramInfoChanged() {
        updateCurrentProgram();
        if (mNotificationEnabled && mListener != null) {
            mListener.onProgramInfoChanged();
        }
    }

    /**
     * Returns the current program which airs right now.<p>
     *
     * If the program is a dummy program, which means there's no program information,
     * returns {@code null}.
     */
    @Nullable
    public Program getCurrentProgram() {
        if (isAvailable()) {
            return mCurrentProgram;
        }
        return null;
    }

    private int getPlaybackSpeed() {
        int[] playbackSpeedList;
        if (getCurrentProgram() == null || getCurrentProgram().getEndTimeUtcMillis()
                - getCurrentProgram().getStartTimeUtcMillis() > SHORT_PROGRAM_THRESHOLD_MILLIS) {
            playbackSpeedList = LONG_PROGRAM_SPEED_FACTORS;
        } else {
            playbackSpeedList = SHORT_PROGRAM_SPEED_FACTORS;
        }
        switch (mPlayController.mDisplayedPlaySpeed) {
            case PLAY_SPEED_1X:
                return 1;
            case PLAY_SPEED_2X:
                return playbackSpeedList[0];
            case PLAY_SPEED_3X:
                return playbackSpeedList[1];
            case PLAY_SPEED_4X:
                return playbackSpeedList[2];
            case PLAY_SPEED_5X:
                return playbackSpeedList[3];
            default:
                Log.w(TAG, "Unknown displayed play speed is chosen : "
                        + mPlayController.mDisplayedPlaySpeed);
                return 1;
        }
    }

    /**
     * A class which controls the trick play.
     */
    private class PlayController {
        private final TunableTvView mTvView;

        private long mPossibleStartTimeMs;
        private long mRecordStartTimeMs;

        @PlayStatus private int mPlayStatus = PLAY_STATUS_PAUSED;
        @PlaySpeed private int mDisplayedPlaySpeed = PLAY_SPEED_1X;
        @PlayDirection private int mPlayDirection = PLAY_DIRECTION_FORWARD;
        private int mPlaybackSpeed;

        /**
         * Indicates that the trick play is not playing the current time position.
         * It is set true when {@link PlayController#pause}, {@link PlayController#rewind},
         * {@link PlayController#fastForward} and {@link PlayController#seekTo}
         * is called.
         * If it is true, the current time is equal to System.currentTimeMillis().
         */
        private boolean mIsPlayOffsetChanged;

        PlayController(TunableTvView tvView) {
            mTvView = tvView;
            mTvView.setTimeShiftListener(new TimeShiftListener() {
                @Override
                public void onAvailabilityChanged() {
                    // Do not send the notifications while the availability is changing,
                    // because the variables are in the intermediate state.
                    // For example, the current program can be null.
                    mNotificationEnabled = false;
                    mDisplayedPlaySpeed = PLAY_SPEED_1X;
                    mPlaybackSpeed = 1;
                    mPlayDirection = PLAY_DIRECTION_FORWARD;
                    mIsPlayOffsetChanged = false;
                    mPossibleStartTimeMs = System.currentTimeMillis();
                    mRecordStartTimeMs = mPossibleStartTimeMs;
                    mCurrentPositionMediator.initialize(mPossibleStartTimeMs);
                    mHandler.removeMessages(MSG_GET_CURRENT_POSITION);

                    if (isAvailable()) {
                        // When the media availability message has come.
                        mPlayController.setPlayStatus(PLAY_STATUS_PLAYING);
                        mHandler.sendEmptyMessageDelayed(MSG_GET_CURRENT_POSITION,
                                REQUEST_CURRENT_POSITION_INTERVAL);
                    } else {
                        // When the tune command is sent.
                        mPlayController.setPlayStatus(PLAY_STATUS_PAUSED);
                    }
                    TimeShiftManager.this.onAvailabilityChanged();
                    mNotificationEnabled = true;
                }

                @Override
                public void onRecordStartTimeChanged(long recordStartTimeMs) {
                    if (recordStartTimeMs < mPossibleStartTimeMs) {
                        // Do not warn in this case because it can happen in normal cases.
                        if (DEBUG) {
                            Log.d(TAG, "Record start time is less then the time when it became "
                                    + "available. {availableStartTime="
                                    + Utils.toTimeString(mPossibleStartTimeMs)
                                    + ", recordStartTimeMs=" + Utils.toTimeString(recordStartTimeMs)
                                    + "}");
                        }
                        recordStartTimeMs = mPossibleStartTimeMs;
                    }
                    if (mRecordStartTimeMs == recordStartTimeMs) {
                        return;
                    }
                    mRecordStartTimeMs = recordStartTimeMs;
                    TimeShiftManager.this.onRecordStartTimeChanged();

                    // According to the UX guidelines, the stream should be resumed if the
                    // recording buffer fills up while paused, which means that the current time
                    // position is the same as or before the recording start time.
                    // But, for this application and the TIS, it's an erroneous and confusing
                    // situation if the current time position is before the recording start time.
                    // So, we recommend the TIS to keep the current time position greater than or
                    // equal to the recording start time.
                    // And here, we assume that the buffer is full if the current time position
                    // is nearly equal to the recording start time.
                    if (mPlayStatus == PLAY_STATUS_PAUSED &&
                            getCurrentPositionMs() - mRecordStartTimeMs
                            < RECORDING_BOUNDARY_THRESHOLD) {
                        TimeShiftManager.this.play();
                    }
                }
            });
        }

        boolean isAvailable() {
            return mTvView.isTimeShiftAvailable() && !mTvView.isScreenBlocked();
        }

        void handleGetCurrentPosition() {
            if (mIsPlayOffsetChanged) {
                long currentTimeMs = System.currentTimeMillis();
                long currentPositionMs = Math.max(
                        Math.min(mTvView.timeshiftGetCurrentPositionMs(), currentTimeMs),
                        mRecordStartTimeMs);
                boolean isCurrentTime =
                        currentTimeMs - currentPositionMs < RECORDING_BOUNDARY_THRESHOLD;
                long newCurrentPositionMs;
                if (isCurrentTime && isForwarding()) {
                    // It's playing forward and the current playing position reached
                    // the current system time. i.e. The live stream is played.
                    // Therefore no need to call TvView.timeshiftGetCurrentPositionMs
                    // any more.
                    newCurrentPositionMs = currentTimeMs;
                    mIsPlayOffsetChanged = false;
                    if (mDisplayedPlaySpeed > PLAY_SPEED_1X) {
                        TimeShiftManager.this.play();
                    }
                } else {
                    newCurrentPositionMs = currentPositionMs;
                    boolean isRecordStartTime = currentPositionMs - mRecordStartTimeMs
                            < RECORDING_BOUNDARY_THRESHOLD;
                    if (isRecordStartTime && isRewinding()) {
                        TimeShiftManager.this.play();
                    }
                }
                setCurrentPositionMs(newCurrentPositionMs);
            } else {
                setCurrentPositionMs(System.currentTimeMillis());
                TimeShiftManager.this.onCurrentPositionChanged();
            }
            // Need to send message here just in case there is no or invalid response
            // for the current time position request from TIS.
            mHandler.sendEmptyMessageDelayed(MSG_GET_CURRENT_POSITION,
                    REQUEST_CURRENT_POSITION_INTERVAL);
        }

        void play() {
            mDisplayedPlaySpeed = PLAY_SPEED_1X;
            mPlaybackSpeed = 1;
            mPlayDirection = PLAY_DIRECTION_FORWARD;
            mTvView.timeshiftPlay();
            setPlayStatus(PLAY_STATUS_PLAYING);
        }

        void pause() {
            mDisplayedPlaySpeed = PLAY_SPEED_1X;
            mPlaybackSpeed = 1;
            mTvView.timeshiftPause();
            setPlayStatus(PLAY_STATUS_PAUSED);
            mIsPlayOffsetChanged = true;
        }

        void togglePlayPause() {
            if (mPlayStatus == PLAY_STATUS_PAUSED) {
                play();
            } else {
                pause();
            }
        }

        void rewind() {
            if (mPlayDirection == PLAY_DIRECTION_BACKWARD) {
                increaseDisplayedPlaySpeed();
            } else {
                mDisplayedPlaySpeed = PLAY_SPEED_2X;
            }
            mPlayDirection = PLAY_DIRECTION_BACKWARD;
            mPlaybackSpeed = getPlaybackSpeed();
            mTvView.timeshiftRewind(mPlaybackSpeed);
            setPlayStatus(PLAY_STATUS_PLAYING);
            mIsPlayOffsetChanged = true;
        }

        void fastForward() {
            if (mPlayDirection == PLAY_DIRECTION_FORWARD) {
                increaseDisplayedPlaySpeed();
            } else {
                mDisplayedPlaySpeed = PLAY_SPEED_2X;
            }
            mPlayDirection = PLAY_DIRECTION_FORWARD;
            mPlaybackSpeed = getPlaybackSpeed();
            mTvView.timeshiftFastForward(mPlaybackSpeed);
            setPlayStatus(PLAY_STATUS_PLAYING);
            mIsPlayOffsetChanged = true;
        }

        /**
         * Moves to the specified time.
         */
        void seekTo(long timeMs) {
            mTvView.timeshiftSeekTo(Math.min(System.currentTimeMillis(),
                    Math.max(mRecordStartTimeMs, timeMs)));
            mIsPlayOffsetChanged = true;
        }

        void onCurrentProgramChanged() {
            // Update playback speed
            if (mDisplayedPlaySpeed == PLAY_SPEED_1X) {
                return;
            }
            int playbackSpeed = getPlaybackSpeed();
            if (playbackSpeed != mPlaybackSpeed) {
                mPlaybackSpeed = playbackSpeed;
                if (mPlayDirection == PLAY_DIRECTION_FORWARD) {
                    mTvView.timeshiftFastForward(mPlaybackSpeed);
                } else {
                    mTvView.timeshiftRewind(mPlaybackSpeed);
                }
            }
        }

        private void increaseDisplayedPlaySpeed() {
            switch (mDisplayedPlaySpeed) {
                case PLAY_SPEED_1X:
                    mDisplayedPlaySpeed = PLAY_SPEED_2X;
                    break;
                case PLAY_SPEED_2X:
                    mDisplayedPlaySpeed = PLAY_SPEED_3X;
                    break;
                case PLAY_SPEED_3X:
                    mDisplayedPlaySpeed = PLAY_SPEED_4X;
                    break;
                case PLAY_SPEED_4X:
                    mDisplayedPlaySpeed = PLAY_SPEED_5X;
                    break;
            }
        }

        private void setPlayStatus(@PlayStatus int status) {
            mPlayStatus = status;
            TimeShiftManager.this.onPlayStatusChanged(status);
        }

        boolean isForwarding() {
            return mPlayStatus == PLAY_STATUS_PLAYING && mPlayDirection == PLAY_DIRECTION_FORWARD;
        }

        private boolean isRewinding() {
            return mPlayStatus == PLAY_STATUS_PLAYING && mPlayDirection == PLAY_DIRECTION_BACKWARD;
        }

        Channel getCurrentChannel() {
            return mTvView.getCurrentChannel();
        }
    }

    private class ProgramManager {
        private final ProgramDataManager mProgramDataManager;
        private Channel mChannel;
        private final List<Program> mPrograms = new ArrayList<>();
        private final Queue<Range<Long>> mProgramLoadQueue = new LinkedList<>();
        private LoadProgramsForCurrentChannelTask mProgramLoadTask = null;

        ProgramManager(ProgramDataManager programDataManager) {
            mProgramDataManager = programDataManager;
        }

        void onAvailabilityChanged(boolean available, Channel channel, long currentPositionMs) {
            mProgramLoadQueue.clear();
            if (mProgramLoadTask != null) {
                mProgramLoadTask.cancel(true);
            }
            mHandler.removeMessages(MSG_PREFETCH_PROGRAM);
            mPrograms.clear();
            mChannel = channel;
            if (channel == null || channel.isPassthrough()) {
                return;
            }
            if (available) {
                Program program = mProgramDataManager.getCurrentProgram(channel.getId());
                long prefetchStartTimeMs;
                if (program != null) {
                    mPrograms.add(program);
                    prefetchStartTimeMs = program.getEndTimeUtcMillis();
                } else {
                    prefetchStartTimeMs = Utils.floorTime(currentPositionMs,
                            MAX_DUMMY_PROGRAM_DURATION);
                }
                // Create dummy program
                mPrograms.addAll(createDummyPrograms(prefetchStartTimeMs,
                        currentPositionMs + PREFETCH_DURATION_FOR_NEXT));
                schedulePrefetchPrograms();
                TimeShiftManager.this.onProgramInfoChanged();
            }
        }

        void onRecordStartTimeChanged(long startTimeMs) {
            if (mChannel == null || mChannel.isPassthrough()) {
                return;
            }
            long currentMs = System.currentTimeMillis();

            long fetchStartTimeMs = Utils.floorTime(startTimeMs, MAX_DUMMY_PROGRAM_DURATION);
            boolean needToLoad = addDummyPrograms(fetchStartTimeMs,
                    currentMs + PREFETCH_DURATION_FOR_NEXT);
            if (needToLoad) {
                Range<Long> period = Range.create(fetchStartTimeMs, currentMs);
                mProgramLoadQueue.add(period);
                startTaskIfNeeded();
            }
        }

        private void startTaskIfNeeded() {
            if (mProgramLoadQueue.isEmpty()) {
                return;
            }
            if (mProgramLoadTask == null || mProgramLoadTask.isCancelled()) {
                startNext();
            } else {
                switch (mProgramLoadTask.getStatus()) {
                    case PENDING:
                        if (mProgramLoadTask.overlaps(mProgramLoadQueue)) {
                            if (mProgramLoadTask.cancel(true)) {
                                mProgramLoadQueue.add(mProgramLoadTask.getPeriod());
                                mProgramLoadTask = null;
                                startNext();
                            }
                        }
                        break;
                    case RUNNING:
                        // Remove pending task fully satisfied by the current
                        Range<Long> current = mProgramLoadTask.getPeriod();
                        Iterator<Range<Long>> i = mProgramLoadQueue.iterator();
                        while (i.hasNext()) {
                            Range<Long> r = i.next();
                            if (current.contains(r)) {
                                i.remove();
                            }
                        }
                        break;
                    case FINISHED:
                        // The task should have already cleared it self, clear and restart anyways.
                        Log.w(TAG, mProgramLoadTask + " is finished, but was not cleared");
                        startNext();
                        break;
                }
            }
        }

        private void startNext() {
            mProgramLoadTask = null;
            if (mProgramLoadQueue.isEmpty()) {
                return;
            }

            Range<Long> next = mProgramLoadQueue.poll();
            // Extend next to include any overlapping Ranges.
            Iterator<Range<Long>> i = mProgramLoadQueue.iterator();
            while(i.hasNext()) {
                Range<Long> r = i.next();
                if(next.contains(r.getLower()) || next.contains(r.getUpper())){
                    i.remove();
                    next = next.extend(r);
                }
            }
            if (mChannel != null) {
                mProgramLoadTask = new LoadProgramsForCurrentChannelTask(
                        mContext.getContentResolver(), next);
                mProgramLoadTask.executeOnDbThread();
            }
        }

        void addDummyProgramsAt(long timeMs) {
            addDummyPrograms(timeMs, timeMs + PREFETCH_DURATION_FOR_NEXT);
        }

        private boolean addDummyPrograms(Range<Long> period) {
            return addDummyPrograms(period.getLower(), period.getUpper());
        }

        private boolean addDummyPrograms(long startTimeMs, long endTimeMs) {
            boolean added = false;
            if (mPrograms.isEmpty()) {
                // Insert dummy program.
                mPrograms.addAll(createDummyPrograms(startTimeMs, endTimeMs));
                return true;
            }
            // Insert dummy program to the head of the list if needed.
            Program firstProgram = mPrograms.get(0);
            if (startTimeMs < firstProgram.getStartTimeUtcMillis()) {
                if (!firstProgram.isValid()) {
                    // Already the firstProgram is dummy.
                    mPrograms.remove(0);
                    mPrograms.addAll(0,
                            createDummyPrograms(startTimeMs, firstProgram.getEndTimeUtcMillis()));
                } else {
                    mPrograms.addAll(0,
                            createDummyPrograms(startTimeMs, firstProgram.getStartTimeUtcMillis()));
                }
                added = true;
            }
            // Insert dummy program to the tail of the list if needed.
            Program lastProgram = mPrograms.get(mPrograms.size() - 1);
            if (endTimeMs > lastProgram.getEndTimeUtcMillis()) {
                if (!lastProgram.isValid()) {
                    // Already the lastProgram is dummy.
                    mPrograms.remove(mPrograms.size() - 1);
                    mPrograms.addAll(
                            createDummyPrograms(lastProgram.getStartTimeUtcMillis(), endTimeMs));
                } else {
                    mPrograms.addAll(
                            createDummyPrograms(lastProgram.getEndTimeUtcMillis(), endTimeMs));
                }
                added = true;
            }
            // Insert dummy programs if the holes exist in the list.
            for (int i = 1; i < mPrograms.size(); ++i) {
                long endOfPrevious = mPrograms.get(i - 1).getEndTimeUtcMillis();
                long startOfCurrent = mPrograms.get(i).getStartTimeUtcMillis();
                if (startOfCurrent > endOfPrevious) {
                    List<Program> dummyPrograms =
                            createDummyPrograms(endOfPrevious, startOfCurrent);
                    mPrograms.addAll(i, dummyPrograms);
                    i += dummyPrograms.size();
                    added = true;
                }
            }
            return added;
        }

        private void removeDummyPrograms() {
            for (int i = 0; i < mPrograms.size(); ++i) {
                Program program = mPrograms.get(i);
                if (!program.isValid()) {
                    mPrograms.remove(i--);
                }
            }
        }

        private void removeOverlappedPrograms(List<Program> loadedPrograms) {
            if (mPrograms.size() == 0) {
                return;
            }
            Program program = mPrograms.get(0);
            for (int i = 0, j = 0; i < mPrograms.size() && j < loadedPrograms.size(); ++j) {
                Program loadedProgram = loadedPrograms.get(j);
                // Skip previous programs.
                while (program.getEndTimeUtcMillis() < loadedProgram.getStartTimeUtcMillis()) {
                    // Reached end of mPrograms.
                    if (++i == mPrograms.size()) {
                        return;
                    }
                    program = mPrograms.get(i);
                }
                // Remove overlapped programs.
                while (program.getStartTimeUtcMillis() < loadedProgram.getEndTimeUtcMillis()
                        && program.getEndTimeUtcMillis() > loadedProgram.getStartTimeUtcMillis()) {
                    mPrograms.remove(i);
                    if (i >= mPrograms.size()) {
                        break;
                    }
                    program = mPrograms.get(i);
                }
            }
        }

        // Returns a list of dummy programs.
        // The maximum duration of a dummy program is {@link MAX_DUMMY_PROGRAM_DURATION}.
        // So if the duration ({@code endTimeMs}-{@code startTimeMs}) is greater than the duration,
        // we need to create multiple dummy programs.
        // The reason of the limitation of the duration is because we want the trick play viewer
        // to show the time-line duration of {@link MAX_DUMMY_PROGRAM_DURATION} at most
        // for a dummy program.
        private List<Program> createDummyPrograms(long startTimeMs, long endTimeMs) {
            if (startTimeMs >= endTimeMs) {
                return Collections.emptyList();
            }
            List<Program> programs = new ArrayList<>();
            long start = startTimeMs;
            long end = Utils.ceilTime(startTimeMs, MAX_DUMMY_PROGRAM_DURATION);
            while (end < endTimeMs) {
                programs.add(new Program.Builder()
                        .setStartTimeUtcMillis(start)
                        .setEndTimeUtcMillis(end)
                        .build());
                start = end;
                end += MAX_DUMMY_PROGRAM_DURATION;
            }
            programs.add(new Program.Builder()
                    .setStartTimeUtcMillis(start)
                    .setEndTimeUtcMillis(endTimeMs)
                    .build());
            return programs;
        }

        Program getProgramAt(long timeMs) {
            return getProgramAt(timeMs, 0, mPrograms.size() - 1);
        }

        private Program getProgramAt(long timeMs, int start, int end) {
            if (start > end) {
                return null;
            }
            int mid = (start + end) / 2;
            Program program = mPrograms.get(mid);
            if (program.getStartTimeUtcMillis() > timeMs) {
                return getProgramAt(timeMs, start, mid - 1);
            } else if (program.getEndTimeUtcMillis() <= timeMs) {
                return getProgramAt(timeMs, mid+1, end);
            } else {
                return program;
            }
        }

        private long getOldestProgramStartTime() {
            if (mPrograms.isEmpty()) {
                return INVALID_TIME;
            }
            return mPrograms.get(0).getStartTimeUtcMillis();
        }

        private Program getLastValidProgram() {
            for (int i = mPrograms.size() - 1; i >= 0; --i) {
                Program program = mPrograms.get(i);
                if (program.isValid()) {
                    return program;
                }
            }
            return null;
        }

        private void schedulePrefetchPrograms() {
            if (DEBUG) Log.d(TAG, "Scheduling prefetching programs.");
            if (mHandler.hasMessages(MSG_PREFETCH_PROGRAM)) {
                return;
            }
            Program lastValidProgram = getLastValidProgram();
            if (DEBUG) Log.d(TAG, "Last valid program = " + lastValidProgram);
            if (lastValidProgram != null) {
                long delay = lastValidProgram.getEndTimeUtcMillis()
                        - PREFETCH_TIME_OFFSET_FROM_PROGRAM_END - System.currentTimeMillis();
                mHandler.sendEmptyMessageDelayed(MSG_PREFETCH_PROGRAM, delay);
                if (DEBUG) Log.d(TAG, "Scheduling with " + delay + "(ms) delays.");
            } else {
                mHandler.sendEmptyMessage(MSG_PREFETCH_PROGRAM);
                if (DEBUG) Log.d(TAG, "Scheduling promptly.");
            }
        }

        private void prefetchPrograms() {
            long startTimeMs;
            Program lastValidProgram = getLastValidProgram();
            if (lastValidProgram == null) {
                startTimeMs = System.currentTimeMillis();
            } else {
                startTimeMs = lastValidProgram.getEndTimeUtcMillis();
            }
            long endTimeMs = System.currentTimeMillis() + PREFETCH_DURATION_FOR_NEXT;
            if (DEBUG) {
                Log.d(TAG, "Prefetch task starts: {startTime=" + Utils.toTimeString(startTimeMs)
                        + ", endTime=" + Utils.toTimeString(endTimeMs) + "}");
            }
            mProgramLoadQueue.add(Range.create(startTimeMs, endTimeMs));
            startTaskIfNeeded();
        }

        private class LoadProgramsForCurrentChannelTask
                extends AsyncDbTask.LoadProgramsForChannelTask {

            public LoadProgramsForCurrentChannelTask(ContentResolver contentResolver,
                    Range<Long> period) {
                super(contentResolver, mChannel.getId(), period);
            }

            @Override
            protected void onPostExecute(List<Program> programs) {
                if (DEBUG) {
                    Log.d(TAG, "Programs are loaded {channelId=" + mChannelId +
                            ", from=" + Utils.toTimeString(mPeriod.getLower()) +
                            ", to=" + Utils.toTimeString(mPeriod.getUpper()) +
                            "}");
                }
                //remove pending tasks that are fully satisfied by this query.
                Iterator<Range<Long>> it = mProgramLoadQueue.iterator();
                while (it.hasNext()) {
                    Range<Long> r = it.next();
                    if (mPeriod.contains(r)) {
                        it.remove();
                    }
                }
                if (programs == null || programs.isEmpty() || mPrograms.isEmpty()) {
                    mPrograms.addAll(programs);
                    if (addDummyPrograms(mPeriod)) {
                        TimeShiftManager.this.onProgramInfoChanged();
                    }
                    schedulePrefetchPrograms();
                    startNextLoadingIfNeeded();
                    return;
                }
                removeDummyPrograms();
                removeOverlappedPrograms(programs);
                Program loadedProgram = programs.get(0);
                for (int i = 0; i < mPrograms.size() && !programs.isEmpty(); ++i) {
                    Program program = mPrograms.get(i);
                    while (program.getStartTimeUtcMillis() > loadedProgram
                            .getStartTimeUtcMillis()) {
                        mPrograms.add(i++, loadedProgram);
                        programs.remove(0);
                        if (programs.isEmpty()) {
                            break;
                        }
                        loadedProgram = programs.get(0);
                    }
                }
                mPrograms.addAll(programs);
                addDummyPrograms(mPeriod);
                TimeShiftManager.this.onProgramInfoChanged();
                schedulePrefetchPrograms();
                startNextLoadingIfNeeded();
            }

            @Override
            protected void onCancelled(List<Program> programs) {
                if (DEBUG) {
                    Log.d(TAG, "Program loading has been canceled {channelId=" + (mChannel == null
                            ? "null" : mChannelId) + ", from=" + Utils
                            .toTimeString(mPeriod.getLower()) + ", to=" + Utils
                            .toTimeString(mPeriod.getUpper()) + "}");
                }
                startNextLoadingIfNeeded();
            }

            private void startNextLoadingIfNeeded() {
                mProgramLoadTask = null;
                // Need to post to handler, because the task is still running.
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startTaskIfNeeded();
                    }
                });
            }

            public boolean overlaps(Queue<Range<Long>> programLoadQueue) {
                for (Range<Long> r : programLoadQueue) {
                    if (mPeriod.contains(r.getLower()) || mPeriod.contains(r.getUpper())) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    @VisibleForTesting
    final class CurrentPositionMediator {
        long mCurrentPositionMs;
        long mSeekRequestTimeMs;

        void initialize(long timeMs) {
            mSeekRequestTimeMs = INVALID_TIME;
            mCurrentPositionMs = timeMs;
            TimeShiftManager.this.onCurrentPositionChanged();
        }

        void onSeekRequested(long seekTimeMs) {
            mSeekRequestTimeMs = System.currentTimeMillis();
            mCurrentPositionMs = seekTimeMs;
            TimeShiftManager.this.onCurrentPositionChanged();
        }

        void onCurrentPositionChanged(long currentPositionMs) {
            if (mSeekRequestTimeMs == INVALID_TIME) {
                mCurrentPositionMs = currentPositionMs;
                TimeShiftManager.this.onCurrentPositionChanged();
                return;
            }
            long currentTimeMs = System.currentTimeMillis();
            boolean isValid = Math.abs(currentPositionMs - mCurrentPositionMs) < REQUEST_TIMEOUT_MS;
            boolean isTimeout = currentTimeMs > mSeekRequestTimeMs + REQUEST_TIMEOUT_MS;
            if (isValid || isTimeout) {
                initialize(currentPositionMs);
            } else {
                if (getPlayStatus() == PLAY_STATUS_PLAYING) {
                    if (getPlayDirection() == PLAY_DIRECTION_FORWARD) {
                        mCurrentPositionMs += (currentTimeMs - mSeekRequestTimeMs)
                                * getPlaybackSpeed();
                    } else {
                        mCurrentPositionMs -= (currentTimeMs - mSeekRequestTimeMs)
                                * getPlaybackSpeed();
                    }
                }
                TimeShiftManager.this.onCurrentPositionChanged();
            }
        }
    }

    /**
     * The listener used to receive the events by the time-shift manager
     */
    public interface Listener {
        /**
         * Called when the availability of the time-shift for the current channel has been changed.
         * If the time shift is available, {@link TimeShiftManager#getRecordStartTimeMs} should
         * return the valid time.
         */
        void onAvailabilityChanged();

        /**
         * Called when the play status is changed between {@link #PLAY_STATUS_PLAYING} and
         * {@link #PLAY_STATUS_PAUSED}
         *
         * @param status The new play state.
         */
        void onPlayStatusChanged(int status);

        /**
         * Called when the recordStartTime has been changed.
         */
        void onRecordStartTimeChanged();

        /**
         * Called when the current position is changed.
         */
        void onCurrentPositionChanged();

        /**
         * Called when the program information is updated.
         */
        void onProgramInfoChanged();

        /**
         * Called when an action becomes enabled or disabled.
         */
        void onActionEnabledChanged(@TimeShiftActionId int actionId, boolean enabled);
    }
}

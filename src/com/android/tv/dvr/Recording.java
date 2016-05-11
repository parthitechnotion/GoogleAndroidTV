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

package com.android.tv.dvr;

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Range;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dvr.provider.DvrContract;
import com.android.tv.util.SoftPreconditions;
import com.android.tv.util.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A data class for one recording contents.
 */
@VisibleForTesting
public final class Recording {
    private static final String TAG = "Recording";

    public static final String RECORDING_ID_EXTRA = "extra.dvr.recording.id";
    public static final String PARAM_INPUT_ID = "input_id";

    public static final long ID_NOT_SET = -1;

    public static final Comparator<Recording> START_TIME_COMPARATOR = new Comparator<Recording>() {
        @Override
        public int compare(Recording lhs, Recording rhs) {
            return Long.compare(lhs.mStartTimeMs, rhs.mStartTimeMs);
        }
    };

    public static final Comparator<Recording> PRIORITY_COMPARATOR = new Comparator<Recording>() {
        @Override
        public int compare(Recording lhs, Recording rhs) {
            int value = Long.compare(lhs.mPriority, rhs.mPriority);
            if (value == 0) {
                value = Long.compare(lhs.mId, rhs.mId);
            }
            return value;
        }
    };

    public static final Comparator<Recording> START_TIME_THEN_PRIORITY_COMPARATOR
            = new Comparator<Recording>() {
        @Override
        public int compare(Recording lhs, Recording rhs) {
            int value = START_TIME_COMPARATOR.compare(lhs, rhs);
            if (value == 0) {
                value = PRIORITY_COMPARATOR.compare(lhs, rhs);
            }
            return value;
        }
    };

    public static Builder builder(Channel c, Program p) {
        return new Builder()
                .setChannel(c)
                .setStartTime(p.getStartTimeUtcMillis())
                .setEndTime(p.getEndTimeUtcMillis())
                .setPrograms(Collections.singletonList(p))
                .setType(TYPE_PROGRAM);
    }

    public static Builder builder(Channel c, long startTime, long endTime) {
        return new Builder()
                .setChannel(c)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setType(TYPE_TIMED);
    }

    public static final class Builder {
        private long mId = ID_NOT_SET;
        private long mPriority = Long.MAX_VALUE;
        private Uri mUri;
        private Channel mChannel;
        private List<Program> mPrograms;
        private @RecordingType int mType;
        private long mStartTime;
        private long mEndTime;
        private long mSize;
        private @RecordingState int mState;
        private SeasonRecording mParentSeasonRecording;

        private Builder() { }

        public Builder setId(long id) {
            mId = id;
            return this;
        }

        public Builder setPriority(long priority) {
            mPriority = priority;
            return this;
        }

        private Builder setUri(Uri uri) {
            mUri = uri;
            return this;
        }

        private Builder setChannel(Channel channel) {
            mChannel = channel;
            return this;
        }

        public Builder setPrograms(List<Program> programs) {
            mPrograms = programs;
            return this;
        }

        private Builder setType(@RecordingType int type) {
            mType = type;
            return this;
        }

        public Builder setStartTime(long startTime) {
            mStartTime = startTime;
            return this;
        }

        public Builder setEndTime(long endTime) {
            mEndTime = endTime;
            return this;
        }

        public Builder setSize(long size) {
            mSize = size;
            return this;
        }

        public Builder setState(@RecordingState int state) {
            mState = state;
            return this;
        }

        public Builder setParentSeasonRecording(SeasonRecording parentSeasonRecording) {
            mParentSeasonRecording = parentSeasonRecording;
            return this;
        }

        public Recording build() {
            return new Recording(mId, mPriority, mUri, mChannel, mPrograms, mType, mStartTime,
                    mEndTime, mSize,
                    mState, mParentSeasonRecording);
        }
    }

    /**
     * Creates {@link Builder} object from the given original {@code Recording}.
     */
    public static Builder buildFrom(Recording orig) {
        return new Builder()
                .setId(orig.mId)
                .setChannel(orig.mChannel)
                .setEndTime(orig.mEndTimeMs)
                .setParentSeasonRecording(orig.mParentSeasonRecording)
                .setPrograms(orig.mPrograms)
                .setSize(orig.mMediaSize)
                .setStartTime(orig.mStartTimeMs)
                .setState(orig.mState)
                .setType(orig.mType)
                .setUri(orig.mUri);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_RECORDING_NOT_STARTED, STATE_RECORDING_IN_PROGRESS,
        STATE_RECORDING_UNEXPECTEDLY_STOPPED, STATE_RECORDING_FINISHED, STATE_RECORDING_FAILED})
    public @interface RecordingState {}
    public static final int STATE_RECORDING_NOT_STARTED = 0;
    public static final int STATE_RECORDING_IN_PROGRESS = 1;
    public static final int STATE_RECORDING_UNEXPECTEDLY_STOPPED = 2;
    public static final int STATE_RECORDING_FINISHED = 3;
    public static final int STATE_RECORDING_FAILED = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_TIMED, TYPE_PROGRAM})
    public @interface RecordingType {}
    /**
     * Record with given time range.
     */
    static final int TYPE_TIMED = 1;
    /**
     * Record with a given program.
     */
    static final int TYPE_PROGRAM = 2;

    @RecordingType private final int mType;

    /**
     * Use this projection if you want to create {@link Recording} object using {@link #fromCursor}.
     */
    public static final String[] PROJECTION = {
        // Columns must match what is read in Recording.fromCursor()
        DvrContract.Recordings._ID,
        DvrContract.Recordings.COLUMN_PRIORITY,
        DvrContract.Recordings.COLUMN_TYPE,
        DvrContract.Recordings.COLUMN_URI,
        DvrContract.Recordings.COLUMN_CHANNEL_ID,
        DvrContract.Recordings.COLUMN_START_TIME_UTC_MILLIS,
        DvrContract.Recordings.COLUMN_END_TIME_UTC_MILLIS,
        DvrContract.Recordings.COLUMN_MEDIA_SIZE,
        DvrContract.Recordings.COLUMN_STATE
    };

    /**
     * The ID internal to Live TV
     */
    private final long mId;

    /**
     * The priority of this recording.
     *
     * <p> The lowest number is recorded first. If there is a tie in priority then the lower id
     * wins.
     */
    private final long mPriority;

    /**
     * The {@link Uri} is used as its identifier with the TIS.
     * Note: If the state is STATE_RECORDING_NOT_STARTED, this might be {@code null}.
     */
    @Nullable
    private final Uri mUri;

    /**
     * Note: mChannel and mPrograms should be loaded from a separate storage not
     * from TvProvider, because info from TvProvider can be removed or edited later.
     */
    @NonNull
    private final Channel mChannel;
    /**
     * Recorded program info. Its size is usually 1. But, when a channel is recorded by given time
     * range, multiple programs can be recorded in one recording.
     */
    @NonNull
    private final List<Program> mPrograms;

    private final long mStartTimeMs;
    private final long mEndTimeMs;
    private final long mMediaSize;
    @RecordingState private final int mState;

    private final SeasonRecording mParentSeasonRecording;

    private Recording(long id, long priority, Uri uri, Channel channel, List<Program> programs,
            @RecordingType int type, long startTime, long endTime, long size,
            @RecordingState int state, SeasonRecording parentSeasonRecording) {
        mId = id;
        mPriority = priority;
        if (uri == null && id >= 0 && channel != null) {
            uri = new Uri.Builder()
                    .scheme("record")
                    .authority("com.android.tv")
                    .appendPath(Long.toString(mId))
                    .appendQueryParameter(PARAM_INPUT_ID, channel.getInputId())
                    .build();
        }
        mUri = uri;
        mChannel = channel;
        mPrograms = programs == null ? Collections.EMPTY_LIST : new ArrayList<>(programs);
        mType = type;
        mStartTimeMs = startTime;
        mEndTimeMs = endTime;
        mMediaSize = size;
        mState = state;
        mParentSeasonRecording = parentSeasonRecording;
    }

    /**
     * Returns recording schedule type. The possible types are {@link #TYPE_PROGRAM} and
     * {@link #TYPE_TIMED}.
     */
    @RecordingType
    public int getType() {
        return mType;
    }

    /**
     * Returns {@link android.net.Uri} representing the recording.
     */
    public Uri getUri() {
        return mUri;
    }

    /**
     * Returns recorded {@link Channel}.
     */
    public Channel getChannel() {
        return mChannel;
    }

    /**
     * Returns a list of recorded {@link Program}.
     */
    public List<Program> getPrograms() {
        return mPrograms;
    }

    /**
     * Returns started time.
     */
    public long getStartTimeMs() {
        return mStartTimeMs;
    }

    /**
     * Returns ended time.
     */
    public long getEndTimeMs() {
        return mEndTimeMs;
    }

    /**
     * Returns duration.
     */
    public long getDuration() {
        return mEndTimeMs - mStartTimeMs;
    }

    /**
     * Returns file size which this record consumes.
     */
    public long getSize() {
        return mMediaSize;
    }

    /**
     * Returns the state. The possible states are {@link #STATE_RECORDING_FINISHED},
     * {@link #STATE_RECORDING_IN_PROGRESS} and {@link #STATE_RECORDING_UNEXPECTEDLY_STOPPED}.
     */
    @RecordingState public int getState() {
        return mState;
    }

    /**
     * Returns {@link SeasonRecording} including this schedule.
     */
    public SeasonRecording getParentSeasonRecording() {
        return mParentSeasonRecording;
    }

    public long getId() {
        return mId;
    }

    public long getPriority() {
        return mPriority;
    }

    /**
     * Creates {@link Recording} object from the given {@link Cursor}.
     */
    public static Recording fromCursor(Cursor c, Channel channel, List<Program> programs) {
        Builder builder = new Builder();
        int index = -1;
        builder.setId(c.getLong(++index));
        builder.setPriority(c.getLong(++index));
        builder.setType(recordingType(c.getString(++index)));
        String uri = c.getString(++index);
        if (uri != null) {
            builder.setUri(Uri.parse(uri));
        }
        // Skip channel.
        ++index;
        builder.setStartTime(c.getLong(++index));
        builder.setEndTime(c.getLong(++index));
        builder.setSize(c.getLong(++index));
        builder.setState(recordingState(c.getString(++index)));
        builder.setChannel(channel);
        builder.setPrograms(programs);
        return builder.build();
    }

    /**
     * Converts a string to a @RecordingType int, defaulting to {@link #TYPE_TIMED}.
     */
    private static @RecordingType int recordingType(String type) {
        int t;
        try {
            t = Integer.valueOf(type);
        } catch (NullPointerException | NumberFormatException e) {
            SoftPreconditions.checkArgument(false, TAG, "Unknown recording type " + type);
            return TYPE_TIMED;
        }
        switch (t) {
            case TYPE_TIMED:
                return TYPE_TIMED;
            case TYPE_PROGRAM:
                return TYPE_PROGRAM;
            default:
                SoftPreconditions.checkArgument(false, TAG, "Unknown recording type " + type);
                return TYPE_TIMED;
        }
    }

    /**
     * Converts a string to a @RecordingState int, defaulting to
     * {@link #STATE_RECORDING_NOT_STARTED}.
     */
    private static @RecordingState int recordingState(String state) {
        int s;
        try {
            s = Integer.valueOf(state);
        } catch (NullPointerException | NumberFormatException e) {
            SoftPreconditions.checkArgument(false, TAG, "Unknown recording state" + state);
            return STATE_RECORDING_NOT_STARTED;
        }
        switch (s) {
            case STATE_RECORDING_NOT_STARTED:
                return STATE_RECORDING_NOT_STARTED;
            case STATE_RECORDING_IN_PROGRESS:
                return STATE_RECORDING_IN_PROGRESS;
            case STATE_RECORDING_FINISHED:
                return STATE_RECORDING_FINISHED;
            case STATE_RECORDING_UNEXPECTEDLY_STOPPED:
                return STATE_RECORDING_UNEXPECTEDLY_STOPPED;
            case STATE_RECORDING_FAILED:
                return STATE_RECORDING_FAILED;
            default:
                SoftPreconditions.checkArgument(false, TAG, "Unknown recording state" + state);
                return STATE_RECORDING_NOT_STARTED;
        }
    }

    /**
     * Checks if the {@code period} overlaps with the recording time.
     */
    public boolean isOverLapping(Range<Long> period) {
        return mStartTimeMs <= period.getUpper() && mEndTimeMs >= period.getLower();
    }

    @Override
    public String toString() {
        return "Recording[" + mId
                + "]"
                + "(startTime=" + Utils.toIsoDateTimeString(mStartTimeMs)
                + ",endTime=" + Utils.toIsoDateTimeString(mEndTimeMs)
                + ",state=" + mState
                + ",priority=" + mPriority
                + ")";
    }
}

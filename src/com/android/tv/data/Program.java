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

package com.android.tv.data;

import android.content.ContentValues;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;

/**
 * A convenience class to create and insert program information entries into the database.
 */
public final class Program {
    public static final long INVALID_ID = -1;

    private long mChannelId;
    private String mTitle;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mDescription;
    private String mLongDescription;
    private String mVideoDefinitionLevel;
    private String mPosterArtUri;
    private String mThumbnailUri;
    private TvContentRating[] mContentRatings;

    private Program() {
        // Do nothing.
    }

    public long getChannelId() {
        return mChannelId;
    }

    public void setChannelId(long channelId) {
        mChannelId = channelId;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    public void setStartTimeUtcMillis(long startTimeUtcMillis) {
        mStartTimeUtcMillis = startTimeUtcMillis;
    }

    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    public void setEndTimeUtcMillis(long endTimeUtcMillis) {
        mEndTimeUtcMillis = endTimeUtcMillis;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public String getLongDescription() {
        return mLongDescription;
    }

    public void setLongDescription(String longDescription) {
        mLongDescription = longDescription;
    }

    public String getVideoDefinitionLevel() {
        return mVideoDefinitionLevel;
    }

    public void setVideoDefinitionLevel(String videoDefinitionLevel) {
        mVideoDefinitionLevel = videoDefinitionLevel;
    }

    public TvContentRating[] getContentRatings() {
        return mContentRatings;
    }

    public void setContentRatings(TvContentRating[] contentRatings) {
        mContentRatings = contentRatings;
    }

    public String getPosterArtUri() {
        return mPosterArtUri;
    }

    public void setPosterArtUri(String posterArtUri) {
        mPosterArtUri = posterArtUri;
    }

    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    public void setThumbnailUri(String thumbnailUri) {
        mThumbnailUri = thumbnailUri;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(TvContract.Programs.COLUMN_CHANNEL_ID, mChannelId);
        values.put(TvContract.Programs.COLUMN_TITLE, mTitle);
        values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, mStartTimeUtcMillis);
        values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, mEndTimeUtcMillis);
        values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, mDescription);
        values.put(TvContract.Programs.COLUMN_LONG_DESCRIPTION, mLongDescription);
        values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, mPosterArtUri);
        values.put(TvContract.Programs.COLUMN_THUMBNAIL_URI, mThumbnailUri);
        return values;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Program{")
                .append(", channelId=").append(mChannelId)
                .append(", title=").append(mTitle)
                .append(", startTimeUtcSec=").append(mStartTimeUtcMillis)
                .append(", endTimeUtcSec=").append(mEndTimeUtcMillis)
                .append(", description=").append(mDescription)
                .append(", longDescription=").append(mLongDescription)
                .append(", videoDefinitionLevel=").append(mVideoDefinitionLevel)
                .append(", posterArtUri=").append(mPosterArtUri)
                .append(", thumbnailUri=").append(mThumbnailUri)
                .append("}")
                .toString();
    }

    public void copyFrom(Program other) {
        if (this == other) {
            return;
        }

        mChannelId = other.mChannelId;
        mTitle = other.mTitle;
        mStartTimeUtcMillis = other.mStartTimeUtcMillis;
        mEndTimeUtcMillis = other.mEndTimeUtcMillis;
        mDescription = other.mDescription;
        mLongDescription = other.mLongDescription;
        mVideoDefinitionLevel = other.mVideoDefinitionLevel;
        mPosterArtUri = other.mPosterArtUri;
        mThumbnailUri = other.mThumbnailUri;
    }

    public static final class Builder {
        private final Program mProgram;

        public Builder() {
            mProgram = new Program();
            // Fill initial data.
            mProgram.mChannelId = Channel.INVALID_ID;
            mProgram.mTitle = "title";
            mProgram.mStartTimeUtcMillis = -1;
            mProgram.mEndTimeUtcMillis = -1;
            mProgram.mDescription = "description";
            mProgram.mLongDescription = "long_description";
        }

        public Builder(Program other) {
            mProgram = new Program();
            mProgram.copyFrom(other);
        }

        public Builder setChannelId(long channelId) {
            mProgram.mChannelId = channelId;
            return this;
        }

        public Builder setTitle(String title) {
            mProgram.mTitle = title;
            return this;
        }

        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mProgram.mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mProgram.mEndTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        public Builder setDescription(String description) {
            mProgram.mDescription = description;
            return this;
        }

        public Builder setLongDescription(String longDescription) {
            mProgram.mLongDescription = longDescription;
            return this;
        }

        public Builder setVideoDefinitionLevel(String videoDefinitionLevel) {
            mProgram.mVideoDefinitionLevel = videoDefinitionLevel;
            return this;
        }

        public Builder setContentRatings(TvContentRating[] contentRatings) {
            mProgram.mContentRatings = contentRatings;
            return this;
        }

        public Builder setPosterArtUri(String posterArtUri) {
            mProgram.mPosterArtUri = posterArtUri;
            return this;
        }

        public Builder setThumbnailUri(String thumbnailUri) {
            mProgram.mThumbnailUri = thumbnailUri;
            return this;
        }

        public Program build() {
            return mProgram;
        }
    }
}

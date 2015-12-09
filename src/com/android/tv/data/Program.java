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

package com.android.tv.data;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.dvr.provider.DvrContract;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.Utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * A convenience class to create and insert program information entries into the database.
 */
public final class Program implements Comparable<Program> {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DUMP_DESCRIPTION = false;
    private static final String TAG = "Program";

    public static final String[] PROJECTION = {
        // Columns must match what is read in Program.fromCursor()
        TvContract.Programs.COLUMN_CHANNEL_ID,
        TvContract.Programs.COLUMN_TITLE,
        TvContract.Programs.COLUMN_EPISODE_TITLE,
        TvContract.Programs.COLUMN_SEASON_NUMBER,
        TvContract.Programs.COLUMN_EPISODE_NUMBER,
        TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
        TvContract.Programs.COLUMN_POSTER_ART_URI,
        TvContract.Programs.COLUMN_THUMBNAIL_URI,
        TvContract.Programs.COLUMN_CANONICAL_GENRE,
        TvContract.Programs.COLUMN_CONTENT_RATING,
        TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
        TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
        TvContract.Programs.COLUMN_VIDEO_WIDTH,
        TvContract.Programs.COLUMN_VIDEO_HEIGHT
    };

    /**
     * Use this projection if you want to create {@link Program} object using
     * {@link #fromDvrCursor}.
     */
    public static final String[] PROJECTION_DVR = {
        // Columns must match what is read in Channel.fromDvrCursor()
        DvrContract.DvrPrograms._ID
    };

    /**
     * Creates {@code Program} object from cursor.
     *
     * <p>The query that created the cursor MUST use {@link #PROJECTION}.
     */
    public static Program fromCursor(Cursor cursor) {
        // Columns read must match the order of match {@link #PROJECTION}
        Builder builder = new Builder();
        int index = 0;
        builder.setChannelId(cursor.getLong(index++));
        builder.setTitle(cursor.getString(index++));
        builder.setEpisodeTitle(cursor.getString(index++));
        builder.setSeasonNumber(cursor.getInt(index++));
        builder.setEpisodeNumber(cursor.getInt(index++));
        builder.setDescription(cursor.getString(index++));
        builder.setPosterArtUri(cursor.getString(index++));
        builder.setThumbnailUri(cursor.getString(index++));
        builder.setCanonicalGenres(cursor.getString(index++));
        builder.setContentRatings(Utils.stringToContentRatings(cursor.getString(index++)));
        builder.setStartTimeUtcMillis(cursor.getLong(index++));
        builder.setEndTimeUtcMillis(cursor.getLong(index++));
        builder.setVideoWidth((int) cursor.getLong(index++));
        builder.setVideoHeight((int) cursor.getLong(index++));
        return builder.build();
    }

    /**
     * Creates a {@link Program} object from the DVR database.
     */
    public static Program fromDvrCursor(Cursor c) {
        Program program = new Program();
        int index = -1;
        program.mDvrId = c.getLong(++index);
        return program;
    }

    private long mChannelId;
    private String mTitle;
    private String mEpisodeTitle;
    private int mSeasonNumber;
    private int mEpisodeNumber;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mDescription;
    private int mVideoWidth;
    private int mVideoHeight;
    private String mPosterArtUri;
    private String mThumbnailUri;
    private int[] mCanonicalGenreIds;
    private TvContentRating[] mContentRatings;

    private long mDvrId;

    /**
     * TODO(DVR): Need to fill the following data.
     */
    private boolean mRecordable;
    private boolean mRecordingScheduled;

    public interface LoadPosterArtCallback {
        void onLoadPosterArtFinished(Program program, Bitmap posterArt);
    }

    private Program() {
        // Do nothing.
    }

    public long getChannelId() {
        return mChannelId;
    }

    /**
     * Returns {@code true} if this program is valid or {@code false} otherwise.
     */
    public boolean isValid() {
        return mChannelId >= 0;
    }

    /**
     * Returns {@code true} if the program is valid and {@code false} otherwise.
     */
    public static boolean isValid(Program program) {
        return program != null && program.isValid();
    }

    public String getTitle() {
        return mTitle;
    }

    public String getEpisodeTitle() {
        return mEpisodeTitle;
    }

    public String getEpisodeDisplayTitle(Context context) {
        if (mSeasonNumber > 0 && mEpisodeNumber > 0 && !TextUtils.isEmpty(mEpisodeTitle)) {
            return String.format(context.getResources().getString(R.string.episode_format),
                    mSeasonNumber, mEpisodeNumber, mEpisodeTitle);
        }
        return mEpisodeTitle;
    }

    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    /**
     * Returns the program duration.
     */
    public long getDurationMillis() {
        return mEndTimeUtcMillis - mStartTimeUtcMillis;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public TvContentRating[] getContentRatings() {
        return mContentRatings;
    }

    public String getPosterArtUri() {
        return mPosterArtUri;
    }

    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    /**
     * Returns array of canonical genres for this program.
     * This is expected to be called rarely.
     */
    public String[] getCanonicalGenres() {
        if (mCanonicalGenreIds == null) {
            return null;
        }
        String[] genres = new String[mCanonicalGenreIds.length];
        for (int i = 0; i < mCanonicalGenreIds.length; i++) {
            genres[i] = GenreItems.getCanonicalGenre(mCanonicalGenreIds[i]);
        }
        return genres;
    }

    /**
     * Returns if this program has the genre.
     */
    public boolean hasGenre(int genreId) {
        if (genreId == GenreItems.ID_ALL_CHANNELS) {
            return true;
        }
        if (mCanonicalGenreIds != null) {
            for (int id : mCanonicalGenreIds) {
                if (id == genreId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns an ID in DVR database.
     */
    public long getDvrId() {
        return mDvrId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mChannelId, mStartTimeUtcMillis, mEndTimeUtcMillis,
                mTitle, mEpisodeTitle, mDescription, mVideoWidth, mVideoHeight,
                mPosterArtUri, mThumbnailUri, Arrays.hashCode(mContentRatings),
                Arrays.hashCode(mCanonicalGenreIds), mSeasonNumber, mEpisodeNumber);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Program)) {
            return false;
        }
        Program program = (Program) other;
        return mChannelId == program.mChannelId
                && mStartTimeUtcMillis == program.mStartTimeUtcMillis
                && mEndTimeUtcMillis == program.mEndTimeUtcMillis
                && Objects.equals(mTitle, program.mTitle)
                && Objects.equals(mEpisodeTitle, program.mEpisodeTitle)
                && Objects.equals(mDescription, program.mDescription)
                && mVideoWidth == program.mVideoWidth
                && mVideoHeight == program.mVideoHeight
                && Objects.equals(mPosterArtUri, program.mPosterArtUri)
                && Objects.equals(mThumbnailUri, program.mThumbnailUri)
                && Arrays.equals(mContentRatings, program.mContentRatings)
                && Arrays.equals(mCanonicalGenreIds, program.mCanonicalGenreIds)
                && mSeasonNumber == program.mSeasonNumber
                && mEpisodeNumber == program.mEpisodeNumber;
    }

    @Override
    public int compareTo(@NonNull Program other) {
        return Long.compare(mStartTimeUtcMillis, other.mStartTimeUtcMillis);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Program{")
                .append("channelId=").append(mChannelId)
                .append(", title=").append(mTitle)
                .append(", episodeTitle=").append(mEpisodeTitle)
                .append(", seasonNumber=").append(mSeasonNumber)
                .append(", episodeNumber=").append(mEpisodeNumber)
                .append(", startTimeUtcSec=").append(Utils.toTimeString(mStartTimeUtcMillis))
                .append(", endTimeUtcSec=").append(Utils.toTimeString(mEndTimeUtcMillis))
                .append(", videoWidth=").append(mVideoWidth)
                .append(", videoHeight=").append(mVideoHeight)
                .append(", contentRatings=").append(Utils.contentRatingsToString(mContentRatings))
                .append(", posterArtUri=").append(mPosterArtUri)
                .append(", thumbnailUri=").append(mThumbnailUri)
                .append(", canonicalGenres=").append(Arrays.toString(mCanonicalGenreIds));
        if (DEBUG_DUMP_DESCRIPTION) {
            builder.append(", description=").append(mDescription);
        }
        return builder.append("}").toString();
    }

    public void copyFrom(Program other) {
        if (this == other) {
            return;
        }

        mChannelId = other.mChannelId;
        mTitle = other.mTitle;
        mEpisodeTitle = other.mEpisodeTitle;
        mSeasonNumber = other.mSeasonNumber;
        mEpisodeNumber = other.mEpisodeNumber;
        mStartTimeUtcMillis = other.mStartTimeUtcMillis;
        mEndTimeUtcMillis = other.mEndTimeUtcMillis;
        mDescription = other.mDescription;
        mVideoWidth = other.mVideoWidth;
        mVideoHeight = other.mVideoHeight;
        mPosterArtUri = other.mPosterArtUri;
        mThumbnailUri = other.mThumbnailUri;
        mCanonicalGenreIds = other.mCanonicalGenreIds;
        mContentRatings = other.mContentRatings;
    }

    public static final class Builder {
        private final Program mProgram;

        public Builder() {
            mProgram = new Program();
            // Fill initial data.
            mProgram.mChannelId = Channel.INVALID_ID;
            mProgram.mTitle = "title";
            mProgram.mSeasonNumber = -1;
            mProgram.mEpisodeNumber = -1;
            mProgram.mStartTimeUtcMillis = -1;
            mProgram.mEndTimeUtcMillis = -1;
            mProgram.mDescription = "description";
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

        public Builder setEpisodeTitle(String episodeTitle) {
            mProgram.mEpisodeTitle = episodeTitle;
            return this;
        }

        public Builder setSeasonNumber(int seasonNumber) {
            mProgram.mSeasonNumber = seasonNumber;
            return this;
        }

        public Builder setEpisodeNumber(int episodeNumber) {
            mProgram.mEpisodeNumber = episodeNumber;
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

        public Builder setVideoWidth(int width) {
            mProgram.mVideoWidth = width;
            return this;
        }

        public Builder setVideoHeight(int height) {
            mProgram.mVideoHeight = height;
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

        public Builder setCanonicalGenres(String genres) {
            if (TextUtils.isEmpty(genres)) {
                return this;
            }
            String[] canonicalGenres = TvContract.Programs.Genres.decode(genres);
            if (canonicalGenres.length > 0) {
                int[] temp = new int[canonicalGenres.length];
                int i = 0;
                for (String canonicalGenre : canonicalGenres) {
                    int genreId = GenreItems.getId(canonicalGenre);
                    if (genreId == GenreItems.ID_ALL_CHANNELS) {
                        // Skip if the genre is unknown.
                        continue;
                    }
                    temp[i++] = genreId;
                }
                if (i < canonicalGenres.length) {
                    temp = Arrays.copyOf(temp, i);
                }
                mProgram.mCanonicalGenreIds=temp;
            }
            return this;
        }

        public Program build() {
            Program program = new Program();
            program.copyFrom(mProgram);
            return program;
        }
    }

    /**
     * Prefetches the program poster art.<p>
     */
    @UiThread
    public void prefetchPosterArt(Context context, int posterArtWidth, int posterArtHeight) {
        if (mPosterArtUri == null) {
            return;
        }
        ImageLoader.prefetchBitmap(context, mPosterArtUri, posterArtWidth, posterArtHeight);
    }

    /**
     * Loads the program poster art and returns it via {@code callback}.<p>
     * <p>
     * Note that it may directly call {@code callback} if the program poster art already is loaded.
     */
    @UiThread
    public void loadPosterArt(Context context, int posterArtWidth, int posterArtHeight,
            final LoadPosterArtCallback callback) {
        if (mPosterArtUri == null) {
            return;
        }
        ImageLoader.loadBitmap(context, mPosterArtUri, posterArtWidth, posterArtHeight,
                new ImageLoader.ImageLoaderCallback() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap) {
                        if (DEBUG) {
                            Log.i(TAG, "Loaded poster art for " + Program.this + ": " + bitmap);
                        }
                        if (callback != null) {
                            callback.onLoadPosterArtFinished(Program.this, bitmap);
                        }
                    }
                });
    }
}

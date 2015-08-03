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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.common.TvCommonConstants;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A convenience class to create and insert channel entries into the database.
 */
public final class Channel {
    private static final String TAG = "Channel";

    public static final long INVALID_ID = -1;
    public static final int LOAD_IMAGE_TYPE_CHANNEL_LOGO = 1;
    public static final int LOAD_IMAGE_TYPE_APP_LINK_ICON = 2;
    public static final int LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART = 3;

    /**
     * When a TIS doesn't provide any information about app link, and it doesn't have a leanback
     * launch intent, there will be no app link card for the TIS.
     */
    public static final int APP_LINK_TYPE_NONE = -1;
    /**
     * When a TIS provide a specific app link information, the app link card will be
     * {@code APP_LINK_TYPE_CHANNEL} which contains all the provided information.
     */
    public static final int APP_LINK_TYPE_CHANNEL = 1;
    /**
     * When a TIS doesn't provide a specific app link information, but the app has a leanback launch
     * intent, the app link card will be {@code APP_LINK_TYPE_APP} which launches the application.
     */
    public static final int APP_LINK_TYPE_APP = 2;

    private static final int APP_LINK_TYPE_NOT_SET = 0;
    private static final String INVALID_PACKAGE_NAME = "packageName";

    private static final String[] PROJECTION_BASE = {
        // Columns should match what is read in Channel.fromCursor()
        TvContract.Channels._ID,
        TvContract.Channels.COLUMN_PACKAGE_NAME,
        TvContract.Channels.COLUMN_INPUT_ID,
        TvContract.Channels.COLUMN_TYPE,
        TvContract.Channels.COLUMN_DISPLAY_NUMBER,
        TvContract.Channels.COLUMN_DISPLAY_NAME,
        TvContract.Channels.COLUMN_DESCRIPTION,
        TvContract.Channels.COLUMN_VIDEO_FORMAT,
        TvContract.Channels.COLUMN_BROWSABLE,
        TvContract.Channels.COLUMN_LOCKED,
    };

    // Additional fields added in MNC.
    private static final String[] PROJECTION_ADDED_IN_MNC = {
            // Columns should match what is read in Channel.fromCursor()
            TvContract.Channels.COLUMN_APP_LINK_TEXT,
            TvContract.Channels.COLUMN_APP_LINK_COLOR,
            TvContract.Channels.COLUMN_APP_LINK_ICON_URI,
            TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI,
            TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
    };

    public static final String[] PROJECTION = createProjection();

    private static String[] createProjection() {
        if (TvCommonConstants.IS_MNC_OR_HIGHER) {
            ArrayList<String> temp = new ArrayList<>(
                    PROJECTION_BASE.length + PROJECTION_ADDED_IN_MNC.length);
            temp.addAll(Arrays.asList(PROJECTION_BASE));
            temp.addAll(Arrays.asList(PROJECTION_ADDED_IN_MNC));
            return temp.toArray(new String[temp.size()]);
        } else {
            return PROJECTION_BASE;
        }
    }

    /** ID of this channel. Matches to BaseColumns._ID. */
    private long mId;

    private String mPackageName;
    private String mInputId;
    private String mType;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private String mVideoFormat;
    private boolean mBrowsable;
    private boolean mLocked;
    private boolean mIsPassthrough;
    private String mAppLinkText;
    private int mAppLinkColor;
    private String mAppLinkIconUri;
    private String mAppLinkPosterArtUri;
    private String mAppLinkIntentUri;
    private Intent mAppLinkIntent;
    private int mAppLinkType;

    public interface LoadImageCallback {
        void onLoadImageFinished(Channel channel, int type, Bitmap logo);
    }

    /**
     * Creates {@code Channel} object from cursor.
     * Suppress using this outside of ChannelDataManager
     * so Channels could be managed by ChannelDataManager.
     */
    public static Channel fromCursor(Cursor cursor) {
        // Columns read here should match Channel.PROJECTION

        Channel channel = new Channel();
        int index = cursor.getColumnIndex(TvContract.Channels._ID);
        if (index >= 0) {
            channel.mId = cursor.getLong(index);
        } else {
            channel.mId = INVALID_ID;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_PACKAGE_NAME);
        if (index >= 0) {
            channel.mPackageName = Utils.intern(cursor.getString(index));
        } else {
            channel.mPackageName = INVALID_PACKAGE_NAME;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_INPUT_ID);
        if (index >= 0) {
            channel.mInputId = Utils.intern(cursor.getString(index));
        } else {
            channel.mInputId = "inputId";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TYPE);
        if (index >= 0) {
            channel.mType = Utils.intern(cursor.getString(index));
        } else {
            channel.mType = "type";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        if (index >= 0) {
            channel.mDisplayNumber = cursor.getString(index);
        } else {
            channel.mDisplayNumber = "0";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        if (index >= 0) {
            channel.mDisplayName = cursor.getString(index);
        } else {
            channel.mDisplayName = "name";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DESCRIPTION);
        if (index >= 0) {
            channel.mDescription = cursor.getString(index);
        } else {
            channel.mDescription = "description";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_VIDEO_FORMAT);
        if (index >= 0) {
            channel.mVideoFormat = Utils.intern(cursor.getString(index));
        } else {
            channel.mVideoFormat = "";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_BROWSABLE);
        channel.mBrowsable = index < 0 || cursor.getInt(index) == 1;

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_LOCKED);
        channel.mLocked = index < 0 || cursor.getInt(index) == 1;
        if (TvCommonConstants.IS_MNC_OR_HIGHER) {
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_TEXT);
            if (index >= 0) {
                channel.mAppLinkText = cursor.getString(index);
            }

            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_COLOR);
            if (index >= 0) {
                channel.mAppLinkColor = cursor.getInt(index);
            }

            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_ICON_URI);
            if (index >= 0) {
                channel.mAppLinkIconUri = cursor.getString(index);
            }

            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI);
            if (index >= 0) {
                channel.mAppLinkPosterArtUri = cursor.getString(index);
            }

            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
            if (index >= 0) {
                channel.mAppLinkIntentUri = cursor.getString(index);
            }
        }
        return channel;
    }

    private Channel() {
        // Do nothing.
    }

    public long getId() {
        return mId;
    }

    public Uri getUri() {
        if (isPassthrough()) {
            return TvContract.buildChannelUriForPassthroughInput(mInputId);
        } else {
            return TvContract.buildChannelUri(mId);
        }
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getInputId() {
        return mInputId;
    }

    public String getType() {
        return mType;
    }

    public String getDisplayNumber() {
        return mDisplayNumber;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getVideoFormat() {
        return mVideoFormat;
    }

    public boolean isPassthrough() {
        return mIsPassthrough;
    }

    public String getAppLinkText() {
        return mAppLinkText;
    }

    public int getAppLinkColor() {
        return mAppLinkColor;
    }

    public String getAppLinkIconUri() {
        return mAppLinkIconUri;
    }

    public String getAppLinkPosterArtUri() {
        return mAppLinkPosterArtUri;
    }

    public String getAppLinkIntentUri() {
        return mAppLinkIntentUri;
    }

    /**
     * Checks if two channels equal by checking ids.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Channel)) {
            return false;
        }
        Channel other = (Channel) o;
        // All pass-through TV channels have INVALID_ID value for mId.
        return mId == other.mId && TextUtils.equals(mInputId, other.mInputId)
                && mIsPassthrough == other.mIsPassthrough;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mInputId, mIsPassthrough);
    }

    public boolean isBrowsable() {
        return mBrowsable;
    }

    public boolean isLocked() {
        return mLocked;
    }

    public void setBrowsable(boolean browsable) {
        mBrowsable = browsable;
    }

    public void setLocked(boolean locked) {
        mLocked = locked;
    }

    /**
     * Check whether {@code other} has same read-only channel info as this. But, it cannot check two
     * channels have same logos. It also excludes browsable and locked, because two fields are
     * changed by TV app.
     */
    public boolean hasSameReadOnlyInfo(Channel other) {
        return other != null
                && Objects.equals(mId, other.mId)
                && Objects.equals(mPackageName, other.mPackageName)
                && Objects.equals(mInputId, other.mInputId)
                && Objects.equals(mType, other.mType)
                && Objects.equals(mDisplayNumber, other.mDisplayNumber)
                && Objects.equals(mDisplayName, other.mDisplayName)
                && Objects.equals(mDescription, other.mDescription)
                && Objects.equals(mVideoFormat, other.mVideoFormat)
                && mIsPassthrough == other.mIsPassthrough
                && Objects.equals(mAppLinkText, other.mAppLinkText)
                && mAppLinkColor == other.mAppLinkColor
                && Objects.equals(mAppLinkIconUri, other.mAppLinkIconUri)
                && Objects.equals(mAppLinkPosterArtUri, other.mAppLinkPosterArtUri)
                && Objects.equals(mAppLinkIntentUri, other.mAppLinkIntentUri);
    }

    @Override
    public String toString() {
        return "Channel{"
                + "id=" + mId
                + ", packageName=" + mPackageName
                + ", inputId=" + mInputId
                + ", type=" + mType
                + ", displayNumber=" + mDisplayNumber
                + ", displayName=" + mDisplayName
                + ", description=" + mDescription
                + ", videoFormat=" + mVideoFormat
                + ", isPassthrough=" + mIsPassthrough
                + ", browsable=" + mBrowsable
                + ", locked=" + mLocked
                + ", appLinkText=" + mAppLinkText + "}";
    }

    void copyFrom(Channel other) {
        if (this == other) {
            return;
        }
        mId = other.mId;
        mPackageName = other.mPackageName;
        mInputId = other.mInputId;
        mType = other.mType;
        mDisplayNumber = other.mDisplayNumber;
        mDisplayName = other.mDisplayName;
        mDescription = other.mDescription;
        mVideoFormat = other.mVideoFormat;
        mIsPassthrough = other.mIsPassthrough;
        mBrowsable = other.mBrowsable;
        mLocked = other.mLocked;
        mAppLinkText = other.mAppLinkText;
        mAppLinkColor = other.mAppLinkColor;
        mAppLinkIconUri = other.mAppLinkIconUri;
        mAppLinkPosterArtUri = other.mAppLinkPosterArtUri;
        mAppLinkIntentUri = other.mAppLinkIntentUri;
        mAppLinkIntent = other.mAppLinkIntent;
        mAppLinkType = other.mAppLinkType;
    }

    /**
     * Creates a channel for a passthrough TV input.
     */
    public static Channel createPassthroughChannel(Uri uri) {
        if (!TvContract.isChannelUriForPassthroughInput(uri)) {
            throw new IllegalArgumentException("URI is not a passthrough channel URI");
        }
        String inputId = uri.getPathSegments().get(1);
        return createPassthroughChannel(inputId);
    }

    /**
     * Creates a channel for a passthrough TV input with {@code inputId}.
     */
    public static Channel createPassthroughChannel(String inputId) {
        return new Builder()
                .setInputId(inputId)
                .setPassthrough(true)
                .build();
    }

    /**
     * Checks whether the channel is valid or not.
     */
    public static boolean isValid(Channel channel) {
        return channel != null && (channel.mId != INVALID_ID || channel.mIsPassthrough);
    }

    /**
     * Builder class for {@code Channel}.
     * Suppress using this outside of ChannelDataManager
     * so Channels could be managed by ChannelDataManager.
     */
    public static final class Builder {
        private final Channel mChannel;

        public Builder() {
            mChannel = new Channel();
            // Fill initial data.
            mChannel.mId = INVALID_ID;
            mChannel.mPackageName = INVALID_PACKAGE_NAME;
            mChannel.mInputId = "inputId";
            mChannel.mType = "type";
            mChannel.mDisplayNumber = "0";
            mChannel.mDisplayName = "name";
            mChannel.mDescription = "description";
            mChannel.mBrowsable = true;
            mChannel.mLocked = false;
            mChannel.mIsPassthrough = false;
        }

        public Builder(Channel other) {
            mChannel = new Channel();
            mChannel.copyFrom(other);
        }

        @VisibleForTesting
        public Builder setId(long id) {
            mChannel.mId = id;
            return this;
        }

        @VisibleForTesting
        public Builder setPackageName(String packageName) {
            mChannel.mPackageName = packageName;
            return this;
        }

        public Builder setInputId(String inputId) {
            mChannel.mInputId = inputId;
            return this;
        }

        public Builder setType(String type) {
            mChannel.mType = type;
            return this;
        }

        @VisibleForTesting
        public Builder setDisplayNumber(String displayNumber) {
            mChannel.mDisplayNumber = displayNumber;
            return this;
        }

        @VisibleForTesting
        public Builder setDisplayName(String displayName) {
            mChannel.mDisplayName = displayName;
            return this;
        }

        public Builder setDescription(String description) {
            mChannel.mDescription = description;
            return this;
        }

        public Builder setVideoFormat(String videoFormat) {
            mChannel.mVideoFormat = videoFormat;
            return this;
        }

        public Builder setBrowsable(boolean browsable) {
            mChannel.mBrowsable = browsable;
            return this;
        }

        public Builder setLocked(boolean locked) {
            mChannel.mLocked = locked;
            return this;
        }

        public Builder setPassthrough(boolean isPassthrough) {
            mChannel.mIsPassthrough = isPassthrough;
            return this;
        }

        @VisibleForTesting
        public Builder setAppLinkText(String appLinkText) {
            mChannel.mAppLinkText = appLinkText;
            return this;
        }

        public Builder setAppLinkColor(int appLinkColor) {
            mChannel.mAppLinkColor = appLinkColor;
            return this;
        }

        public Builder setAppLinkIconUri(String appLinkIconUri) {
            mChannel.mAppLinkIconUri = appLinkIconUri;
            return this;
        }

        public Builder setAppLinkPosterArtUri(String appLinkPosterArtUri) {
            mChannel.mAppLinkPosterArtUri = appLinkPosterArtUri;
            return this;
        }

        @VisibleForTesting
        public Builder setAppLinkIntentUri(String appLinkIntentUri) {
            mChannel.mAppLinkIntentUri = appLinkIntentUri;
            return this;
        }

        public Channel build() {
            Channel channel = new Channel();
            channel.copyFrom(mChannel);
            return channel;
        }
    }

    /**
     * Prefetches the images for this channel.
     */
    @UiThread
    public void prefetchImage(Context context, int type, int maxWidth, int maxHeight) {
        String uriString = getImageUriString(type);
        if (!TextUtils.isEmpty(uriString)) {
            ImageLoader.prefetchBitmap(context, uriString, maxWidth, maxHeight);
        }
    }

    /**
     * Loads the bitmap of this channel and returns it via {@code callback}.
     * The loaded bitmap will be cached and resized with given params.
     * <p>
     * Note that it may directly call {@code callback} if the bitmap is already loaded.
     *
     * @param context A context.
     * @param type The type of bitmap which will be loaded. It should be one of follows:
     *        {@link #LOAD_IMAGE_TYPE_CHANNEL_LOGO}, {@link #LOAD_IMAGE_TYPE_APP_LINK_ICON}, or
     *        {@link #LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART}.
     * @param maxWidth The max width of the loaded bitmap.
     * @param maxHeight The max height of the loaded bitmap.
     * @param callback A callback which will be called after the loading finished.
     */
    @UiThread
    public void loadBitmap(Context context, final int type, int maxWidth, int maxHeight,
            final LoadImageCallback callback) {
        String uriString = getImageUriString(type);
        ImageLoader.loadBitmap(context, uriString, maxWidth, maxHeight,
                new ImageLoader.ImageLoaderCallback() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap) {
                        if (callback != null) {
                            callback.onLoadImageFinished(Channel.this, type, bitmap);
                        }
                    }
                });
    }

    /**
     * Returns the type of app link for this channel.
     * It returns {@link #APP_LINK_TYPE_CHANNEL} if the channel has a non null app link text and
     * a valid app link intent, it returns {@link #APP_LINK_TYPE_APP} if the input service which
     * holds the channel has leanback launch intent, and it returns {@link #APP_LINK_TYPE_NONE}
     * otherwise.
     */
    public int getAppLinkType(Context context) {
        if (mAppLinkType == APP_LINK_TYPE_NOT_SET) {
            initAppLinkTypeAndIntent(context);
        }
        return mAppLinkType;
    }

    /**
     * Returns the app link intent for this channel.
     * If the type of app link is {@link #APP_LINK_TYPE_NONE}, it returns {@code null}.
     */
    public Intent getAppLinkIntent(Context context) {
        if (mAppLinkType == APP_LINK_TYPE_NOT_SET) {
            initAppLinkTypeAndIntent(context);
        }
        return mAppLinkIntent;
    }

    private void initAppLinkTypeAndIntent(Context context) {
        mAppLinkType = APP_LINK_TYPE_NONE;
        mAppLinkIntent = null;
        PackageManager pm = context.getPackageManager();
        if (!TextUtils.isEmpty(mAppLinkText) && !TextUtils.isEmpty(mAppLinkIntentUri)) {
            try {
                Intent intent = Intent.parseUri(mAppLinkIntentUri, 0);
                if (intent.resolveActivityInfo(pm, 0) != null) {
                    mAppLinkIntent = intent;
                    mAppLinkIntent.putExtra(TvCommonConstants.EXTRA_APP_LINK_CHANNEL_URI,
                            getUri().toString());
                    mAppLinkType = APP_LINK_TYPE_CHANNEL;
                    return;
                }
            } catch (URISyntaxException e) {
                // Do nothing.
            }
        }
        if (mPackageName.equals(context.getApplicationContext().getPackageName())) {
            return;
        }
        mAppLinkIntent = pm.getLeanbackLaunchIntentForPackage(mPackageName);
        if (mAppLinkIntent != null) {
            mAppLinkIntent.putExtra(TvCommonConstants.EXTRA_APP_LINK_CHANNEL_URI,
                    getUri().toString());
            mAppLinkType = APP_LINK_TYPE_APP;
        }
    }

    private String getImageUriString(int type) {
        switch (type) {
            case LOAD_IMAGE_TYPE_CHANNEL_LOGO:
                return TvContract.buildChannelLogoUri(mId).toString();
            case LOAD_IMAGE_TYPE_APP_LINK_ICON:
                return mAppLinkIconUri;
            case LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART:
                return mAppLinkPosterArtUri;
        }
        return null;
    }

    public static class DefaultComparator implements Comparator<Channel> {
        private final Context mContext;
        private final TvInputManagerHelper mInputManager;
        private final Map<String, String> mInputIdToLabelMap = new HashMap<>();
        private boolean mDetectDuplicatesEnabled;

        public DefaultComparator(Context context, TvInputManagerHelper inputManager) {
            mContext = context;
            mInputManager = inputManager;
        }

        public void setDetectDuplicatesEnabled(boolean detectDuplicatesEnabled) {
            mDetectDuplicatesEnabled = detectDuplicatesEnabled;
        }

        @Override
        public int compare(Channel lhs, Channel rhs) {
            if (Objects.equals(lhs.getInputId(), rhs.getInputId())) {
                // Compare the channel numbers if both channels belong to the same input.
                int compare = ChannelNumber.compare(lhs.getDisplayNumber(), rhs.getDisplayNumber());
                if (mDetectDuplicatesEnabled && compare == 0) {
                    Log.w(TAG, "Duplicate channels detected! - \""
                            + lhs.getDisplayNumber() + " " + lhs.getDisplayName() + "\" and \""
                            + rhs.getDisplayNumber() + " " + rhs.getDisplayName() + "\"");
                }
                return compare;
            } else {
                // Put channels from OEM/SOC inputs first.
                boolean lhsIsPartner = mInputManager.isPartnerInput(lhs.getInputId());
                boolean rhsIsPartner = mInputManager.isPartnerInput(rhs.getInputId());
                if (lhsIsPartner != rhsIsPartner) {
                    return lhsIsPartner ? -1 : 1;
                }

                // Otherwise, compare the input labels.
                String lhsLabel = getInputLabelForChannel(lhs);
                String rhsLabel = getInputLabelForChannel(rhs);
                if (lhsLabel == null && rhsLabel != null) {
                    return 1;
                } else if (lhsLabel != null && rhsLabel == null) {
                    return -1;
                } else if (lhsLabel == null /* && rhsLabel == null */) {
                    return 0;
                }
                return lhsLabel.compareTo(rhsLabel);
            }
        }

        @VisibleForTesting
        String getInputLabelForChannel(Channel channel) {
            String label = mInputIdToLabelMap.get(channel.getInputId());
            if (label == null) {
                TvInputInfo info = mInputManager.getTvInputInfo(channel.getInputId());
                if (info != null) {
                    label = Utils.loadLabel(mContext, info);
                    if (label != null) {
                        mInputIdToLabelMap.put(channel.getInputId(), label);
                    }
                }
            }
            return label;
        }
    }
}

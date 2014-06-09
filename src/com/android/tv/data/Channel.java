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
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A convenience class to create and insert channel entries into the database.
 */
public final class Channel {
    private static final String TAG = "Channel";

    public static final long INVALID_ID = -1;

    /** ID of this channel. Matches to BaseColumns._ID. */
    private long mId;

    private String mServiceName;
    private int mType;
    private int mOriginalNetworkId;
    private int mTransportStreamId;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private boolean mIsBrowsable;
    private byte[] mData;

    private boolean mIsLogoLoaded;
    private LoadLogoTask mLoadLogoTask;
    private Bitmap mLogo;

    public interface LoadLogoCallback {
        void onLoadLogoFinished(Channel channel, Bitmap logo);
    }

    private final List<LoadLogoCallback> mPendingLoadLogoCallbacks =
            new ArrayList<LoadLogoCallback>();

    public static Channel fromCursor(Cursor cursor) {
        Channel channel = new Channel();
        int index = cursor.getColumnIndex(TvContract.Channels._ID);
        if (index >= 0) {
            channel.mId = cursor.getLong(index);
        } else {
            channel.mId = INVALID_ID;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_SERVICE_NAME);
        if (index >= 0) {
            channel.mServiceName = cursor.getString(index);
        } else {
            channel.mServiceName = "serviceName";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TYPE);
        if (index >= 0) {
            channel.mType = cursor.getInt(index);
        } else {
            channel.mType = 0;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID);
        if (index >= 0) {
            channel.mTransportStreamId = cursor.getInt(index);
        } else {
            channel.mTransportStreamId = 0;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID);
        if (index >= 0) {
            channel.mOriginalNetworkId = cursor.getInt(index);
        } else {
            channel.mOriginalNetworkId = 0;
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

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_BROWSABLE);
        if (index >= 0) {
            channel.mIsBrowsable = cursor.getInt(index) == 1;
        } else {
            channel.mIsBrowsable = true;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
        if (index >= 0) {
            channel.mData = cursor.getBlob(index);
        } else {
            channel.mData = null;
        }
        return channel;
    }

    private Channel() {
        // Do nothing.
    }

    public long getId() {
        return mId;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public int getType() {
        return mType;
    }

    public int getOriginalNetworkId() {
        return mOriginalNetworkId;
    }

    public int getTransportStreamId() {
        return mTransportStreamId;
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

    public boolean isBrowsable() {
        return mIsBrowsable;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setBrowsable(boolean browsable) {
        mIsBrowsable = browsable;
    }

    public byte[] getData() {
        return mData;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_SERVICE_NAME, mServiceName);
        values.put(TvContract.Channels.COLUMN_TYPE, mType);
        values.put(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID, mTransportStreamId);
        values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, mDisplayNumber);
        values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, mDisplayName);
        values.put(TvContract.Channels.COLUMN_DESCRIPTION, mDescription);
        values.put(TvContract.Channels.COLUMN_BROWSABLE, mIsBrowsable ? 1 : 0);
        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, mData);
        return values;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Channel{")
                .append("id=").append(mId)
                .append(", serviceName=").append(mServiceName)
                .append(", type=").append(mType)
                .append(", originalNetworkId=").append(mOriginalNetworkId)
                .append(", transportStreamId=").append(mTransportStreamId)
                .append(", displayNumber=").append(mDisplayNumber)
                .append(", displayName=").append(mDisplayName)
                .append(", description=").append(mDescription)
                .append(", browsable=").append(mIsBrowsable)
                .append(", data=").append(mData)
                .append("}")
                .toString();
    }

    public void copyFrom(Channel other) {
        if (this == other) {
            return;
        }
        mId = other.mId;
        mServiceName = other.mServiceName;
        mType = other.mType;
        mTransportStreamId = other.mTransportStreamId;
        mOriginalNetworkId = other.mOriginalNetworkId;
        mDisplayNumber = other.mDisplayNumber;
        mDisplayName = other.mDisplayName;
        mDescription = other.mDescription;
        mIsBrowsable = other.mIsBrowsable;
        mData = other.mData;
    }

    public static final class Builder {
        private final Channel mChannel;

        public Builder() {
            mChannel = new Channel();
            // Fill initial data.
            mChannel.mId = INVALID_ID;
            mChannel.mServiceName = "serviceName";
            mChannel.mType = 0;
            mChannel.mTransportStreamId = 0;
            mChannel.mOriginalNetworkId = 0;
            mChannel.mDisplayNumber = "0";
            mChannel.mDisplayName = "name";
            mChannel.mDescription = "description";
            mChannel.mIsBrowsable = true;
            mChannel.mData = null;
        }

        public Builder(Channel other) {
            mChannel = new Channel();
            mChannel.copyFrom(other);
        }

        public Builder setId(long id) {
            mChannel.mId = id;
            return this;
        }

        public Builder setServiceName(String serviceName) {
            mChannel.mServiceName = serviceName;
            return this;
        }

        public Builder setType(int type) {
            mChannel.mType = type;
            return this;
        }

        public Builder setTransportStreamId(int transportStreamId) {
            mChannel.mTransportStreamId = transportStreamId;
            return this;
        }

        public Builder setOriginalNetworkId(int originalNetworkId) {
            mChannel.mOriginalNetworkId = originalNetworkId;
            return this;
        }

        public Builder setDisplayNumber(String displayNumber) {
            mChannel.mDisplayNumber = displayNumber;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            mChannel.mDisplayName = displayName;
            return this;
        }

        public Builder setDescription(String description) {
            mChannel.mDescription = description;
            return this;
        }

        public Builder setBrowsable(boolean browsable) {
            mChannel.mIsBrowsable = browsable;
            return this;
        }

        public Builder setData(byte[] data) {
            mChannel.mData = data;
            return this;
        }

        public Channel build() {
            return mChannel;
        }
    }

    public boolean isLogoLoaded() {
        return mIsLogoLoaded;
    }

    public boolean isLogoLoading() {
        return mLoadLogoTask != null;
    }

    public Bitmap getLogo() {
        return mLogo;
    }

    // Assumes call from UI thread.
    public void loadLogo(Context context, LoadLogoCallback callback) {
        if (isLogoLoaded()) {
            callback.onLoadLogoFinished(this, mLogo);
        } else {
            mPendingLoadLogoCallbacks.add(callback);
            if (!isLogoLoading()) {
                mLoadLogoTask = new LoadLogoTask(context);
                mLoadLogoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    // Assumes call from UI thread.
    private void setLogo(Bitmap logo) {
        mIsLogoLoaded = true;
        if (isLogoLoading()) {
            mLoadLogoTask.cancel(true);
            mLoadLogoTask = null;
        }
        mLogo = logo;

        for (LoadLogoCallback callback : mPendingLoadLogoCallbacks) {
            callback.onLoadLogoFinished(this, logo);
        }
        mPendingLoadLogoCallbacks.clear();
    }

    private class LoadLogoTask extends AsyncTask<Void, Void, Bitmap> {
        private Context mContext;

        LoadLogoTask(Context context) {
            mContext = context;
        }

        @Override
        public Bitmap doInBackground(Void... params) {
            Log.v(TAG, "Load logo for " + Channel.this);

            InputStream is = null;
            try {
                is = mContext.getContentResolver().openInputStream(
                        TvContract.buildChannelLogoUri(mId));
            } catch (FileNotFoundException e) {
                // Logo may not exist.
                Log.i(TAG, "Logo not found", e);
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode logo image for " + Channel.this);
            }
            return bitmap;
        }

        @Override
        public void onPostExecute(Bitmap logo) {
            if (isCancelled()) {
                Log.w(TAG, "Load logo canceled for " + Channel.this);
                return;
            }
            Log.v(TAG, "Loaded logo for " + Channel.this + ": " + logo);
            mLoadLogoTask = null;
            setLogo(logo);
        }
    }
}

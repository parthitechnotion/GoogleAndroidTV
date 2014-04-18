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

package com.android.tv;

import android.content.ContentValues;
import android.provider.TvContract;

/**
 * A convenience class to create and insert channel entries into the database.
 */
public final class Channel {
    public static final long INVALID_ID = -1;

    /** ID of this channel. Matches to BaseColumns._ID. */
    private long mId;

    private String mServiceName;
    private String mType;
    private int mOriginalNetworkId;
    private int mTransportStreamId;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private boolean mIsBrowsable;
    private String mData;

    private Channel() {
        // Do nothing.
    }

    public long getId() {
        return mId;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public String getType() {
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

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setBrowsable(boolean browsable) {
        mIsBrowsable = browsable;
    }

    public String getData() {
        return mData;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.SERVICE_NAME, mServiceName);
        values.put(TvContract.Channels.TYPE, mType);
        values.put(TvContract.Channels.TRANSPORT_STREAM_ID, mTransportStreamId);
        values.put(TvContract.Channels.DISPLAY_NUMBER, mDisplayNumber);
        values.put(TvContract.Channels.DISPLAY_NAME, mDisplayName);
        values.put(TvContract.Channels.DESCRIPTION, mDescription);
        values.put(TvContract.Channels.BROWSABLE, mIsBrowsable ? 1 : 0);
        values.put(TvContract.Channels.DATA, mData);
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
            mChannel.mType = "type";
            mChannel.mTransportStreamId = 0;
            mChannel.mOriginalNetworkId = 0;
            mChannel.mDisplayNumber = "0";
            mChannel.mDisplayName = "name";
            mChannel.mDescription = "description";
            mChannel.mIsBrowsable = true;
            mChannel.mData = "";
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

        public Builder setType(String type) {
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

        public Builder setData(String data) {
            mChannel.mData = data;
            return this;
        }

        public Channel build() {
            return mChannel;
        }
    }
}

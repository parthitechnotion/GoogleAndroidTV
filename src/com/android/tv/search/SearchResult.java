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

package com.android.tv.search;

public class SearchResult {
    private long mChannelId;
    private String mTitle;
    private String mDescription;
    private String mImageUri;
    private String mIntentAction;
    private String mIntentData;

    private SearchResult() {
        // do nothing
    }

    public long getChannelId() {
        return mChannelId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getImageUri() {
        return mImageUri;
    }

    public String getIntentAction() {
        return mIntentAction;
    }

    public String getIntentData() {
        return mIntentData;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SearchResult result;

        private Builder() {
            result = new SearchResult();
        }

        public SearchResult build() {
            return result;
        }

        public Builder setChannelId(long ChannelId) {
            result.mChannelId = ChannelId;
            return this;
        }

        public Builder setTitle(String title) {
            result.mTitle = title;
            return this;
        }

        public Builder setDescription(String description) {
            result.mDescription = description;
            return this;
        }

        public Builder setImageUri(String imageUri) {
            result.mImageUri = imageUri;
            return this;
        }

        public Builder setIntentAction(String intentAction) {
            result.mIntentAction = intentAction;
            return this;
        }

        public Builder setIntentData(String intentData) {
            result.mIntentData = intentData;
            return this;
        }
    }
}

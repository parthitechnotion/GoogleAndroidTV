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

package com.android.tv.input;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.android.tv.data.ChannelMap;

public abstract class TvInput {
    abstract public String getId();
    abstract public String getDisplayName();
    abstract public Intent getIntentForSetupActivity();
    abstract public Intent getIntentForSettingsActivity();
    abstract public int getInputState();
    abstract public boolean hasChannel(boolean browsableOnly);
    abstract public ChannelMap buildChannelMap(Activity activity, long initialChannelId,
            Runnable onChannelsLoadFinished);

    // TvContract related method
    abstract public Uri buildChannelsUri(String genre);
    abstract public String buildChannelsSortOrder();

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TvInput) {
            return getId().equals(((TvInput) o).getId());
        }
        return false;
    }
}

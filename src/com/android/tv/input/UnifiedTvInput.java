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
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.text.TextUtils;

import com.android.tv.R;
import com.android.tv.data.ChannelMap;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.Collection;

public class UnifiedTvInput extends TvInput {
    public static final String ID = "unified_tv_input_id";

    private final TvInputManagerHelper mInputManagerHelper;
    private final Context mContext;

    public UnifiedTvInput(TvInputManagerHelper inputManagerHelper, Context context) {
        mInputManagerHelper = inputManagerHelper;
        mContext = context;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return mContext.getString(R.string.unified_tv_input_label);
    }

    @Override
    public Intent getIntentForSetupActivity() {
        return null;
    }

    @Override
    public Intent getIntentForSettingsActivity() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return hasChannel(false);
    }

    @Override
    public boolean hasChannel(boolean browsableOnly) {
        Collection<TvInputInfo> inputInfos = mInputManagerHelper.getTvInputInfos(true);
        for (TvInputInfo inputInfo : inputInfos) {
            if (Utils.hasChannel(mContext, inputInfo, browsableOnly)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ChannelMap buildChannelMap(Activity activity, long initialChannelId,
            Runnable onChannelsLoadFinished) {
        return new ChannelMap(activity, this, initialChannelId, mInputManagerHelper,
                onChannelsLoadFinished);
    }

    @Override
    public Uri buildChannelsUri(String genre) {
        if (genre == null) {
            return TvContract.Channels.CONTENT_URI;
        }
        return TvContract.buildChannelsUriForCanonicalGenre(null, genre, true);
    }

    @Override
    public String buildChannelsSortOrder() {
        return Utils.CHANNEL_SORT_ORDER_BY_INPUT_NAME + ", "
                + Utils.CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER;
    }
}

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

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.provider.TvContract;
import android.tv.TvInputInfo;

public class TisTvInput extends TvInput {
    private static final boolean DEBUG = true;
    private static final String TAG = "TisTvInput";

    private final TvInputManagerHelper mInputManagerHelper;
    private final TvInputInfo mInputInfo;
    private final Context mContext;
    private final String mId;

    public TisTvInput(TvInputManagerHelper inputManagerHelper, TvInputInfo inputInfo,
            Context context) {
        mInputManagerHelper = inputManagerHelper;
        mInputInfo = inputInfo;
        mContext = context;
        mId = mInputInfo.getId();
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String getDisplayName() {
        return Utils.getDisplayNameForInput(mContext, mInputInfo);
    }

    @Override
    public boolean hasActivity(String action) {
        return Utils.hasActivity(mContext, mInputInfo, action);
    }

    @Override
    public boolean startActivity(String action) {
        return Utils.startActivity(mContext, mInputInfo, action);
    }

    @Override
    public boolean startActivityForResult(Activity activity, String action, int requestCode) {
        return Utils.startActivityForResult(activity, mInputInfo, action, requestCode);
    }

    @Override
    public boolean isAvailable() {
        return mInputManagerHelper.isAvailable(mId);
    }

    @Override
    public boolean hasChannel(boolean browsableOnly) {
        return Utils.hasChannel(mContext, mInputInfo, browsableOnly);
    }

    @Override
    public ChannelMap buildChannelMap(Activity activity, long initialChannelId,
            Runnable onChannelsLoadFinished) {
        return new ChannelMap(activity, this, initialChannelId, mInputManagerHelper,
                onChannelsLoadFinished);
    }

    @Override
    public Uri buildChannelsUri() {
        return TvContract.buildChannelsUriForInput(mInputInfo.getComponent(), false);
    }

    @Override
    public String buildChannelsSortOrder() {
        return Utils.CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER;
    }
}

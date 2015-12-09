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
 * limitations under the License
 */

package com.android.tv.dvr;

import android.content.Context;

import com.android.tv.Features;
import com.android.tv.common.dvr.DvrSessionClient;
import com.android.tv.data.Channel;
import com.android.tv.util.SoftPreconditions;

/**
 * Manages Dvr Sessions.
 * Responsible for:
 * <ul>
 *     <li>Manage DvrSession</li>
 *     <li>Manage capabilities (conflict)</li>
 * </ul>
 */
public class DvrSessionManager {
    private final static String TAG = "DvrSessionManager";

    public DvrSessionManager(Context context) {
        SoftPreconditions.checkFeatureEnabled(context, Features.DVR, TAG);
    }

    public DvrSessionClient acquireDvrSession(String inputId, Channel channel) {
        return null;
    }

    public boolean canAcquireDvrSession(String inputId, Channel channel) {
        return false;
    }

    public void releaseDvrSession(DvrSessionClient session) {
    }
}

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
import android.support.annotation.MainThread;
import android.util.Log;

import com.android.tv.common.CollectionUtils;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.util.SoftPreconditions;

import java.util.Set;

/**
 * Base implementation of @{link DataManagerInternal}.
 */
@MainThread
public abstract class BaseDvrDataManager implements WritableDvrDataManager {
    private final static String TAG = "BaseDvrDataManager";
    private final static boolean DEBUG = false;

    private final Set<DvrDataManager.Listener> mListeners = CollectionUtils.createSmallSet();

    BaseDvrDataManager (Context context){
        SoftPreconditions.checkFeatureEnabled(context, CommonFeatures.DVR, TAG);
    }

    @Override
    public final void addListener(DvrDataManager.Listener listener) {
        mListeners.add(listener);
    }

    @Override
    public final void removeListener(DvrDataManager.Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Calls {@link DvrDataManager.Listener#onRecordingAdded(Recording)} for each current listener.
     */
    protected final void notifyRecordingAdded(Recording recording) {
        for (Listener l : mListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + "added recording " + recording);
            l.onRecordingAdded(recording);
        }
    }

    /**
     * Calls {@link DvrDataManager.Listener#onRecordingRemoved(Recording)} for each current listener.
     */
    protected final void notifyRecordingRemoved(Recording recording) {
        for (Listener l : mListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + "removed recording " + recording);
            l.onRecordingRemoved(recording);
        }
    }

    /**
     * Calls {@link DvrDataManager.Listener#onRecordingStatusChanged(Recording)} for each current
     * listener.
     */
    protected final void notifyRecordingStatusChanged(Recording recording) {
        for (Listener l : mListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + "changed recording " + recording);
            l.onRecordingStatusChanged(recording);
        }
    }
}

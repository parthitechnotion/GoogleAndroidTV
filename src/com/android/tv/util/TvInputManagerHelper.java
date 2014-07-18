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

package com.android.tv.util;

import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.TvInputCallback;
import android.os.Handler;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TvInputManagerHelper {
    private static final String TAG = "TvInputManagerHelper";

    private final TvInputManager mTvInputManager;
    private final Map<String, Integer> mInputStateMap =
            new HashMap<String, Integer>();
    private final Map<String, TvInputInfo> mInputMap =
            new HashMap<String, TvInputInfo>();
    private final TvInputCallback mInternalCallback =
            new TvInputCallback() {
                @Override
                public void onInputStateChanged(String inputId, int state) {
                    mInputStateMap.put(inputId, state);
                    for (TvInputCallback listener : mCallbacks) {
                        listener.onInputStateChanged(inputId, state);
                    }
                }
            };
    private final Handler mHandler = new Handler();
    private boolean mStarted;
    private final HashSet<TvInputCallback> mCallbacks = new HashSet<TvInputCallback>();

    public TvInputManagerHelper(TvInputManager tvInputManager) {
        mTvInputManager = tvInputManager;
    }

    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
        if (inputs.size() < 1) {
            return;
        }
        mTvInputManager.registerCallback(mInternalCallback, mHandler);
        update();
    }

    // It updates newly installed or deleted TV input.
    // TODO: remove it when TIS package change can be notified from frameworks.
    public void update() {
        if (!mStarted) {
            throw new IllegalStateException("TvInputManagerHelper didn't start yet");
        }
        mInputMap.clear();
        mInputStateMap.clear();
        for (TvInputInfo input : mTvInputManager.getTvInputList()) {
            String inputId = input.getId();
            mInputMap.put(inputId, input);
            int state = mTvInputManager.getInputState(inputId);
            mInputStateMap.put(inputId, state);
        }
        Assert.assertEquals(mInputStateMap.size(), mInputMap.size());
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mTvInputManager.unregisterCallback(mInternalCallback);
        mStarted = false;
        mInputStateMap.clear();
        mInputMap.clear();
    }

    public Collection<TvInputInfo> getTvInputInfos(boolean availableOnly) {
        if (!availableOnly) {
            return mInputMap.values();
        } else {
            ArrayList<TvInputInfo> list = new ArrayList<TvInputInfo>();
            Iterator<Map.Entry<String, Integer>> it =
                    mInputStateMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> pair = it.next();
                if (pair.getValue() != TvInputManager.INPUT_STATE_DISCONNECTED) {
                    list.add(getTvInputInfo(pair.getKey()));
                }
            }
            return list;
        }
    }

    public TvInputInfo getTvInputInfo(String inputId) {
        if (!mStarted) {
            throw new IllegalStateException("TvInputManagerHelper didn't start yet");
        }
        if (inputId == null) {
            return null;
        }
        TvInputInfo input = mInputMap.get(inputId);
        if (input == null) {
            update();
            input = mInputMap.get(inputId);
        }
        return input;
    }

    public int getTvInputSize() {
        return mInputStateMap.size();
    }

    public int getInputState(TvInputInfo inputInfo) {
        return getInputState(inputInfo.getId());
    }

    public int getInputState(String inputId) {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
        }
        Integer state = mInputStateMap.get(inputId);
        if (state == null) {
            update();
            state = mInputStateMap.get(inputId);
            if (state == null) {
                Log.w(TAG, "getInputState: no such input (id=" + inputId + ")");
                return TvInputManager.INPUT_STATE_DISCONNECTED;
            }
        }
        return state;
    }

    public void addCallback(TvInputCallback listener) {
        mCallbacks.add(listener);
    }

    public void removeCallback(TvInputCallback listener) {
        mCallbacks.remove(listener);
    }
}

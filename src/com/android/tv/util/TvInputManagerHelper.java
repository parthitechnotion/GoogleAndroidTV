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

import android.os.Handler;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;
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
    private final Map<String, Boolean> mInputAvailabilityMap =
            new HashMap<String, Boolean>();
    private final Map<String, TvInputInfo> mInputMap =
            new HashMap<String, TvInputInfo>();
    private final TvInputManager.TvInputListener mListener =
            new TvInputManager.TvInputListener() {
                @Override
                public void onAvailabilityChanged(String inputId, boolean isAvailable) {
                    mInputAvailabilityMap.put(inputId, Boolean.valueOf(isAvailable));
                }
            };
    private final Handler mHandler = new Handler();
    private boolean mStarted;

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
        for (TvInputInfo input : inputs) {
            String inputId = input.getId();
            mTvInputManager.registerListener(inputId, mListener, mHandler);
            boolean available = mTvInputManager.getAvailability(inputId);
            mInputAvailabilityMap.put(inputId, available);
            mInputMap.put(inputId, input);
        }
        Assert.assertEquals(mInputAvailabilityMap.size(), mInputMap.size());
    }

    // It updates newly installed or deleted TV input.
    // TODO: remove it when TIS package change can be notified from frameworks.
    public void update() {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
        }
        Set<String> inputIds = new HashSet<String>();
        mInputMap.clear();
        for (TvInputInfo input : mTvInputManager.getTvInputList()) {
            inputIds.add(input.getId());
            mInputMap.put(input.getId(), input);
        }
        for (String inputId : inputIds) {
            if (mInputAvailabilityMap.get(inputId) != null) {
                continue;
            }
            mTvInputManager.registerListener(inputId, mListener, mHandler);
            boolean available = mTvInputManager.getAvailability(inputId);
            mInputAvailabilityMap.put(inputId, available);
        }
        for (String inputId : mInputAvailabilityMap.keySet()) {
            if (!inputIds.contains(inputId)) {
                mTvInputManager.unregisterListener(inputId, mListener);
                mInputAvailabilityMap.remove(inputId);
            }
        }
        Assert.assertEquals(mInputAvailabilityMap.size(), mInputMap.size());
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        for (String inputId : mInputAvailabilityMap.keySet()) {
            mTvInputManager.unregisterListener(inputId, mListener);
        }
        mInputAvailabilityMap.clear();
        mInputMap.clear();
    }

    public Collection<TvInputInfo> getTvInputInfos(boolean availableOnly) {
        if (!availableOnly) {
            return mInputMap.values();
        } else {
            ArrayList<TvInputInfo> list = new ArrayList<TvInputInfo>();
            Iterator<Map.Entry<String, Boolean>> it =
                    mInputAvailabilityMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Boolean> pair = it.next();
                if (pair.getValue() == true) {
                    list.add(getTvInputInfo(pair.getKey()));
                }
            }
            return list;
        }
    }

    public TvInputInfo getTvInputInfo(String inputId) {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
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
        return mInputAvailabilityMap.size();
    }

    public boolean isAvailable(TvInputInfo inputInfo) {
        return isAvailable(inputInfo.getId());
    }

    public boolean isAvailable(String inputId) {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
        }
        Boolean available = mInputAvailabilityMap.get(inputId);
        if (available == null) {
            update();
            available = mInputAvailabilityMap.get(inputId);
            if (available == null) {
                Log.w(TAG, "isAvailable: no such input (id=" + inputId + ")");
                return false;
            }
        }
        return available;
    }
}
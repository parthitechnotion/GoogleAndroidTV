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

import android.content.ComponentName;
import android.os.Handler;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;

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
    private final TvInputManager mTvInputManager;
    private final Map<ComponentName, Boolean> mInputAvailabilityMap =
            new HashMap<ComponentName, Boolean>();
    private final Map<ComponentName, TvInputInfo> mInputMap =
            new HashMap<ComponentName, TvInputInfo>();
    private final TvInputManager.TvInputListener mListener =
            new TvInputManager.TvInputListener() {
                @Override
                public void onAvailabilityChanged(ComponentName name, boolean isAvailable) {
                    mInputAvailabilityMap.put(name, Boolean.valueOf(isAvailable));
                }
            };
    private final Handler mHandler = new Handler();
    private boolean mStarted;

    TvInputManagerHelper(TvInputManager tvInputManager) {
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
            ComponentName inputName = input.getComponent();
            mTvInputManager.registerListener(inputName, mListener, mHandler);
            boolean available = mTvInputManager.getAvailability(inputName);
            mInputAvailabilityMap.put(inputName, available);
            mInputMap.put(inputName, input);
        }
        Assert.assertEquals(mInputAvailabilityMap.size(), mInputMap.size());
    }

    // It updates newly installed or deleted TV input.
    // TODO: remove it when TIS package change can be notified from frameworks.
    public void update() {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
        }
        Set<ComponentName> inputNames = new HashSet<ComponentName>();
        mInputMap.clear();
        for (TvInputInfo input : mTvInputManager.getTvInputList()) {
            inputNames.add(input.getComponent());
            mInputMap.put(input.getComponent(), input);
        }
        for (ComponentName inputName : inputNames) {
            if (mInputAvailabilityMap.get(inputName) != null) {
                continue;
            }
            mTvInputManager.registerListener(inputName, mListener, mHandler);
            boolean available = mTvInputManager.getAvailability(inputName);
            mInputAvailabilityMap.put(inputName, available);
        }
        for (ComponentName name : mInputAvailabilityMap.keySet()) {
            if (!inputNames.contains(name)) {
                mTvInputManager.unregisterListener(name, mListener);
                mInputAvailabilityMap.remove(name);
            }
        }
        Assert.assertEquals(mInputAvailabilityMap.size(), mInputMap.size());
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        for (ComponentName inputName : mInputAvailabilityMap.keySet()) {
            mTvInputManager.unregisterListener(inputName, mListener);
        }
        mInputAvailabilityMap.clear();
        mInputMap.clear();
    }

    public Collection<TvInputInfo> getTvInputInfos(boolean availableOnly) {
        if (!availableOnly) {
            return mInputMap.values();
        } else {
            ArrayList<TvInputInfo> list = new ArrayList<TvInputInfo>();
            Iterator<Map.Entry<ComponentName, Boolean>> it =
                    mInputAvailabilityMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ComponentName, Boolean> pair = it.next();
                if (pair.getValue() == true) {
                    list.add(getTvInputInfo(pair.getKey()));
                }
            }
            return list;
        }
    }

    public TvInputInfo getTvInputInfo(ComponentName name) {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
        }
        TvInputInfo input = mInputMap.get(name);
        if (input == null) {
            update();
            input = mInputMap.get(name);
        }
        return input;
    }

    public int getTvInputSize() {
        return mInputAvailabilityMap.size();
    }

    public boolean isAvaliable(ComponentName inputName) {
        if (!mStarted) {
            throw new IllegalStateException("AvailabilityManager doesn't started");
        }
        Boolean available = mInputAvailabilityMap.get(inputName);
        if (available == null) {
            update();
            available = mInputAvailabilityMap.get(inputName);
            if (available == null) {
                throw new IllegalArgumentException("inputName (" + inputName + ") doesn't exist");
            }
        }
        return available;
    }
}
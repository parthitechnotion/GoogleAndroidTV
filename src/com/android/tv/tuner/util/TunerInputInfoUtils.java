/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.tuner.util;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.util.Log;
import android.util.Pair;

import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.tuner.R;
import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.tvinput.TunerTvInputService;

/**
 * Utility class for providing tuner input info.
 */
public class TunerInputInfoUtils {
    private static final String TAG = "TunerInputInfoUtils";
    private static final boolean DEBUG = false;

    /**
     * Builds tuner input's info.
     */
    @Nullable
    @TargetApi(Build.VERSION_CODES.N)
    public static TvInputInfo buildTunerInputInfo(Context context) {
        Pair<Integer, Integer> tunerTypeAndCount = TunerHal.getTunerTypeAndCount(context);
        if (tunerTypeAndCount.first == null || tunerTypeAndCount.second == 0) {
            return null;
        }
        int inputLabelId = 0;
        switch (tunerTypeAndCount.first) {
            case TunerHal.TUNER_TYPE_BUILT_IN:
                inputLabelId = R.string.bt_app_name;
                break;
            case TunerHal.TUNER_TYPE_USB:
                inputLabelId = R.string.ut_app_name;
                break;
            case TunerHal.TUNER_TYPE_NETWORK:
                inputLabelId = R.string.nt_app_name;
                break;
        }
        try {
            TvInputInfo.Builder builder = new TvInputInfo.Builder(context,
                    new ComponentName(context, TunerTvInputService.class));
            return builder.setLabel(inputLabelId)
                    .setCanRecord(CommonFeatures.DVR.isEnabled(context))
                    .setTunerCount(tunerTypeAndCount.second)
                    .build();
        } catch (IllegalArgumentException | NullPointerException e) {
            // TunerTvInputService is not enabled.
            return null;
        }
    }

    /**
     * Updates tuner input's info.
     *
     * @param context {@link Context} instance
     */
    public static void updateTunerInputInfo(Context context) {
        if (BuildCompat.isAtLeastN()) {
            if (DEBUG) Log.d(TAG, "updateTunerInputInfo()");
            TvInputInfo info = buildTunerInputInfo(context);
            if (info != null) {
                ((TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE))
                        .updateTvInputInfo(info);
                if (DEBUG) {
                    Log.d(TAG, "TvInputInfo [" + info.loadLabel(context)
                            + "] updated: " + info.toString());
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Updating tuner input's info failed. Input is not ready yet.");
                }
            }
        }
    }
}
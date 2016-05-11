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

import android.content.ComponentName;
import android.content.Context;
import android.media.tv.TvContract;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;

import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.recording.RecordingCapability;
import com.android.tv.common.recording.TvRecording;
import com.android.tv.data.Channel;
import com.android.tv.util.SoftPreconditions;
import com.android.usbtuner.tvinput.UsbTunerTvInputService;

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
    private final Context mContext;
    private TvRecording.TvRecordingClient mRecordingClient;
    private ArrayMap<String, RecordingCapability> mCapabilityMap = new ArrayMap<>();

    public DvrSessionManager(Context context) {
        SoftPreconditions.checkFeatureEnabled(context, CommonFeatures.DVR, TAG);
        mContext = context.getApplicationContext();
        // TODO(DVR): get a session to all clients, for now just get USB a TestInput
        final String inputId = TvContract
                .buildInputId(new ComponentName(context, UsbTunerTvInputService.class));
        mRecordingClient = acquireDvrSession(inputId, null);
        mRecordingClient.connect(inputId, new TvRecording.ClientCallback() {
            @Override
            public void onCapabilityReceived(RecordingCapability capability) {
                mCapabilityMap.put(inputId, capability);
                mRecordingClient.release();
                mRecordingClient = null;
            }
        });
        if (CommonFeatures.DVR.isEnabled(context)) { // STOPSHIP(DVR)
            String testInputId = "com.android.tv.testinput/.TestTvInputService";
            mCapabilityMap.put(testInputId,
                    RecordingCapability.builder()
                            .setInputId(testInputId)
                            .setMaxConcurrentPlayingSessions(2)
                            .setMaxConcurrentTunedSessions(2)
                            .setMaxConcurrentSessionsOfAllTypes(3)
                            .build());

        }
    }

    public TvRecording.TvRecordingClient acquireDvrSession(String inputId, Channel channel) {
        // TODO(DVR): use input and channel or change API
        TvRecording.TvRecordingClient sessionClient = new TvRecording.TvRecordingClient(mContext);
        return sessionClient;
    }

    public boolean canAcquireDvrSession(String inputId, Channel channel) {
        // TODO(DVR): implement
        return true;
    }

    public void releaseDvrSession(TvRecording.TvRecordingClient session) {
        session.release();
    }

    @Nullable
    public RecordingCapability getRecordingCapability(String inputId) {
        return mCapabilityMap.get(inputId);
    }
}

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

package com.android.tv.data;

import android.media.tv.TvInputInfo;

public interface StreamInfo {
    public final int VIDEO_DEFINITION_LEVEL_UNKNOWN = 0;
    public final int VIDEO_DEFINITION_LEVEL_SD = 1;
    public final int VIDEO_DEFINITION_LEVEL_HD = 2;
    public final int VIDEO_DEFINITION_LEVEL_FULL_HD = 3;
    public final int VIDEO_DEFINITION_LEVEL_ULTRA_HD = 4;

    public final int AUDIO_CHANNEL_COUNT_UNKNOWN = 0;

    TvInputInfo getCurrentTvInputInfo();

    int getVideoWidth();
    int getVideoHeight();
    int getVideoDefinitionLevel();
    int getAudioChannelCount();
    boolean hasClosedCaption();
}

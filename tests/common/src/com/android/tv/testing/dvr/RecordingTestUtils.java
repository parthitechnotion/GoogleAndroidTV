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

package com.android.tv.testing.dvr;

import com.android.tv.data.Channel;
import com.android.tv.dvr.Recording;

import java.util.Collections;

/**
 * Static utils for using {@link Recording} in tests.
 */
public class RecordingTestUtils {
    public static Recording createTestRecordingWithPeriod(long id, long startTime, long endTime) {
        return Recording.builder(new Channel.Builder().build(), startTime, endTime)
                .setId(id)
                .setPrograms(Collections.EMPTY_LIST)
                .build();
    }
}

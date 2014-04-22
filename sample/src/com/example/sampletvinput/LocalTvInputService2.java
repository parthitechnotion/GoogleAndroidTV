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

package com.example.sampletvinput;

public class LocalTvInputService2 extends BaseLocalTvInputService {
    @Override
    public void createSampleChannels() {
        mNumberOfChannels = 3;
        mSamples = new Sample[] {
                new Sample(R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz,
                        "Sample Video A for LocalSampleTvInputService2"),
                new Sample(R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz,
                        "Sample Video B for LocalSampleTvInputService2") };
    }
}

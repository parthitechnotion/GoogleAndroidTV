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

import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalFileTvInputService extends BaseTvInputService {
    private static String TAG = "ExternalFileTvInputService";
    private static String CHANNEL_XML_PATH = "/sdcard/tvinput/channels.xml";

    @Override
    public List<ChannelInfo> createSampleChannels() {
        Log.d(TAG, "createSampleChannels");
        List<ChannelInfo> list = new ArrayList<ChannelInfo>();
        File file = new File(CHANNEL_XML_PATH);
        try {
            FileInputStream is = new FileInputStream(file);
            list = ChannelXMLParser.parseChannelXML(is);
        } catch (XmlPullParserException | IOException e) {
            // TODO: Disable this service.
            Log.w(TAG, "failed to load channels.");
        }
        return list;
    }
}

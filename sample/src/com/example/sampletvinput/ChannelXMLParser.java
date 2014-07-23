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

import android.media.tv.TvContentRating;
import android.util.Log;
import android.util.Xml;

import com.example.sampletvinput.BaseTvInputService.ChannelInfo;
import com.example.sampletvinput.BaseTvInputService.ProgramInfo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class ChannelXMLParser {
    private static String TAG = "ChannelXmlParser";

    private static final String TAG_CHANNELS = "Channels";
    private static final String TAG_CHANNEL = "Channel";
    private static final String TAG_PROGRAM = "Program";

    private static final String ATTR_DISPLAY_NUMBNER = "display_number";
    private static final String ATTR_DISPLAY_NAME = "display_name";
    private static final String ATTR_VIDEO_WIDTH = "video_width";
    private static final String ATTR_VIDEO_HEIGHT = "video_height";
    private static final String ATTR_AUDIO_CHANNEL_COUNT = "audio_channel_count";
    private static final String ATTR_HAS_CLOSED_CAPTION = "has_closed_caption";
    private static final String ATTR_LOGO_URL = "logo_url";

    private static final String ATTR_TITLE = "title";
    private static final String ATTR_POSTER_ART_URI = "poster_art_uri";
    private static final String ATTR_START_TIME = "start_time";
    private static final String ATTR_DURATION_SEC = "duration_sec";
    private static final String ATTR_VIDEO_URL = "video_url";
    private static final String ATTR_DESCRIPTION = "description";
    private static final String ATTR_CONTENT_RATING = "content_rating";

    public static List<ChannelInfo> parseChannelXML(InputStream in)
            throws XmlPullParserException, IOException {
        Log.d(TAG, "parseChannelXML");
        List<ChannelInfo> list = new ArrayList<ChannelInfo>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null);
        parser.nextTag();

        parser.require(XmlPullParser.START_TAG, null, TAG_CHANNELS);
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && TAG_CHANNEL.equals(parser.getName())) {
                list.add(parseChannel(parser));
            }
        }
        return list;
    }

    private static ChannelInfo parseChannel(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Log.d(TAG, "parseChannel " + parser.getAttributeCount());
        String displayNumber = null;
        String displayName = null;
        int videoWidth = 0;
        int videoHeight = 0;
        int audioChannelCount = 0;
        boolean hasClosedCaption = false;
        String logoUrl = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_DISPLAY_NUMBNER.equals(attr)) {
                displayNumber = value;
            } else if (ATTR_DISPLAY_NAME.equals(attr)) {
                displayName = value;
            } else if (ATTR_VIDEO_WIDTH.equals(attr)) {
                videoWidth = Integer.parseInt(value);
            } else if (ATTR_VIDEO_HEIGHT.equals(attr)) {
                videoHeight = Integer.parseInt(value);
            } else if (ATTR_AUDIO_CHANNEL_COUNT.equals(attr)) {
                audioChannelCount = Integer.parseInt(value);
            } else if (ATTR_HAS_CLOSED_CAPTION.equals(attr)) {
                hasClosedCaption = "true".equalsIgnoreCase(value);
            } else if (ATTR_LOGO_URL.equals(attr)) {
                logoUrl = value;
            }
        }
        ProgramInfo program = null;
        int depth = 0;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                depth++;
                if (TAG_PROGRAM.equals(parser.getName()) && program == null) {
                    program = parseProgram(parser);
                }
            } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        return new ChannelInfo(displayNumber, displayName, logoUrl, videoWidth, videoHeight,
                audioChannelCount, hasClosedCaption, program);
    }

    private static ProgramInfo parseProgram(XmlPullParser parser) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String title = null;
        long startTimeSec = 0;
        long durationSec = 0;
        String videoUrl = null;
        String description = null;
        String posterArtUri = null;
        String contentRatings = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_TITLE.equals(attr)) {
                title = value;
            } else if (ATTR_POSTER_ART_URI.equals(attr)) {
                posterArtUri = value;
            } else if (ATTR_START_TIME.equals(attr)) {
                try {
                    startTimeSec = format.parse(value).getTime() / 1000;
                } catch (ParseException e) {
                    Log.w(TAG, "Malformed start time value - " + value);
                }
            } else if (ATTR_DURATION_SEC.equals(attr)) {
                durationSec = Integer.parseInt(value);
            } else if (ATTR_VIDEO_URL.equals(attr)) {
                videoUrl = value;
            } else if (ATTR_DESCRIPTION.equals(attr)) {
                description = value;
            } else if (ATTR_CONTENT_RATING.equals(attr)) {
                contentRatings = value;
            }
        }
        return new ProgramInfo(title, posterArtUri, description, startTimeSec, durationSec,
                Utils.stringToContentRatings(contentRatings), videoUrl, 0);
    }
}

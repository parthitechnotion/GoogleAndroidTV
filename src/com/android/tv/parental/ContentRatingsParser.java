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

package com.android.tv.parental;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.android.tv.parental.ContentRatingSystem.Rating;
import com.android.tv.parental.ContentRatingSystem.SubRating;
import com.android.tv.parental.ContentRatingSystem.Order;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ContentRatingsParser {
    private final static String TAG = "ContentRatingsParser";

    private final static String TAG_RATING_SYSTEM_DEFINITIONS = "rating-system-definitions";
    private final static String TAG_RATING_SYSTEM_DEFINITION = "rating-system-definition";
    private final static String TAG_SUB_RATING_DEFINITION = "sub-rating-definition";
    private final static String TAG_RATING_DEFINITION = "rating-definition";
    private final static String TAG_SUB_RATING = "sub-rating";
    private final static String TAG_ORDER = "order";
    private final static String TAG_RATING = "rating";

    private final static String ATTR_ID = "id";
    private final static String ATTR_DISPLAY_NAME = "displayName";
    private final static String ATTR_COUNTRY = "country";
    private final static String ATTR_ICON = "icon";
    private final static String ATTR_DESCRIPTION = "description";
    private final static String ATTR_AGE_HINT = "ageHint";

    private ContentRatingsParser() {
        // Prevent instantiation.
    }

    public static List<ContentRatingSystem> parse(Context context, Uri uri) {
        List<ContentRatingSystem> ratingSystems = null;
        XmlResourceParser parser = null;
        try {
            if (!uri.getScheme().equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
                throw new IllegalArgumentException("Invalid URI scheme " + uri);
            }
            String packageName = uri.getAuthority();
            int resId = (int) ContentUris.parseId(uri);
            parser = context.getPackageManager().getXml(packageName, resId, null);
            if (parser == null) {
                throw new IllegalArgumentException("Cannot get XML with URI " + uri);
            }
            ratingSystems = parse(parser, packageName);
        } catch (Exception e) {
            // Catching all exceptions and print which URI is malformed XML with description
            // and stack trace here.
            // TODO: We may want to print message to stdout. see b/16803331
            Log.w(TAG, "Error parsing XML " + uri, e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }

        return ratingSystems;
    }

    private static void assertEquals(int a, int b, String msg) throws XmlPullParserException {
        if (a != b) {
            throw new XmlPullParserException(msg);
        }
    }

    private static void assertEquals(String a, String b, String msg) throws XmlPullParserException {
        if (!b.equals(a)) {
            throw new XmlPullParserException(msg);
        }
    }

    private static List<ContentRatingSystem> parse(XmlResourceParser parser, String domain)
            throws XmlPullParserException, IOException {
        // Consume all START_DOCUMENT which can appear more than once.
        while (parser.next() == XmlPullParser.START_DOCUMENT);

        int eventType = parser.getEventType();
        assertEquals(eventType, XmlPullParser.START_TAG, "Malformed XML: Not a valid XML file");
        assertEquals(parser.getName(), TAG_RATING_SYSTEM_DEFINITIONS,
                "Malformed XML: Should start with tag " + TAG_RATING_SYSTEM_DEFINITIONS);

        List<ContentRatingSystem> ratingSystems = new ArrayList<ContentRatingSystem>();
        while (true) {
            eventType = parser.nextTag();

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    assertEquals(parser.getName(), TAG_RATING_SYSTEM_DEFINITION,
                            "Malformed XML: Should contains " +
                                    TAG_RATING_SYSTEM_DEFINITION);
                    ratingSystems.add(parseRatingSystem(parser, domain));
                    break;
                case XmlPullParser.END_TAG:
                    assertEquals(parser.getName(), TAG_RATING_SYSTEM_DEFINITIONS,
                            "Malformed XML: Should end with tag " +
                                    TAG_RATING_SYSTEM_DEFINITIONS);
                    eventType = parser.next();
                    assertEquals(eventType, XmlPullParser.END_DOCUMENT,
                            "Malformed XML: Should end with tag " +
                                    TAG_RATING_SYSTEM_DEFINITIONS);
                    return ratingSystems;
                default:
                    throw new XmlPullParserException("Malformed XML: Error in " +
                            TAG_RATING_SYSTEM_DEFINITIONS);
            }
        }
    }

    private static ContentRatingSystem parseRatingSystem(XmlResourceParser parser, String domain)
            throws XmlPullParserException, IOException {
        ContentRatingSystem.Builder builder = new ContentRatingSystem.Builder();

        builder.setDomain(domain);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attr = parser.getAttributeName(i);
            switch (attr) {
                case ATTR_ID:
                    builder.setId(parser.getAttributeValue(i));
                    break;
                case ATTR_COUNTRY:
                    builder.setCountry(parser.getAttributeValue(i));
                    break;
                case ATTR_DISPLAY_NAME:
                    builder.setDisplayName(parser.getAttributeValue(i));
                    break;
                case ATTR_DESCRIPTION:
                    builder.setDisplayName(parser.getAttributeValue(i));
                    break;
                default:
                    throw new XmlPullParserException("Malformed XML: Unknown attribute " + attr +
                            " in " + TAG_RATING_SYSTEM_DEFINITIONS);
            }
        }

        while (true) {
            int eventType = parser.nextTag();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    String tag = parser.getName();
                    switch (tag) {
                        case TAG_RATING_DEFINITION:
                            builder.addRatingBuilder(parseRating(parser));
                            break;
                        case TAG_SUB_RATING_DEFINITION:
                            builder.addSubRatingBuilder(parseSubRating(parser));
                            break;
                        case TAG_ORDER:
                            builder.addOrderBuilder(parseOrder(parser));
                            break;
                        default:
                            throw new XmlPullParserException("Malformed XML: Unknown tag " + tag +
                                    " in " + TAG_RATING_SYSTEM_DEFINITION);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    assertEquals(parser.getName(), TAG_RATING_SYSTEM_DEFINITION,
                            "Malformed XML: Tag mismatch for " + TAG_RATING_SYSTEM_DEFINITION);
                    return builder.build();
                default:
                    throw new XmlPullParserException("Malformed XML: Tag is expected in " +
                            TAG_RATING_SYSTEM_DEFINITION);
            }
        }
    }

    private static Rating.Builder parseRating(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        Rating.Builder builder = new Rating.Builder();

        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attr = parser.getAttributeName(i);
            switch (attr) {
                case ATTR_ID:
                    builder.setId(parser.getAttributeValue(i));
                    break;
                case ATTR_DISPLAY_NAME:
                    builder.setDisplayName(parser.getAttributeValue(i));
                    break;
                case ATTR_DESCRIPTION:
                    builder.setDisplayName(parser.getAttributeValue(i));
                    break;
                case ATTR_ICON:
                    builder.setIconUri(Uri.parse(parser.getAttributeValue(i)));
                    break;
                case ATTR_AGE_HINT:
                    int ageHint = -1;
                    try {
                        ageHint = Integer.parseInt(parser.getAttributeValue(i));
                    } catch (NumberFormatException e) {
                    }

                    if (ageHint < 0) {
                        throw new XmlPullParserException("Malformed XML: " + ATTR_AGE_HINT +
                                " should be a non-negative number");
                    }
                    builder.setAgeHint(ageHint);
                    break;
                default:
                    throw new XmlPullParserException("Malformed XML: Unknown attribute " + attr +
                            " in " + TAG_RATING_DEFINITION);
            }
        }

        while (true) {
            int eventType = parser.nextTag();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    assertEquals(parser.getName(), TAG_SUB_RATING,
                            "Malformed XML: Only " + TAG_SUB_RATING + " is allowed in " +
                                    TAG_RATING_DEFINITION);
                    if (parser.getAttributeCount() != 1 ||
                            !ATTR_ID.equals(parser.getAttributeName(0))) {
                        throw new XmlPullParserException("Malformed XML: " + TAG_SUB_RATING +
                                " should only contain " + ATTR_ID);
                    }
                    builder.addSubRatingId(parser.getAttributeValue(0));
                    eventType = parser.nextTag();
                    if (eventType != XmlPullParser.END_TAG ||
                            !TAG_SUB_RATING.equals(parser.getName())) {
                        throw new XmlPullParserException("Malformed XML: " + TAG_SUB_RATING +
                                " has child");
                    }
                    break;
                case XmlPullParser.END_TAG:
                    assertEquals(parser.getName(), TAG_RATING_DEFINITION,
                            "Malformed XML: Tag mismatch for " + TAG_RATING_DEFINITION);
                    return builder;

                default:
                    throw new XmlPullParserException("Malformed XML: Error in " +
                            TAG_RATING_DEFINITION);
            }
        }
    }

    private static SubRating.Builder parseSubRating(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        SubRating.Builder builder = new SubRating.Builder();

        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attr = parser.getAttributeName(i);
            switch (attr) {
                case ATTR_ID:
                    builder.setId(parser.getAttributeValue(i));
                    break;
                case ATTR_DISPLAY_NAME:
                    builder.setDisplayName(parser.getAttributeValue(i));
                    break;
                case ATTR_DESCRIPTION:
                    builder.setDisplayName(parser.getAttributeValue(i));
                    break;
                case ATTR_ICON:
                    builder.setIconUri(Uri.parse(parser.getAttributeValue(i)));
                    break;
                default:
                    throw new XmlPullParserException("Malformed XML: Unknown attribute " + attr +
                            " in " + TAG_SUB_RATING_DEFINITION);
            }
        }

        assertEquals(parser.nextTag(), XmlPullParser.END_TAG,
                "Malformed XML: " + TAG_SUB_RATING_DEFINITION + " has child");
        assertEquals(parser.getName(), TAG_SUB_RATING_DEFINITION,
                "Malformed XML: " + TAG_SUB_RATING_DEFINITION + " isn't closed");

        return builder;
    }

    private static Order.Builder parseOrder(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        Order.Builder builder = new Order.Builder();

        assertEquals(parser.getAttributeCount(), 0,
                "Malformed XML: Attribute isn't allowed in " + TAG_ORDER);

        while (true) {
            int eventType = parser.nextTag();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    assertEquals(parser.getName(), TAG_RATING,
                            "Malformed XML: Only " + TAG_RATING + " is allowed in " +
                                    TAG_ORDER);
                    if (parser.getAttributeCount() != 1 ||
                            !ATTR_ID.equals(parser.getAttributeName(0))) {
                        throw new XmlPullParserException("Malformed XML: " + TAG_ORDER +
                                " should only contain " + ATTR_ID);
                    }
                    builder.addRatingId(parser.getAttributeValue(0));
                    eventType = parser.nextTag();
                    if (eventType != XmlPullParser.END_TAG ||
                            !TAG_RATING.equals(parser.getName())) {
                        throw new XmlPullParserException("Malformed XML: " + TAG_RATING +
                                " has child");
                    }
                    break;
                case XmlPullParser.END_TAG:
                    assertEquals(parser.getName(), TAG_ORDER,
                            "Malformed XML: Tag mismatch for " + TAG_ORDER);
                    return builder;
                default:
                    throw new XmlPullParserException("Malformed XML: Error in " + TAG_ORDER);
            }
        }
    }
}

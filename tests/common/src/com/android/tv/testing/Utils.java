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
 * limitations under the License.
 */

package com.android.tv.testing;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An utility class for testing.
 *
 * <p>This class is also used to check whether TV app is running in tests or not.
 *
 * @see com.android.tv.util.Utils#isRunningInTest
 */
public class Utils {
    public static String getUriStringForResource(Context context, int resId) {
        if (resId == 0) {
            return "";
        }
        Resources res = context.getResources();
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(res.getResourcePackageName(resId))
            .path(res.getResourceTypeName(resId))
            .appendPath(res.getResourceEntryName(resId)).build().toString();
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    public static String getServiceNameFromInputId(Context context, String inputId) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.getId().equals(inputId)) {
                return info.getServiceInfo().name;
            }
        }
        return null;
    }

    public static String getInputIdFromComponentName(Context context, ComponentName name) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : tim.getTvInputList()) {
            ServiceInfo si = info.getServiceInfo();
            if (new ComponentName(si.packageName, si.name).equals(name)) {
                return info.getId();
            }
        }
        return null;
    }

    public static TvContentRating[] stringToContentRatings(String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return null;
        }
        String[] ratings = commaSeparatedRatings.split("\\s*,\\s*");
        TvContentRating[] contentRatings = new TvContentRating[ratings.length];
        for (int i = 0; i < contentRatings.length; ++i) {
            contentRatings[i] = TvContentRating.unflattenFromString(ratings[i]);
        }
        return contentRatings;
    }

    public static String contentRatingsToString(TvContentRating[] contentRatings) {
        if (contentRatings == null || contentRatings.length == 0) {
            return null;
        }
        final String DELIMITER = ",";
        StringBuilder ratings = new StringBuilder(contentRatings[0].flattenToString());
        for (int i = 1; i < contentRatings.length; ++i) {
            ratings.append(DELIMITER);
            ratings.append(contentRatings[i].flattenToString());
        }
        return ratings.toString();
    }

    private Utils() {}
}
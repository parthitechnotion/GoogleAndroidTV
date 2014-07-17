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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.util.Log;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class Utils {
    private static final String TAG = "Utils";
    private static final boolean DEBUG = true;

    public static void insertFile(Context context, Uri contentUri, File file) {
        if (DEBUG) {
            Log.d(TAG, "Inserting " + file + " to " + contentUri);
        }
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(file);
            os = context.getContentResolver().openOutputStream(contentUri);
            copy(is, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to write " + file + "  to " + contentUri, ioe);
        } finally {
            IoUtils.closeQuietly(is);
            IoUtils.closeQuietly(os);
        }
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

    private Utils() {}
}

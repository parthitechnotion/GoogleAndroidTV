/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.location.Address;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.tuner.TunerPreferences;
import com.android.tv.util.LocationUtils;

import java.io.IOException;
import java.util.Locale;

/**
 * A utility class to update, get, and set the last known postal or zip code.
 */
public class PostalCodeUtils {
    private static final String TAG = "PostalCodeUtils";
    private static final String SUPPORTED_COUNTRY_CODE = Locale.US.getCountry();

    /** Returns {@code true} if postal code has been changed */
    public static boolean updatePostalCode(Context context)
            throws IOException, SecurityException, NoPostalCodeException {
        String postalCode = getPostalCode(context);
        String lastPostalCode = getLastPostalCode(context);
        if (TextUtils.isEmpty(postalCode)) {
            if (TextUtils.isEmpty(lastPostalCode)) {
                throw new NoPostalCodeException();
            }
        } else if (!TextUtils.equals(postalCode, lastPostalCode)) {
            setLastPostalCode(context, postalCode);
            return true;
        }
        return false;
    }

    /**
     * Gets the last stored postal or zip code, which might be decided by {@link LocationUtils} or
     * input by users.
     */
    public static String getLastPostalCode(Context context) {
        return TunerPreferences.getLastPostalCode(context);
    }

    /**
     * Sets the last stored postal or zip code. This method will overwrite the value written by
     * calling {@link #updatePostalCode(Context)}.
     */
    public static void setLastPostalCode(Context context, String postalCode) {
        Log.i(TAG, "Set Postal Code:" + postalCode);
        TunerPreferences.setLastPostalCode(context, postalCode);
    }

    @Nullable
    private static String getPostalCode(Context context) throws IOException, SecurityException {
        Address address = LocationUtils.getCurrentAddress(context);
        if (address != null) {
            Log.i(TAG, "Current country and postal code is " + address.getCountryName() + ", "
                    + address.getPostalCode());
            if (TextUtils.equals(address.getCountryCode(), SUPPORTED_COUNTRY_CODE)) {
                return address.getPostalCode();
            }
        }
        return null;
    }

    /** An {@link java.lang.Exception} class to notify no valid postal or zip code is available. */
    public static class NoPostalCodeException extends Exception {
        public NoPostalCodeException() {
        }
    }
}
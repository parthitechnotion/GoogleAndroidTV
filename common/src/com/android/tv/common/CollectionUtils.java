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

package com.android.tv.common;

import android.os.Build;
import android.util.ArrayMap;
import android.util.ArraySet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Static utilities for collections
 */
public class CollectionUtils {
    /**
     * Returns a new Set suitable for small data sets.
     *
     * <p>In M and above this is a {@link ArraySet} otherwise it is a  {@link HashSet}.
     */
    public static <T> Set<T> createSmallSet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ArraySet<>();
        } else {
            return new HashSet<>();
        }
    }

    /**
     * Returns a new Map suitable for small data sets.
     *
     * <p>In M and above this is a {@link ArrayMap} otherwise it is a {@link HashMap}.
     */
    public static <K, V> Map<K, V> createSmallMap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ArrayMap<>();
        } else {
            return new HashMap<>();
        }
    }

    /**
     * Returns an array with the arrays concatenated together.
     *
     * @see <a href="http://stackoverflow.com/a/784842/1122089">Stackoverflow answer</a> by
     *      <a href="http://stackoverflow.com/users/40342/joachim-sauer">Joachim Sauer</a>
     */
    public static <T> T[] concatAll(T[] first, T[]... rest) {
        int totalLength = first.length;
        for (T[] array : rest) {
            totalLength += array.length;
        }
        T[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (T[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}

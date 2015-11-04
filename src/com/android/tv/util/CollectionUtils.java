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

package com.android.tv.util;

import android.os.Build;
import android.util.ArraySet;

import java.util.HashSet;
import java.util.Set;

/**
 * Static utilities for collections
 */
public class CollectionUtils {
    /**
     * Returns a new Set suitable for small data sets.
     *
     * <p>In M and above this is a ArraySet otherwise it is a HashSet
     */
    public static <T> Set<T> createSmallSet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ArraySet<T>();
        } else {
            return new HashSet<T>();
        }
    }
}

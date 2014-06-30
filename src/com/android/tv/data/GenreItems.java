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

package com.android.tv.data;

import android.content.Context;
import android.media.tv.TvContract.Programs.Genres;

import com.android.tv.R;

public class GenreItems {
    public static final int POSITION_ALL_CHANNELS = 0;

    private static final String[] CANONICAL_GENRES = {
        null, // All channels
        Genres.FAMILY_KIDS,
        Genres.SPORTS,
        Genres.SHOPPING,
        Genres.MOVIES,
        Genres.COMEDY,
        Genres.TRAVEL,
        Genres.DRAMA,
        Genres.EDUCATION,
        Genres.ANIMAL_WILDLIFE,
        Genres.NEWS,
        Genres.GAMING
    };

    private static String[] sItems;

    private GenreItems() { }

    public static final String[] getItems(Context context) {
        if (sItems == null) {
            sItems = context.getResources().getStringArray(R.array.show_only_label);
        }
        return sItems;
    }

    public static final String getLabel(Context context, int item) {
        return getItems(context)[item];
    }

    public static final String getLabel(Context context, String canonicalGenre) {
        if (canonicalGenre == null) {
            return getItems(context)[POSITION_ALL_CHANNELS];
        }

        for (int i = 1; i < CANONICAL_GENRES.length; ++i) {
            if (CANONICAL_GENRES[i].equals(canonicalGenre)) {
                return getItems(context)[i];
            }
        }
        return getItems(context)[POSITION_ALL_CHANNELS];
    }

    public static final String getCanonicalGenre(int item) {
        return CANONICAL_GENRES[item];
    }

    public static final String getCanonicalGenre(Context context, String item) {
        int index = 0;
        for (String genre : getItems(context)) {
            if (genre.equals(item)) {
                return CANONICAL_GENRES[index];
            }
            ++index;
        }
        return null;
    }

    public static final int getPosition(Context context, String item) {
        int index = 0;
        for (String genre : getItems(context)) {
            if (genre.equals(item)) {
                return index;
            }
            ++index;
        }
        return POSITION_ALL_CHANNELS;
    }

    public static final int getPosition(String canonicalGenre) {
        if (canonicalGenre == null) {
            return POSITION_ALL_CHANNELS;
        }
        for (int i = 1; i < CANONICAL_GENRES.length; ++i) {
            if (CANONICAL_GENRES[i].equals(canonicalGenre)) {
                return i;
            }
        }
        return POSITION_ALL_CHANNELS;
    }
}

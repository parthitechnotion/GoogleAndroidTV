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

package com.android.tv.search;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.TvContract.Channels;
import android.provider.TvContract.Programs;

import java.util.ArrayList;
import java.util.List;

public class TvProviderSearch {
    public static List<SearchResult> search(Context context, String query) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        results.addAll(searchChannels(context, query, new String[] {
                Channels.COLUMN_DISPLAY_NAME,
                Channels.COLUMN_DESCRIPTION
        }));
        results.addAll(searchPrograms(context, query, new String[] {
                Programs.COLUMN_TITLE,
                Programs.COLUMN_DESCRIPTION
        }));
        return results;
    }

    private static List<SearchResult> searchChannels(Context context, String query,
            String[] columnNames) {
        String[] projection = {
                Channels._ID,
                Channels.COLUMN_DISPLAY_NAME,
                Channels.COLUMN_DESCRIPTION,
        };
        return search(context, Channels.CONTENT_URI, projection, query, columnNames);
    }

    // TODO: Consider the case when the searched programs are already ended or the user select a
    //       searched program which doesn't air right now.
    private static List<SearchResult> searchPrograms(Context context, String query,
            String[] columnNames) {
        String[] projection = {
                Programs.COLUMN_CHANNEL_ID,
                Programs.COLUMN_TITLE,
                Programs.COLUMN_DESCRIPTION,
        };
        return search(context, Programs.CONTENT_URI, projection, query, columnNames);
    }

    private static List<SearchResult> search(Context context, Uri uri, String[] projection,
            String query, String[] columnNames) {
        List<SearchResult> results = new ArrayList<SearchResult>();

        StringBuilder sb = new StringBuilder("1=0");
        for (String columnName : columnNames) {
            sb.append(" OR ").append(columnName).append(" like ?");
        }
        String selection = sb.toString();
        String selectionArg = "%" + query + "%";
        String[] selectionArgs = new String[columnNames.length];
        for (int i=0; i<selectionArgs.length; ++i) {
            selectionArgs[i] = selectionArg;
        }

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null) {
                // TODO: Need to add image when available.
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    String title = cursor.getString(1);
                    String description = cursor.getString(2);

                    SearchResult result = SearchResult.builder()
                            .setTitle(title)
                            .setDescription(description)
                            .setIntentAction(Intent.ACTION_VIEW)
                            .setIntentData(ContentUris.withAppendedId(Channels.CONTENT_URI, id)
                                    .toString())
                            .build();
                    results.add(result);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }
}

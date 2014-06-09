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
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.net.Uri;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TvProviderSearch {
    public static List<SearchResult> search(Context context, String query, int limit) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        results.addAll(searchChannels(context, query, new String[] {
                Channels.COLUMN_DISPLAY_NAME,
                Channels.COLUMN_DESCRIPTION
        }, limit));
        if (results.size() >= limit) {
            return results;
        }

        Set<Long> previousResults = getChannelIdSet(results);
        limit -= results.size();
        results.addAll(searchPrograms(context, query, new String[] {
                Programs.COLUMN_TITLE,
                Programs.COLUMN_SHORT_DESCRIPTION
        }, previousResults, limit));
        return results;
    }

    private static Set<Long> getChannelIdSet(List<SearchResult> results) {
        Set<Long> channelIdSet = new HashSet<Long>();
        for (SearchResult sr : results) {
            channelIdSet.add(sr.getChannelId());
        }
        return channelIdSet;
    }

    private static List<SearchResult> searchChannels(Context context, String query,
            String[] columnNames, int limit) {
        Preconditions.checkState(columnNames != null && columnNames.length > 0);

        String[] projection = {
                Channels._ID,
                Channels.COLUMN_DISPLAY_NAME,
                Channels.COLUMN_DESCRIPTION,
        };

        StringBuilder sb = new StringBuilder();
        sb.append(Channels.COLUMN_BROWSABLE).append("=1 AND ");
        sb.append(Channels.COLUMN_SEARCHABLE).append("=1 AND (");
        sb.append(columnNames[0]).append(" like ?");
        for (int i = 1; i < columnNames.length; ++i) {
            sb.append(" OR ").append(columnNames[i]).append(" like ?");
        }
        sb.append(")");
        String selection = sb.toString();

        String selectionArg = "%" + query + "%";
        String[] selectionArgs = new String[columnNames.length];
        for (int i = 0; i < selectionArgs.length; ++i) {
            selectionArgs[i] = selectionArg;
        }

        return search(context, Channels.CONTENT_URI, projection, selection, selectionArgs, limit,
                null);
    }

    private static List<SearchResult> searchPrograms(final Context context, String query,
            String[] columnNames, final Set<Long> previousResults, int limit) {
        Preconditions.checkState(columnNames != null && columnNames.length > 0);

        String[] projection = {
                Programs.COLUMN_CHANNEL_ID,
                Programs.COLUMN_TITLE,
                Programs.COLUMN_SHORT_DESCRIPTION,
        };

        StringBuilder sb = new StringBuilder();
        // Search among the programs which are now being on the air.
        sb.append(Programs.COLUMN_START_TIME_UTC_MILLIS).append("<=? AND ");
        sb.append(Programs.COLUMN_END_TIME_UTC_MILLIS).append(">=? AND (");
        sb.append(columnNames[0]).append(" like ?");
        for (int i = 1; i < columnNames.length; ++i) {
            sb.append(" OR ").append(columnNames[0]).append(" like ?");
        }
        sb.append(")");
        String selection = sb.toString();
        String selectionArg = "%" + query + "%";
        String[] selectionArgs = new String[columnNames.length+2];
        selectionArgs[0] = selectionArgs[1] = String.valueOf(System.currentTimeMillis());
        for (int i = 2; i < selectionArgs.length; ++i) {
            selectionArgs[i] = selectionArg;
        }

        return search(context, Programs.CONTENT_URI, projection, selection, selectionArgs, limit,
                new ResultFilter() {
                    private Map<Long, Boolean> searchableMap = new HashMap<Long, Boolean>();

                    @Override
                    public boolean filter(Cursor c) {
                        long id = c.getLong(0);
                        // Filter out the program whose channel is already searched.
                        if (previousResults.contains(id)) {
                            return false;
                        }
                        // The channel is cached.
                        Boolean isSearchable = searchableMap.get(id);
                        if (isSearchable != null) {
                            return isSearchable;
                        }

                        // Don't know whether the channel is searchable or not.
                        String selection = Channels._ID + "=? AND "
                                + Channels.COLUMN_BROWSABLE + "=1 AND "
                                + Channels.COLUMN_SEARCHABLE + "=1";
                        Cursor cursor = null;
                        try {
                            // Don't need to fetch all the columns.
                            cursor = context.getContentResolver().query(Channels.CONTENT_URI,
                                    new String[] { Channels._ID }, selection,
                                    new String[] { String.valueOf(id) }, null);
                            boolean isSearchableChannel = cursor != null && cursor.getCount() > 0;
                            searchableMap.put(id, isSearchableChannel);
                            return isSearchableChannel;
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
        });
    }

    private static List<SearchResult> search(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, int limit, ResultFilter resultFilter) {
        List<SearchResult> results = new ArrayList<SearchResult>();

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null) {
                // TODO: Need to add image when available.
                int count = 0;
                while (cursor.moveToNext()) {
                    if (resultFilter != null && !resultFilter.filter(cursor)) {
                        continue;
                    }

                    long id = cursor.getLong(0);
                    String title = cursor.getString(1);
                    String description = cursor.getString(2);

                    SearchResult result = SearchResult.builder()
                            .setChannelId(id)
                            .setTitle(title)
                            .setDescription(description)
                            .setIntentAction(Intent.ACTION_VIEW)
                            .setIntentData(ContentUris.withAppendedId(Channels.CONTENT_URI, id)
                                    .toString())
                            .build();
                    results.add(result);

                    if (++count >= limit) {
                        break;
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    private interface ResultFilter {
        boolean filter(Cursor c);
    }
}

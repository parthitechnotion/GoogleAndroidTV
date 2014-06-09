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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class LocalSearchProvider extends ContentProvider {
    private static final String[] SEARCHABLE_COLUMNS = new String[] {
        SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2,
        SearchManager.SUGGEST_COLUMN_ICON_1, SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
        SearchManager.SUGGEST_COLUMN_INTENT_DATA
    };

    private static final String EXPECTED_PATH_PREFIX = "/" + SearchManager.SUGGEST_URI_PATH_QUERY;
    // The launcher passes 10 as a 'limit' parameter by default.
    private static final int DEFAULT_SEARCH_LIMIT = 10;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String query = uri.getLastPathSegment();
        int limit = DEFAULT_SEARCH_LIMIT;
        try {
            limit = Integer.parseInt(uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT));
        } catch (NumberFormatException | UnsupportedOperationException e) {
            // Ignore the exceptions
        }
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (!TextUtils.isEmpty(query)) {
            results.addAll(TvProviderSearch.search(getContext(), query, limit));
        }
        return createSuggestionsCursor(results);
    }

    private Cursor createSuggestionsCursor(List<SearchResult> results) {
        MatrixCursor cursor = new MatrixCursor(SEARCHABLE_COLUMNS, results.size());
        List<String> row = new ArrayList<String>(SEARCHABLE_COLUMNS.length);

        for (SearchResult result : results) {
            row.clear();
            row.add(result.getTitle());
            row.add(result.getDescription());
            row.add(result.getImageUri());
            row.add(result.getIntentAction());
            row.add(result.getIntentData());
            cursor.addRow(row);
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        if (!checkUriCorrect(uri)) return null;
        return SearchManager.SUGGEST_MIME_TYPE;
    }

    private static boolean checkUriCorrect(Uri uri) {
        return uri != null && uri.getPath().startsWith(EXPECTED_PATH_PREFIX);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
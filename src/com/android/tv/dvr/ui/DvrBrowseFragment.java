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

package com.android.tv.dvr.ui;

import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.Recording;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * {@link BrowseFragment} for DVR functions.
 */
public class DvrBrowseFragment extends BrowseFragment {
    private static final String TAG = "DvrBrowseFragment";

    @IntDef({DVR_CURRENT_RECORDINGS, DVR_SCHEDULED_RECORDINGS, DVR_RECORDED_PROGRAMS, DVR_SETTINGS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DVR_HEADERS_MODE {}
    public static final int DVR_CURRENT_RECORDINGS = 0;
    public static final int DVR_SCHEDULED_RECORDINGS = 1;
    public static final int DVR_RECORDED_PROGRAMS = 2;
    public static final int DVR_SETTINGS = 3;

    private static LinkedHashMap<Integer, Integer> sHeaders =
            new LinkedHashMap<Integer, Integer>() {{
        put(DVR_CURRENT_RECORDINGS, R.string.dvr_main_current_recordings);
        put(DVR_SCHEDULED_RECORDINGS, R.string.dvr_main_scheduled_recordings);
        put(DVR_RECORDED_PROGRAMS, R.string.dvr_main_recorded_programs);
        put(DVR_SETTINGS, R.string.dvr_main_settings);
    }};

    private DvrDataManager mDvrDataManager;
    private ArrayObjectAdapter mRowsAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        setupUiElements();
        setupAdapter();
        prepareEntranceTransition();

        // TODO: load asynchronously.
        loadData();
    }

    private void setupUiElements() {
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(false);
    }

    private void setupAdapter() {
        mDvrDataManager = TvApplication.getSingletons(getContext()).getDvrDataManager();
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mRowsAdapter);
    }

    private void loadRow(ArrayObjectAdapter gridRowAdapter, List<Recording> recordings) {
        if (recordings == null || recordings.size() == 0) {
            gridRowAdapter.add(null);
            return;
        }
        for (Recording r : recordings) {
            gridRowAdapter.add(r);
        }
    }

    private void loadData() {
        for (@DVR_HEADERS_MODE int i : sHeaders.keySet()) {
            HeaderItem gridHeader = new HeaderItem(i, getContext().getString(sHeaders.get(i)));
            GridItemPresenter gridPresenter = new GridItemPresenter(this);
            ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(gridPresenter);
            switch (i) {
                case DVR_CURRENT_RECORDINGS:
                    loadRow(gridRowAdapter, mDvrDataManager.getStartedRecordings());
                    break;
                case DVR_SCHEDULED_RECORDINGS:
                    loadRow(gridRowAdapter, mDvrDataManager.getScheduledRecordings());
                    break;
                case DVR_RECORDED_PROGRAMS:
                    loadRow(gridRowAdapter, mDvrDataManager.getFinishedRecordings());
                    break;
                case DVR_SETTINGS:
                    // TODO: provide setup rows.
                    break;
            }
            mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));
        }
        startEntranceTransition();
    }
}

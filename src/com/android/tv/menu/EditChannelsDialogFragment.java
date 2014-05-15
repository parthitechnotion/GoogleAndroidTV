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

package com.android.tv.menu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.TvContract;
import android.text.TextUtils;
import android.tv.TvInputInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.Utils;

public class EditChannelsDialogFragment extends DialogFragment {
    public static final String DIALOG_TAG = EditChannelsDialogFragment.class.getName();

    public static final String ARG_CURRENT_INPUT = "current_input";
    public static final String ARG_IS_UNIFIED_TV_INPUT = "unified_tv_input";

    private static final int BROWSABLE = 1;

    private TvInputInfo mCurrentInput;
    private boolean mIsUnifiedTvInput;
    private SimpleCursorAdapter mAdapter;

    private View mView;
    private ListView mListView;

    private int mIndexDisplayNumber;
    private int mIndexDisplayName;
    private int mIndexBrowsable;

    private int mBrowsableChannelCount;

    private boolean isInitialLoading;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = getArguments();
        assert(arg != null);

        mCurrentInput = arg.getParcelable(ARG_CURRENT_INPUT);
        mIsUnifiedTvInput = arg.getBoolean(ARG_IS_UNIFIED_TV_INPUT);
        String displayName = Utils.getDisplayNameForInput(getActivity(), mCurrentInput,
                mIsUnifiedTvInput);
        String title = String.format(getString(R.string.edit_channels_title), displayName);

        mView = LayoutInflater.from(getActivity()).inflate(R.layout.edit_channels, null);
        initButtons();
        initListView();

        return new AlertDialog.Builder(getActivity())
                .setView(mView)
                .setTitle(title)
                .create();
    }

    private void initButtons() {
        Button button = (Button) mView.findViewById(R.id.button_enable);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateAllChannels(true);
            }
        });

        button = (Button) mView.findViewById(R.id.button_disable);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateAllChannels(false);
            }
        });
    }

    private void initListView() {
        getLoaderManager().initLoader(0, null, new LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                Uri uri;
                if (mIsUnifiedTvInput) {
                    uri = TvContract.Channels.CONTENT_URI;
                } else {
                    uri = TvContract.buildChannelsUriForInput(mCurrentInput.getComponent(), false);
                }
                String[] projections = { TvContract.Channels._ID,
                        TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                        TvContract.Channels.COLUMN_DISPLAY_NAME,
                        TvContract.Channels.COLUMN_BROWSABLE };
                String sortOrder;
                if (mIsUnifiedTvInput) {
                    sortOrder = Utils.CHANNEL_SORT_ORDER_BY_INPUT_NAME + ", "
                            + Utils.CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER;
                } else {
                    sortOrder = Utils.CHANNEL_SORT_ORDER_BY_DISPLAY_NUMBER;
                }

                isInitialLoading = true;
                return new CursorLoader(getActivity(), uri, projections, null, null, sortOrder);
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
                mIndexDisplayNumber = cursor.getColumnIndex(
                        TvContract.Channels.COLUMN_DISPLAY_NUMBER);
                mIndexDisplayName = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
                mIndexBrowsable = cursor.getColumnIndex(TvContract.Channels.COLUMN_BROWSABLE);

                cursor.setNotificationUri(getActivity().getContentResolver(),
                        TvContract.Channels.CONTENT_URI);
                mAdapter.swapCursor(cursor);

                if (isInitialLoading) {
                    isInitialLoading = false;
                    mBrowsableChannelCount = 0;
                    while(cursor.moveToNext()) {
                        if (cursor.getInt(mIndexBrowsable) == BROWSABLE) {
                            ++mBrowsableChannelCount;
                        }
                    }
                    if (mBrowsableChannelCount <= 0) {
                        Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                mAdapter.swapCursor(null);
            }
        });

        // TODO: need to show logo when TvProvider supports logo-related field.
        String[] from = { TvContract.Channels.COLUMN_DISPLAY_NAME };
        int[] to = {R.id.channel_text_view};

        mAdapter = new SimpleCursorAdapter(getActivity(), R.layout.edit_channels_item, null, from,
                to, 0);
        mAdapter.setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == mIndexDisplayName) {
                    String channelNumber = cursor.getString(mIndexDisplayNumber);
                    String channelName = cursor.getString(mIndexDisplayName);
                    String channelString;
                    if (TextUtils.isEmpty(channelName)) {
                        channelString = channelNumber;
                    } else {
                        channelString = String.format(getString(R.string.channel_item),
                                channelNumber, channelName);
                    }
                    CheckedTextView checkedTextView = (CheckedTextView) view;
                    checkedTextView.setText(channelString);
                    checkedTextView.setChecked(cursor.getInt(mIndexBrowsable) == 1);
                }
                return true;
            }
        });

        mListView = (ListView) mView.findViewById(android.R.id.list);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mView.findViewById(R.id.empty));

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CheckedTextView checkedTextView =
                        (CheckedTextView) view.findViewById(R.id.channel_text_view);
                boolean checked = checkedTextView.isChecked();

                Uri uri = TvContract.buildChannelUri(id);
                ContentValues values = new ContentValues();
                values.put(TvContract.Channels.COLUMN_BROWSABLE, checked ? 0 : 1);
                getActivity().getContentResolver().update(uri, values, null, null);

                mBrowsableChannelCount += checked ? 1 : -1;
                if (mBrowsableChannelCount <= 0) {
                    Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateAllChannels(boolean browsable) {
        if (mAdapter == null || mAdapter.getCursor() == null) {
            return;
        }
        Uri uri;
        if (mIsUnifiedTvInput) {
            uri = TvContract.Channels.CONTENT_URI;
        } else {
            uri = TvContract.buildChannelsUriForInput(mCurrentInput.getComponent(), false);
        }
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_BROWSABLE, browsable ? 1 : 0);

        getActivity().getContentResolver().update(uri, values, null, null);

        if (browsable) {
            mBrowsableChannelCount = mAdapter.getCount();
        } else  {
            mBrowsableChannelCount = 0;
            Toast.makeText(getActivity(), R.string.all_the_channels_are_unchecked,
                    Toast.LENGTH_SHORT).show();
        }
    }
}

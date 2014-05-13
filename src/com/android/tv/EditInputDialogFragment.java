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

package com.android.tv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditInputDialogFragment extends DialogFragment implements OnItemSelectedListener {
    private TvInputManager mTvInputManager;
    private SharedPreferences mPreferences;

    // A mapping from the display name of each TV input to its ID (String).
    private final Map<String, String> mInputIdMap = new HashMap<String, String>();

    // A list of display names for all TV inputs.
    private String[] mDisplayNames;

    private ArrayAdapter<String> mAdapter;
    private int mSelected;
    private EditText mEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = getActivity().getSharedPreferences(TvSettings.PREFS_FILE,
                Context.MODE_PRIVATE);
        mAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
                new ArrayList<String>());

        mTvInputManager = (TvInputManager) getActivity().getSystemService(Context.TV_INPUT_SERVICE);
        setupAdapter();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_edit_input, null);
        Spinner dropdown = (Spinner) view.findViewById(R.id.spinner);
        dropdown.setAdapter(mAdapter);
        dropdown.setOnItemSelectedListener(this);
        mEditText = (EditText) view.findViewById(R.id.input_edit_text);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.new_input_device_name)
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mSelected < 0 || mSelected >= mDisplayNames.length) {
                            return;
                        }
                        String oldDisplayName = mDisplayNames[mSelected];
                        String newDisplayName = mEditText.getText().toString().trim();
                        if (oldDisplayName.equals(newDisplayName)) {
                            return;
                        }
                        if (mInputIdMap.get(newDisplayName) != null) {
                            Toast.makeText(getActivity(), R.string.name_already_exists,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String inputId = mInputIdMap.get(oldDisplayName);
                        String key = TvSettings.PREF_DISPLAY_INPUT_NAME + inputId;
                        mPreferences.edit().putString(key, newDisplayName).commit();
                    }
                })
                .setNeutralButton(R.string.reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mSelected < 0 || mSelected >= mDisplayNames.length) {
                            return;
                        }
                        String oldDisplayName = mDisplayNames[mSelected];
                        String inputId = mInputIdMap.get(oldDisplayName);
                        mPreferences.edit().remove(
                                TvSettings.PREF_DISPLAY_INPUT_NAME + inputId).commit();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mSelected = position;
        mEditText.requestFocus();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void setupAdapter() {
        List<TvInputInfo> inputs = mTvInputManager.getTvInputList();
        if (inputs.size() < 1) {
            return;
        }
        for (TvInputInfo input : inputs) {
            String id = input.getId();
            String name = Utils.getDisplayNameForInput(getActivity(), input);
            mInputIdMap.put(name, id);
        }
        mDisplayNames = mInputIdMap.keySet().toArray(new String[0]);
        mAdapter.addAll(mDisplayNames);
        mAdapter.notifyDataSetChanged();
    }
}

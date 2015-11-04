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
 * limitations under the License.
 */

package com.android.tv.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.common.ui.setup.SetupFragment;

/**
 * A fragment for the onboarding screen.
 */
public class WelcomePageFragment extends SetupFragment {
    public static final String KEY_TITLE = "key_title";
    public static final String KEY_DESCRIPTION = "key_description";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();
        ((TextView) view.findViewById(R.id.title)).setText(args.getString(KEY_TITLE));
        ((TextView) view.findViewById(R.id.description)).setText(args.getString(KEY_DESCRIPTION));
        return view;
     }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_welcome_page;
    }
}

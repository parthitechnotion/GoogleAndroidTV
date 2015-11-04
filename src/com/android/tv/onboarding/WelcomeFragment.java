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

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.tv.R;
import com.android.tv.common.ui.setup.SetupFragment;

/**
 * A fragment for the onboarding screen.
 */
public class WelcomeFragment extends SetupFragment {
    public static final int ACTION_NEXT = 1;

    private int mNumPages;
    private String[] mPageTitles;
    private String[] mPageDescriptions;
    private int mCurrentPageIndex;

    private PagingIndicator mPageIndicator;
    private Button mButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mPageTitles = getResources().getStringArray(R.array.welcome_page_titles);
        mPageDescriptions = getResources().getStringArray(R.array.welcome_page_descriptions);
        mNumPages = mPageTitles.length;
        mCurrentPageIndex = 0;
        mPageIndicator = (PagingIndicator) view.findViewById(R.id.page_indicator);
        mPageIndicator.setPageCount(mNumPages);
        mButton = (Button) view.findViewById(R.id.button);
        mButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCurrentPageIndex == mNumPages - 1) {
                    onActionClick(ACTION_NEXT);
                } else {
                    showPage(++mCurrentPageIndex);
                }
            }
        });
        showPage(mCurrentPageIndex);
        return view;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_welcome;
    }

    /*
     * Should return {@link SetupFragment} for the custom animations.
     */
    private SetupFragment getPage(int index) {
        Bundle args = new Bundle();
        args.putString(WelcomePageFragment.KEY_TITLE, mPageTitles[index]);
        args.putString(WelcomePageFragment.KEY_DESCRIPTION, mPageDescriptions[index]);
        SetupFragment fragment = new WelcomePageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private void showPage(int pageIndex) {
        SetupFragment fragment = getPage(pageIndex);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (pageIndex != 0) {
            ft.setCustomAnimations(SetupFragment.ANIM_ENTER,
                    SetupFragment.ANIM_EXIT);
        }
        ft.replace(R.id.page_container, fragment).commit();
        if (pageIndex == mNumPages - 1) {
            mButton.setText(R.string.welcome_start_button_text);
        } else {
            mButton.setText(R.string.welcome_next_button_text);
        }
        mPageIndicator.onPageSelected(pageIndex, pageIndex != 0);
    }
}

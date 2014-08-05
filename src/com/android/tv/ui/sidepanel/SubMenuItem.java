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

package com.android.tv.ui.sidepanel;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.view.View;
import android.widget.TextView;

import com.android.tv.R;

import java.util.List;

public class SubMenuItem extends Item {
    private final String mTitle;
    private final FragmentManager mFragmentManager;
    private TextView mTitleView;

    public SubMenuItem(String title, FragmentManager fragmentManager) {
        mTitle = title;
        mFragmentManager = fragmentManager;
    }

    @Override
    protected int getResourceId() {
        return R.layout.option_item_sub_menu;
    }

    @Override
    protected void bind(View view) {
        mTitleView = (TextView) view.findViewById(R.id.title);
        mTitleView.setText(mTitle);
    }

    @Override
    protected void unbind() {
        mTitleView = null;
    }

    @Override
    protected void onSelected() {
        mFragmentManager
            .beginTransaction()
            .add(R.id.right_panel, new SideFragment() {
                @Override
                protected String getTitle() {
                    return SubMenuItem.this.getTitle();
                }

                @Override
                protected List<Item> getItemList() {
                    return SubMenuItem.this.getItemList();
                }
            })
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .addToBackStack(null)
            .commit();
    }

    protected String getTitle() {
        return mTitle;
    }

    protected List<Item> getItemList() {
        return null;
    }
}
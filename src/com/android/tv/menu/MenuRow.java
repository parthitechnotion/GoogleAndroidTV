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

package com.android.tv.menu;

import android.content.Context;

import com.android.tv.MainActivity;

/**
 * A base class of the item which will be displayed in the main menu.
 * It contains the data such as title to represent a row.
 * This is an abstract class and the sub-class could have it's own data for
 * the row.
 */
public abstract class MenuRow {
    private final Context mContext;
    private final String mTitle;
    private final int mHeight;

    // TODO: Check if the heightResId is really necessary.
    public MenuRow(Context context, int titleResId, int heightResId) {
        this(context, context.getString(titleResId), heightResId);
    }

    public MenuRow(Context context, String title, int heightResId) {
        mContext = context;
        mTitle = title;
        mHeight = context.getResources().getDimensionPixelSize(heightResId);
    }

    /**
     * Returns the context.
     */
    protected Context getContext() {
        return mContext;
    }

    protected MainActivity getMainActivity() {
        return (MainActivity) mContext;
    }

    /**
     * Returns the title of this row.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Returns the height of this row.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Updates the contents in this row.
     * This method is called only by the menu when necessary.
     */
    abstract public void update();

    /**
     * Indicates whether this row is shown in the menu.
     */
    public boolean isVisible() {
        return true;
    }

    /**
     * Releases all the resources which need to be released.
     * This method is called when the main menu is not available any more.
     */
    public void release() {
    }

    /**
     * Returns the ID of the layout resource for this row.
     */
    abstract public int getLayoutResId();

    /**
     * Returns the ID of this row. This ID is used to select the row in the main menu.
     */
    abstract public String getId();
}

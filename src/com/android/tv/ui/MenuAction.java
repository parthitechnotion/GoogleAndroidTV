// Copyright 2014 Google Inc. All Rights Reserved.

package com.android.tv.ui;

import android.content.Context;

import com.android.tv.R;

/**
 * A class to define possible actions from main menu.
 */
public class MenuAction {
    public static final int SELECT_TV_INPUT_TYPE = 0;
    public static final int EDIT_CHANNEL_LIST_TYPE = 1;
    public static final int AUTO_SCAN_CHANNELS_TYPE = 2;
    public static final int PRIVACY_SETTING_TYPE = 3;
    public static final int TOGGLE_PIP_TYPE = 4;
    public static final int MORE_TYPE = 5;

    public static final MenuAction SELECT_TV_INPUT_ACTION =
            new MenuAction(R.string.menu_select_input, SELECT_TV_INPUT_TYPE);
    public static final MenuAction EDIT_CHANNEL_LIST_ACTION =
            new MenuAction(R.string.menu_edit_channels, EDIT_CHANNEL_LIST_TYPE);
    public static final MenuAction AUTO_SCAN_CHANNELS_ACTION =
            new MenuAction(R.string.menu_auto_scan, AUTO_SCAN_CHANNELS_TYPE);
    public static final MenuAction PRIVACY_SETTING_ACTION =
            new MenuAction(R.string.menu_privacy_setting, PRIVACY_SETTING_TYPE);
    public static final MenuAction TOGGLE_PIP_ACTION =
            new MenuAction(R.string.menu_toggle_pip, TOGGLE_PIP_TYPE);
    public static final MenuAction MORE_ACTION =
            new MenuAction(R.string.source_specific_setting, MORE_TYPE);

    private int mActionNameResId;
    private int mType;

    public MenuAction(int actionNameResId, int type) {
        mActionNameResId = actionNameResId;
        mType = type;
    }

    public String getActionName(Context context) {
        return context.getString(mActionNameResId);
    }

    public int getType() {
        return mType;
    }
}
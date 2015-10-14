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

package com.android.tv.tests.ui;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertHas;
import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;

import com.android.tv.R;
import com.android.tv.testing.testinput.ChannelStateData;
import com.android.tv.testing.testinput.TvTestInputConstants;
import com.android.tv.testing.uihelper.Constants;
import com.android.tv.testing.uihelper.DialogHelper;
import com.android.tv.testing.uihelper.SidePanelHelper;

/**
 * Basic tests for the LiveChannels app.
 */
@LargeTest
@Suppress  // http://b/25147411 Tests fail missing classes from tests/common
public class LiveChannelsAppTest extends LiveChannelsTestCase {
    private SidePanelHelper mSidePanelHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSidePanelHelper = new SidePanelHelper(mDevice, mTargetResources);
        mLiveChannelsHelper.assertAppStarted();
        pressKeysForChannel(TvTestInputConstants.CH_1_DEFAULT_DONT_MODIFY);
        getInstrumentation().waitForIdleSync();
    }

    public void testChannelSourcesCancel() {
        mMenuHelper.assertPressOptionsChannelSources();
        BySelector byChannelSourcesSidePanel = mSidePanelHelper
                .bySidePanelTitled(R.string.channel_source_item_customize_channels);
        assertWaitForCondition(mDevice, Until.hasObject(byChannelSourcesSidePanel));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(byChannelSourcesSidePanel));
        assertHas(mDevice, Constants.MENU, false);
    }

    public void testClosedCaptionsCancel() {
        mMenuHelper.assertPressOptionsClosedCaptions();
        BySelector byClosedCaptionSidePanel = mSidePanelHelper
                .bySidePanelTitled(R.string.side_panel_title_closed_caption);
        assertWaitForCondition(mDevice, Until.hasObject(byClosedCaptionSidePanel));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(byClosedCaptionSidePanel));
        assertHas(mDevice, Constants.MENU, false);
    }

    public void testDisplayModeCancel() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.add(com.android.tv.testing.Constants.SVGA_VIDEO_TRACK);
        data.mSelectedVideoTrackId = com.android.tv.testing.Constants.SVGA_VIDEO_TRACK
                .getId();
        updateThenTune(data, TvTestInputConstants.CH_2);

        mMenuHelper.assertPressOptionsDisplayMode();
        BySelector byDisplayModeSidePanel = mSidePanelHelper
                .bySidePanelTitled(R.string.side_panel_title_display_mode);
        assertWaitForCondition(mDevice, Until.hasObject(byDisplayModeSidePanel));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(byDisplayModeSidePanel));
        assertHas(mDevice, Constants.MENU, false);
    }

    public void testMenu() {
        mDevice.pressMenu();

        assertWaitForCondition(mDevice, Until.hasObject(Constants.MENU));
        assertHas(mDevice, mMenuHelper.getByChannels(), true);
    }

    public void testMultiAudioCancel() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.add(com.android.tv.testing.Constants.GENERIC_AUDIO_TRACK);
        updateThenTune(data, TvTestInputConstants.CH_2);

        mMenuHelper.assertPressOptionsMultiAudio();
        BySelector byMultiAudioSidePanel = mSidePanelHelper
                .bySidePanelTitled(R.string.side_panel_title_multi_audio);
        assertWaitForCondition(mDevice, Until.hasObject(byMultiAudioSidePanel));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(byMultiAudioSidePanel));
        assertHas(mDevice, Constants.MENU, false);
    }

    public void testPinCancel() {
        mMenuHelper.showMenu();
        mMenuHelper.assertPressOptionsParentalControls();
        DialogHelper dialogHelper = new DialogHelper(mDevice, mTargetResources);
        dialogHelper.assertWaitForPinDialogOpen();
        mDevice.pressBack();
        dialogHelper.assertWaitForPinDialogClose();
        assertHas(mDevice, Constants.MENU, false);
    }
}

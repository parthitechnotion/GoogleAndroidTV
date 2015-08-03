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
package com.android.tv.tests.jank;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.content.res.Resources;
import android.os.SystemClock;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.jank.WindowContentFrameStatsMonitor;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.common.TvCommonConstants;
import com.android.tv.testing.uihelper.ByResource;
import com.android.tv.testing.uihelper.Constants;
import com.android.tv.testing.uihelper.LiveChannelsUiDeviceHelper;
import com.android.tv.testing.uihelper.MenuHelper;
import com.android.tv.testing.uihelper.UiDeviceUtils;

/**
 * Jank tests for the program guide.
 */
@MediumTest
public class ProgramGuideJankTest extends JankTestBase {
    private static final boolean DEBUG = false;
    private static final String TAG = "ProgramGuideJank";

    private static final String STARTING_CHANNEL = "13";
    private static final int EXPECTED_FRAMES = 5;

    protected UiDevice mDevice;

    protected Resources mTargetResources;
    protected MenuHelper mMenuHelper;
    protected LiveChannelsUiDeviceHelper mLiveChannelsHelper;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mTargetResources = getInstrumentation().getTargetContext().getResources();
        mMenuHelper = new MenuHelper(mDevice, mTargetResources);
        mLiveChannelsHelper = new LiveChannelsUiDeviceHelper(mDevice, mTargetResources,
                getInstrumentation().getContext());
        mLiveChannelsHelper.assertAppStarted();
        pressKeysForChannelNumber(STARTING_CHANNEL);
    }

    @JankTest(expectedFrames = 7,
            beforeTest = "warmProgramGuide",
            beforeLoop = "selectProgramGuideMenuItem",
            afterLoop = "clearProgramGuide")
    @WindowContentFrameStatsMonitor
    public void testShowProgramGuide() {
        mDevice.pressDPadCenter();

        // Full show has two animations.
        long delay = mTargetResources.getInteger(R.integer.program_guide_anim_duration) * 2;
        waitForIdleAtLeast(delay);
    }

    @JankTest(expectedFrames = EXPECTED_FRAMES,
            beforeLoop = "showProgramGuide")
    @WindowContentFrameStatsMonitor
    public void testClearProgramGuide() {
        mDevice.pressBack();
        // Full show has two animations.
        waitForIdleAtLeast(mTargetResources.getInteger(R.integer.program_guide_anim_duration) * 2);
    }

    @JankTest(expectedFrames = EXPECTED_FRAMES,
            beforeLoop = "showProgramGuide",
            afterLoop = "clearProgramGuide")
    @WindowContentFrameStatsMonitor
    public void testScrollDown() {
        mDevice.pressDPadDown();
        waitForIdleAtLeast(mTargetResources
                .getInteger(R.integer.program_guide_table_detail_toggle_anim_duration));
    }

    @JankTest(expectedFrames = EXPECTED_FRAMES,
            beforeLoop = "showProgramGuide",
            afterLoop = "clearProgramGuide")
    @WindowContentFrameStatsMonitor
    public void testScrollRight() {
        mDevice.pressDPadRight();
        waitForIdleAtLeast(mTargetResources
                .getInteger(R.integer.program_guide_table_detail_toggle_anim_duration));
    }

    /**
     * {@link UiDevice#waitForIdle() Wait for idle} , then sleep if needed, then wait for idle
     * again.
     *
     * @param delayInMillis The minimum amount of time to delay.  This is usually the expected
     *                      duration of the animation.
     */
    private void waitForIdleAtLeast(long delayInMillis) {

        // This seems to give the most reliable numbers.
        // The first wait until idle usually returned in 1ms.
        //  Sometimes it would take the whole duration. If we sleep after that we get bad fps
        // because nothing is happening after the idle ends.
        //
        // So sleeping only for the remaining about ensure there is at least enough time for the
        // animation to complete. If we sleep then wait for idle again.  This will usually allow
        // the animation to complete.

        long startTime = SystemClock.uptimeMillis();
        mDevice.waitForIdle();

        long idle = SystemClock.uptimeMillis() - startTime;
        if (DEBUG) {
            Log.d(TAG, "Waited for idle " + (idle) / 1000.0 + " sec");
        }
        if (idle < delayInMillis) {
            long more = delayInMillis - idle;
            SystemClock.sleep(more);
            Log.d(TAG, "Slept " + (more) / 1000.0 + " sec");
            mDevice.waitForIdle();
        }
        if (DEBUG) {
            Log.d(TAG, "Total wait " + (SystemClock.uptimeMillis() - startTime) / 1000.0 + " sec");
        }
    }

    //TODO: move to a mixin/helper
    protected void pressKeysForChannelNumber(String channel) {
        UiDeviceUtils.pressKeys(mDevice, channel);
        mDevice.pressDPadCenter();
    }

    public void selectProgramGuideMenuItem() {
        mMenuHelper.showMenu();
        int rowTitleResId = TvCommonConstants.IS_MNC_OR_HIGHER ? R.string.menu_title_channels
                : R.string.menu_title_channels_legacy;
        mMenuHelper.assertNavigateToMenuItem(rowTitleResId, R.string.channels_item_program_guide);
        mDevice.waitForIdle();
    }

    public void warmProgramGuide() {
        // TODO: b/21078199  First time Program Guide is opened there is a noticeable delay
        selectProgramGuideMenuItem();
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.PROGRAM_GUIDE));
        mDevice.pressBack();

    }

    public void clearProgramGuide() {
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(Constants.PROGRAM_GUIDE));
    }

    public void showProgramGuide() {
        selectProgramGuideMenuItem();
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.PROGRAM_GUIDE));
        // If the side panel grid is visible (and thus has focus), move right to clear it.
        if (mDevice.hasObject(
                ByResource.id(mTargetResources, R.id.program_guide_side_panel_grid_view))) {
            mDevice.pressDPadRight();
        }
    }
}

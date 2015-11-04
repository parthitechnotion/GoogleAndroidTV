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
package com.android.tv.tests.ui;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.tv.Features;
import com.android.tv.R;
import com.android.tv.testing.uihelper.ByResource;
import com.android.tv.testing.uihelper.SidePanelHelper;

/**
 * Test for the About menu.
 */
@LargeTest
public class AboutFragmentTest extends LiveChannelsTestCase {
    private SidePanelHelper mSidePanelHelper;
    private BySelector mAboutSidePanel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSidePanelHelper = new SidePanelHelper(mDevice, mTargetResources);
        mAboutSidePanel = mSidePanelHelper.bySidePanelTitled(R.string.side_panel_title_about);
    }

    public void testAllowAnalytics_present() {
        mLiveChannelsHelper.assertAppStarted();
        mMenuHelper.assertPressOptionsAbout();
        assertWaitForCondition(mDevice, Until.hasObject(mAboutSidePanel));
        BySelector improveSelector = ByResource
                .text(mTargetResources, R.string.about_menu_improve_summary);
        if (Features.ANALYTICS_OPT_OUT.isEnabled(getInstrumentation().getContext())) {
            assertWaitForCondition(mDevice, Until.hasObject(improveSelector));
        } else {
            assertFalse(mDevice.hasObject(improveSelector));
        }
        mDevice.pressBack();
    }
}

package com.android.tv.testing.uihelper;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import junit.framework.Assert;

/**
 * Helper for testing the Live Channels Application.
 */
public class LiveChannelsUiDeviceHelper extends BaseUiDeviceHelper {
    private static final int APPLICATION_START_TIMEOUT_MSEC = 500;

    private final Context mContext;

    public LiveChannelsUiDeviceHelper(UiDevice uiDevice, Resources targetResources,
            Context context) {
        super(uiDevice, targetResources);
        mContext = context;
    }

    public void assertAppStarted() {
        Intent intent = mContext.getPackageManager()
                .getLaunchIntentForPackage(Constants.TV_APP_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        mContext.startActivity(intent);
        mUiDevice.waitForIdle();

        Assert.assertTrue(Constants.TV_APP_PACKAGE + " did not start", mUiDevice
                .wait(Until.hasObject(By.pkg(Constants.TV_APP_PACKAGE).depth(0)),
                        APPLICATION_START_TIMEOUT_MSEC));

        BySelector welcome = ByResource.id(mTargetResources, com.android.tv.R.id.intro);
        if (mUiDevice.hasObject(welcome)) {
            mUiDevice.pressBack();
        }
    }
}

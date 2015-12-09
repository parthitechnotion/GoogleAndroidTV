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

package com.android.usbtuner;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v4.app.NotificationCompat;

import com.android.tv.common.TvCommonConstants;
import com.android.tv.common.TvCommonUtils;
import com.android.usbtuner.tvinput.UsbTunerTvInputService;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * An activity that serves USB tuner setup process.
 */
public class TunerSetupActivity extends Activity {
    public static final String EXTRA_FOR_CHANNEL_SCAN_FILE = "scan_file_choice";
    public static final String EXTRA_FOR_SCANNED_RESULT = "scanned_count";

    // For the recommendation card
    private static final String TV_ACTIVITY_CLASS_NAME = "com.android.tv.TvActivity";
    private static final String NOTIFY_TAG = "UsbTunerSetup";
    private static final int NOTIFY_ID = 1000;
    private static final String TAG_DRAWABLE = "drawable";
    private static final String TAG_ICON = "ic_launcher_s";

    private static final int ID_SCAN = 1;

    // an arbitrary large number in order not to duplicate another action id
    private static final int ID_FINISH_ACTION = 1000;

    private static final int CHANNEL_MAP_SCAN_FILE[] = {
            R.raw.ut_us_atsc_center_frequencies_8vsb,
            R.raw.ut_us_cable_standard_center_frequencies_qam256,
            R.raw.ut_us_all,
            R.raw.ut_kr_atsc_center_frequencies_8vsb,
            R.raw.ut_kr_cable_standard_center_frequencies_qam256,
            R.raw.ut_kr_all,
            R.raw.ut_kr_dev_cj_cable_center_frequencies_qam256};
    private static final int SETUP_BREADCRUMB = R.string.ut_setup_breadcrumb;
    private static final int[] SETUP_TITLE =
            {R.string.ut_setup_new_title, R.string.ut_setup_again_title};
    private static final int[] SETUP_DESCRIPTION =
            {R.string.ut_setup_new_description, R.string.ut_setup_again_description};
    private static final int[] SETUP_CHOICES =
            {R.array.ut_setup_new_choices, R.array.ut_setup_again_choices};
    private static final int SETUP_FINISH_POSITION = 1;
    private static final int CONNECTION_TITLE = R.string.ut_connection_title;
    private static final int CONNECTION_DESCRIPTION = R.string.ut_connection_description;
    private static final int CONNECTION_CHOICES = R.array.ut_connection_choices;
    private static final int[] SCAN_RESULT_TITLE =
            {R.plurals.ut_result_found_title, R.string.ut_result_not_found_title};
    private static final int[] SCAN_RESULT_DESCRIPTION =
            {R.plurals.ut_result_found_description, R.string.ut_result_not_found_description};
    private static final int[] SCAN_RESULT_CHOICES =
            {R.array.ut_result_found_choices, R.array.ut_result_not_found_choices};
    private static final int[] SCAN_RESULT_RESCAN_POSITION = {1, 0};


    private int mChannelCountOnPreference;
    private int mChannelScanned;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mChannelCountOnPreference = UsbTunerPreferences
                .getScannedChannelCount(getApplicationContext());
        SetupGuidedStepFragment
                .addSetupFragment(getFragmentManager(), new SetupStepFragment(), true);
    }

    /**
     * Finishes the setup activity.
     * <p>
     * If scanning is done, it starts TV app after finishing.
     *
     * @param isScanningDone tells whether scanning is done
     */
    public final void finishSetupStep(boolean isScanningDone) {
        UsbTunerPreferences
                .setScannedChannelCount(getApplicationContext(), mChannelScanned);
        if (!isScanningDone) {
            setResult(Activity.RESULT_CANCELED);
        }
        finish();
    }

    private int getSetupIndex() {
        return (mChannelCountOnPreference == 0 && mChannelScanned == 0) ? 0 : 1;
    }

    private class SetupStepFragment extends SetupGuidedStepFragment {
        private int mIndex;

        public SetupStepFragment() {
        }

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            mIndex = getSetupIndex();
            String title = getString(SETUP_TITLE[mIndex]);
            String description = getString(SETUP_DESCRIPTION[mIndex]);
            return new Guidance(title, description, null, null);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            updateActions(actions);
        }

        private void updateActions(List<GuidedAction> actions) {
            updateGuidanceTitle(getString(SETUP_TITLE[mIndex]));
            updateGuidanceDescription(getString(SETUP_DESCRIPTION[mIndex]));
            String[] choices = getResources().getStringArray(SETUP_CHOICES[mIndex]);
            for (int i = 0; i < choices.length; ++i) {
                if (i == SETUP_FINISH_POSITION) {
                    addDefaultAction(actions, ID_FINISH_ACTION, choices[i]);
                } else {
                    addDefaultAction(actions, i, choices[i]);
                }
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final int actionId = (int) action.getId();

            doExitAnimation(new Runnable() {
                @Override
                public void run() {
                    switch (actionId) {
                        case ID_FINISH_ACTION:
                            // Finishing without scanning
                            finishSetupStep(false);
                            break;
                        default:
                            SetupGuidedStepFragment.addSetupFragment(getFragmentManager(),
                                    new ScanStepFragment(), false);
                            break;
                    }
                }
            });
        }

        @Override
        public void onStart() {
            super.onStart();
            int index = getSetupIndex();
            if (index != mIndex) {
                mIndex = index;
                List<GuidedAction> actions = new ArrayList<>();
                updateActions(actions);
                setActions(actions);
            }
            getGuidedActionsStylist().getActionsGridView().setSelectedPosition(0);
            setStartAnimation();
        }
    }

    private class ScanStepFragment extends SetupGuidedStepFragment {
        private final static int INDEX_RESULT_HAS_CHANNELS = 0;
        private final static int INDEX_RESULT_NO_CHANNELS = 1;
        private int mScanChoice = -1;

        public ScanStepFragment() {
        }

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(CONNECTION_TITLE);
            String breadcrumb = getString(SETUP_BREADCRUMB);
            String description = getString(CONNECTION_DESCRIPTION);
            return new Guidance(title, description, breadcrumb, null);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            updateActions(actions);
        }

        private int getResultIndex() {
            return mChannelScanned != 0 ? INDEX_RESULT_HAS_CHANNELS : INDEX_RESULT_NO_CHANNELS;
        }

        private String getResultTitle(int index, int result) {
            if (index == INDEX_RESULT_HAS_CHANNELS) {
                // handle plurality.
                return getResources().getQuantityString(SCAN_RESULT_TITLE[index], result, result);
            }
            return getString(SCAN_RESULT_TITLE[index]);
        }

        private String getResultDescription(int index, int result) {
            if (index == INDEX_RESULT_HAS_CHANNELS) {
                // handle plurality.
                return getResources()
                        .getQuantityString(SCAN_RESULT_DESCRIPTION[index], result, result);
            }
            return getString(SCAN_RESULT_DESCRIPTION[index]);
        }

        private void updateActions(List<GuidedAction> actions) {
            if (mScanChoice < 0) {
                String[] choices = getResources().getStringArray(CONNECTION_CHOICES);
                int length = choices.length - 1;
                int startOffset = 0;
                for (int i = 0; i < length; ++i) {
                    addIntentAction(actions, startOffset + i, ScanActivity.class, choices[i]);
                }
            } else {
                // Render channel scan result UI.
                int index = getResultIndex();
                updateGuidanceTitle(getResultTitle(index, mChannelScanned));
                updateGuidanceDescription(getResultDescription(index, mChannelScanned));

                String[] choices = getResources().getStringArray(SCAN_RESULT_CHOICES[index]);
                for (int i = 0; i < choices.length; ++i) {
                    if (SCAN_RESULT_RESCAN_POSITION[index] == i) {
                        addIntentAction(actions, mScanChoice, ScanActivity.class, choices[i]);
                    } else {
                        addDefaultAction(actions, ID_FINISH_ACTION, choices[i]);
                    }
                }
                setSelectedActionPosition(0);
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final int choice = (int) action.getId();
            final Intent intent = action.getIntent();

            doExitAnimation(new Runnable() {
                @Override
                public void run() {
                    if (mScanChoice >= 0 && choice == ID_FINISH_ACTION) {
                        // Finishing after scanning
                        finishSetupStep(true);
                    } else {
                        intent.putExtra(TvInputInfo.EXTRA_INPUT_ID,
                                getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID));
                        intent.putExtra(EXTRA_FOR_CHANNEL_SCAN_FILE, CHANNEL_MAP_SCAN_FILE[choice]);
                        mScanChoice = choice;
                        startActivityForResult(intent, (int) ID_SCAN);
                    }
                }
            });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == ID_SCAN) {
                Activity activity = getActivity();
                activity.setResult(resultCode);
                mChannelScanned = data.getIntExtra(EXTRA_FOR_SCANNED_RESULT, 0);

                // Cancel a previously shown recommendation card.
                cancelRecommendationCard(getApplicationContext());

                // Mark scan as done
                UsbTunerPreferences.setScanDone(getApplicationContext());

                // finishing will be done manually.
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void onStart() {
            super.onStart();
            List<GuidedAction> actions = new ArrayList<>();
            updateActions(actions);
            setActions(actions);
            setStartAnimation();
        }
    }

    /**
     * A callback to be invoked when the TvInputService is enabled or disabled.
     *
     * @param context a {@link Context} instance
     * @param enabled {@code true} for the {@link UsbTunerTvInputService} to be enabled;
     *                otherwise {@code false}
     */
    public static void onTvInputEnabled(Context context, boolean enabled) {
        // Send a recommendation card for USB channel tuner setup
        // if there's no channels and the USB tuner TV input setup has been not done.
        boolean channelScanDoneOnPreference = UsbTunerPreferences.isScanDone(context);
        int channelCountOnPreference = UsbTunerPreferences.getScannedChannelCount(context);
        if (enabled && !channelScanDoneOnPreference && channelCountOnPreference == 0) {
            UsbTunerPreferences.setShouldShowSetupActivity(context, true);
            sendRecommendationCard(context);
        } else {
            UsbTunerPreferences.setShouldShowSetupActivity(context, false);
            cancelRecommendationCard(context);
        }
    }

    /**
     * Returns a {@link Intent} to launch the USB tuner TV input service.
     *
     * @param context a {@link Context} instance
     */
    public static Intent createSetupActivity(Context context) {
        String inputId = TvContract.buildInputId(new ComponentName(context.getPackageName(),
                UsbTunerTvInputService.class.getName()));

        // Make an intent to launch the setup activity of USB tuner TV input.
        Intent intent = TvCommonUtils.createSetupIntent(
                new Intent(context, TunerSetupActivity.class), inputId);
        intent.putExtra(TvCommonConstants.EXTRA_INPUT_ID, inputId);
        Intent tvActivityIntent = new Intent();
        tvActivityIntent.setComponent(new ComponentName(context, TV_ACTIVITY_CLASS_NAME));
        intent.putExtra(TvCommonConstants.EXTRA_ACTIVITY_AFTER_COMPLETION, tvActivityIntent);
        return intent;
    }

    /**
     * Returns a {@link PendingIntent} to launch the USB tuner TV input service.
     *
     * @param context a {@link Context} instance
     */
    private static PendingIntent createPendingIntentForSetupActivity(Context context) {
        return PendingIntent.getActivity(context, 0, createSetupActivity(context),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Sends the recommendation card to start the USB tuner TV input setup activity.
     *
     * @param context a {@link Context} instance
     */
    private static void sendRecommendationCard(Context context) {
        Resources resources = context.getResources();
        String focusedTitle = resources.getString(
                R.string.ut_setup_recommendation_card_focused_title);
        String title = resources.getString(R.string.ut_setup_recommendation_card_title);
        Bitmap largeIcon = BitmapFactory.decodeResource(resources,
                R.drawable.recommendation_antenna);

        // Build and send the notification.
        Notification notification = new NotificationCompat.BigPictureStyle(
                new NotificationCompat.Builder(context)
                        .setAutoCancel(false)
                        .setContentTitle(focusedTitle)
                        .setContentText(title)
                        .setContentInfo(title)
                        .setCategory(Notification.CATEGORY_RECOMMENDATION)
                        .setLargeIcon(largeIcon)
                        .setSmallIcon(resources.getIdentifier(
                                TAG_ICON, TAG_DRAWABLE, context.getPackageName()))
                        .setContentIntent(createPendingIntentForSetupActivity(context)))
                .build();
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFY_TAG, NOTIFY_ID, notification);
    }

    /**
     * Cancels the previously shown recommendation card.
     *
     * @param context a {@link Context} instance
     */
    private static void cancelRecommendationCard(Context context) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFY_TAG, NOTIFY_ID);
    }
}

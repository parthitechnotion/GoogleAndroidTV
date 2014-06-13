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

package com.android.tv;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.tv.data.DisplayMode;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelMap;
import com.android.tv.data.StreamInfo;
import com.android.tv.dialog.EditInputDialogFragment;
import com.android.tv.dialog.RecentlyWatchedDialogFragment;
import com.android.tv.input.TisTvInput;
import com.android.tv.input.TvInput;
import com.android.tv.input.UnifiedTvInput;
import com.android.tv.notification.NotificationService;
import com.android.tv.ui.DisplayModeOptionFragment;
import com.android.tv.ui.BaseSideFragment;
import com.android.tv.ui.ChannelBannerView;
import com.android.tv.ui.ClosedCaptionOptionFragment;
import com.android.tv.ui.EditChannelsFragment;
import com.android.tv.ui.InputPickerFragment;
import com.android.tv.ui.MainMenuView;
import com.android.tv.ui.SidePanelContainer;
import com.android.tv.ui.SimpleGuideFragment;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.OnTuneListener;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.TvSettings;
import com.android.tv.util.Utils;

import java.util.HashSet;

/**
 * The main activity for demonstrating TV app.
 */
public class TvActivity extends Activity implements AudioManager.OnAudioFocusChangeListener {
    // STOPSHIP: Turn debugging off
    private static final boolean DEBUG = true;
    private static final String TAG = "TvActivity";

    private static final int MSG_START_TV_RETRY = 1;

    private static final int DURATION_SHOW_CHANNEL_BANNER = 8000;
    private static final int DURATION_SHOW_CONTROL_GUIDE = 1000;
    private static final int DURATION_SHOW_MAIN_MENU = 5000;
    private static final int DURATION_SHOW_SIDE_FRAGMENT = 60000;
    private static final float AUDIO_MAX_VOLUME = 1.0f;
    private static final float AUDIO_MIN_VOLUME = 0.0f;
    private static final float AUDIO_DUCKING_VOLUME = 0.3f;
    // Wait for 3 seconds
    private static final int START_TV_MAX_RETRY = 12;
    private static final int START_TV_RETRY_INTERVAL = 250;

    private static final int SIDE_FRAGMENT_TAG_SHOW = 0;
    private static final int SIDE_FRAGMENT_TAG_HIDE = 1;
    private static final int SIDE_FRAGMENT_TAG_RESET = 2;

    // TODO: add more KEYCODEs to the white list.
    private static final int[] KEYCODE_WHITELIST = {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND,
            KeyEvent.KEYCODE_M,
    };
    // TODO: this value should be able to be toggled in menu.
    private static final boolean USE_KEYCODE_BLACKLIST = false;
    private static final int[] KEYCODE_BLACKLIST = {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT
    };
    // STOPSHIP: debug keys are used only for testing.
    private static final boolean USE_DEBUG_KEYS = true;

    private static final int REQUEST_START_SETUP_ACTIIVTY = 0;

    private static final String LEANBACK_SET_SHYNESS_BROADCAST =
            "com.android.mclauncher.action.SET_APP_SHYNESS";
    private static final String LEANBACK_SHY_MODE_EXTRA = "shyMode";

    private static final HashSet<String> AVAILABLE_DIALOG_TAGS = new HashSet<String>();

    private TvInputManager mTvInputManager;
    private TunableTvView mTvView;
    private LinearLayout mControlGuide;
    private MainMenuView mMainMenuView;
    private ChannelBannerView mChannelBanner;
    private SidePanelContainer mSidePanelContainer;
    private HideRunnable mHideChannelBanner;
    private HideRunnable mHideControlGuide;
    private HideRunnable mHideMainMenu;
    private HideRunnable mHideSideFragment;
    private int mShortAnimationDuration;
    private int mDisplayWidth;
    private GestureDetector mGestureDetector;
    private ChannelMap mChannelMap;
    private long mInitChannelId;
    private String mInitTvInputId;

    private TvInput mTvInputForSetup;
    private TvInputManagerHelper mTvInputManagerHelper;
    private AudioManager mAudioManager;
    private int mAudioFocusStatus;
    private boolean mTunePendding;
    private boolean mPipEnabled;
    private long mPipChannelId;
    private boolean mDebugNonFullSizeScreen;
    private boolean mActivityResumed;
    private boolean mUseKeycodeBlacklist = USE_KEYCODE_BLACKLIST;
    private boolean mIsShy = true;

    private boolean mIsClosedCaptionEnabled;
    private int mDisplayMode;
    private SharedPreferences mSharedPreferences;

    static {
        AVAILABLE_DIALOG_TAGS.add(RecentlyWatchedDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(EditInputDialogFragment.DIALOG_TAG);
    }

    // PIP is used for debug/verification of multiple sessions rather than real PIP feature.
    // When PIP is enabled, the same channel as mTvView is tuned.
    private TunableTvView mPipView;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_START_TV_RETRY) {
                Object[] arg = (Object[]) msg.obj;
                TvInput input = (TvInput) arg[0];
                long channelId = (Long) arg[1];
                int retryCount = msg.arg1;
                startTvIfAvailableOrRetry(input, channelId, retryCount);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tv);
        mTvView = (TunableTvView) findViewById(R.id.tv_view);
        mTvView.setOnUnhandledInputEventListener(new TunableTvView.OnUnhandledInputEventListener() {
            @Override
            public boolean onUnhandledInputEvent(InputEvent event) {
                if (event instanceof KeyEvent) {
                    KeyEvent keyEvent = (KeyEvent) event;
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        return onKeyUp(keyEvent.getKeyCode(), keyEvent);
                    }
                } else if (event instanceof MotionEvent) {
                    MotionEvent motionEvent = (MotionEvent) event;
                    if (motionEvent.isTouchEvent()) {
                        return onTouchEvent(motionEvent);
                    }
                }
                return false;
            }
        });
        mPipView = (TunableTvView) findViewById(R.id.pip_view);
        mPipView.setPip(true);

        mControlGuide = (LinearLayout) findViewById(R.id.control_guide);
        mChannelBanner = (ChannelBannerView) findViewById(R.id.channel_banner);
        mMainMenuView = (MainMenuView) findViewById(R.id.main_menu);
        mSidePanelContainer = (SidePanelContainer) findViewById(R.id.right_panel);
        mMainMenuView.setTvActivity(this);

        // Initially hide the channel banner and the control guide.
        mChannelBanner.setVisibility(View.GONE);
        mMainMenuView.setVisibility(View.GONE);
        mControlGuide.setVisibility(View.GONE);
        mSidePanelContainer.setVisibility(View.GONE);
        mSidePanelContainer.setTag(SIDE_FRAGMENT_TAG_RESET);

        mHideControlGuide = new HideRunnable(mControlGuide, DURATION_SHOW_CONTROL_GUIDE);
        mHideChannelBanner = new HideRunnable(mChannelBanner, DURATION_SHOW_CHANNEL_BANNER);
        mHideMainMenu = new HideRunnable(mMainMenuView, DURATION_SHOW_MAIN_MENU,
                new Runnable() {
                    @Override
                    public void run() {
                        if (mPipEnabled) {
                            mPipView.setVisibility(View.INVISIBLE);
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (mPipEnabled && mActivityResumed) {
                            mPipView.setVisibility(View.VISIBLE);
                        }
                    }
                });
        mHideSideFragment = new HideRunnable(mSidePanelContainer, DURATION_SHOW_SIDE_FRAGMENT, null,
                new Runnable() {
                    @Override
                    public void run() {
                        resetSideFragment();
                    }
                });

        mShortAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioFocusStatus = AudioManager.AUDIOFOCUS_LOSS;
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mDisplayWidth = size.x;

        mGestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
            static final float CONTROL_MARGIN = 0.2f;
            final float mLeftMargin = mDisplayWidth * CONTROL_MARGIN;
            final float mRightMargin = mDisplayWidth * (1 - CONTROL_MARGIN);

            @Override
            public boolean onDown(MotionEvent event) {
                if (DEBUG) Log.d(TAG, "onDown: " + event.toString());
                if (mChannelMap == null) {
                    return false;
                }

                mHideControlGuide.showAndHide();

                if (event.getX() <= mLeftMargin) {
                    channelDown();
                    return true;
                } else if (event.getX() >= mRightMargin) {
                    channelUp();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (mChannelMap == null) {
                    showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
                    return true;
                }

                if (event.getX() > mLeftMargin && event.getX() < mRightMargin) {
                    displayMainMenu(true);
                    return true;
                }
                return false;
            }
        });

        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        mTvInputManagerHelper = new TvInputManagerHelper(mTvInputManager);
        mTvInputManagerHelper.start();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        restoreClosedCaptionEnabled();
        restoreDisplayMode();
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Handle the passed key press, if any. Note that only the key codes that are currently
        // handled in the TV app will be handled via Intent.
        // TODO: Consider defining a separate intent filter as passing data of mime type
        // vnd.android.cursor.item/channel isn't really necessary here.
        int keyCode = intent.getIntExtra(Utils.EXTRA_KEYCODE, KeyEvent.KEYCODE_UNKNOWN);
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            if (DEBUG) Log.d(TAG, "Got an intent with keycode: " + keyCode);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            onKeyUp(keyCode, event);
            return;
        }

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // In case the channel is given explicitly, use it.
            mInitChannelId = ContentUris.parseId(intent.getData());
        } else {
            mInitChannelId = Channel.INVALID_ID;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTvInputManagerHelper.update();
        if (mTvInputManagerHelper.getTvInputSize() == 0) {
            Toast.makeText(this, R.string.no_input_device_found, Toast.LENGTH_SHORT).show();
            // TODO: Direct the user to a Play Store landing page for TvInputService apps.
            return;
        }
        boolean tvStarted = false;
        if (mInitTvInputId != null) {
            TvInputInfo inputInfo = mTvInputManagerHelper.getTvInputInfo(mInitTvInputId);
            if (inputInfo != null) {
                startTvIfAvailableOrRetry(new TisTvInput(mTvInputManagerHelper, inputInfo, this),
                        Channel.INVALID_ID, 0);
                tvStarted = true;
            }
        }
        if (!tvStarted) {
            startTv(mInitChannelId);
        }
        mInitChannelId = Channel.INVALID_ID;
        mInitTvInputId = null;
        if (mPipEnabled) {
            if (!mPipView.isPlaying()) {
                startPip();
            } else if (!mPipView.isShown()) {
                mPipView.setVisibility(View.VISIBLE);
            }
        }
        mActivityResumed = true;
    }

    @Override
    protected void onPause() {
        hideOverlays(true, true, true);
        if (mPipEnabled) {
            mPipView.setVisibility(View.INVISIBLE);
        }
        mActivityResumed = false;
        super.onPause();
    }

    private void startTv(long channelId) {
        if (mTvView.isPlaying()) {
            // TV has already started.
            if (channelId == Channel.INVALID_ID) {
                // Simply adjust the volume without tune.
                setVolumeByAudioFocusStatus();
                return;
            }
            Uri channelUri = mChannelMap.getCurrentChannelUri();
            if (channelUri != null && ContentUris.parseId(channelUri) == channelId) {
                // The requested channel is already tuned.
                setVolumeByAudioFocusStatus();
                return;
            }
            stopTv();
        }

        if (channelId == Channel.INVALID_ID) {
            // If any initial channel id is not given, remember the last channel the user watched.
            channelId = Utils.getLastWatchedChannelId(this);
        }
        if (channelId == Channel.INVALID_ID) {
            // If failed to pick a channel, try a different input.
            showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
            return;
        }
        String inputId = Utils.getInputIdForChannel(this, channelId);
        if (TextUtils.isEmpty(inputId)) {
            // If the channel is invalid, try to use the last selected physical tv input.
            inputId = Utils.getLastSelectedPhysInputId(this);
            if (TextUtils.isEmpty(inputId)) {
                // If failed to determine the input for that channel, try a different input.
                showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
                return;
            }
        }
        TvInputInfo inputInfo = mTvInputManagerHelper.getTvInputInfo(inputId);
        if (inputInfo == null) {
            // TODO: if the last selected TV input is uninstalled, getLastWatchedChannelId
            // should return Channel.INVALID_ID.
            Log.w(TAG, "Input (id=" + inputId + ") doesn't exist");
            showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
            return;
        }
        String lastSelectedInputId = Utils.getLastSelectedInputId(this);
        TvInput input;
        if (UnifiedTvInput.ID.equals(lastSelectedInputId)) {
            input = new UnifiedTvInput(mTvInputManagerHelper, this);
        } else {
            input = new TisTvInput(mTvInputManagerHelper, inputInfo, this);
        }
        startTvIfAvailableOrRetry(input, channelId, 0);
    }

    private void startTvIfAvailableOrRetry(TvInput input, long channelId, int retryCount) {
        if (!input.isAvailable()) {
            if (retryCount >= START_TV_MAX_RETRY) {
                showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
                return;
            }
            if (DEBUG) Log.d(TAG, "Retry start TV (retryCount=" + retryCount + ")");
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_TV_RETRY,
                    retryCount + 1, 0, new Object[]{input, channelId}),
                    START_TV_RETRY_INTERVAL);
            return;
        }
        startTv(input, channelId);
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(TAG, "onStop()");
        hideOverlays(true, true, true, false);
        mHandler.removeMessages(MSG_START_TV_RETRY);
        stopTv();
        stopPip();
        super.onStop();
    }

    public void onInputPicked(TvInput input) {
        if (input.equals(getSelectedTvInput())) {
            // Nothing has changed thus nothing to do.
            return;
        }
        if (!input.hasChannel(false)) {
            mTvInputForSetup = null;
            if (!startSetupActivity(input)) {
                String message = String.format(
                        getString(R.string.empty_channel_tvinput_and_no_setup_activity),
                        input.getDisplayName());
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
            }
            return;
        }

        stopTv();
        startTvWithLastWatchedChannel(input);
    }

    public TvInputManagerHelper getTvInputManagerHelper() {
        return mTvInputManagerHelper;
    }

    public TvInput getSelectedTvInput() {
        return mChannelMap == null ? null : mChannelMap.getTvInput();
    }

    public void showEditChannelsFragment(int initiator) {
        showSideFragment(new EditChannelsFragment(mChannelMap.getChannelList(false)), initiator);
    }

    public boolean startSetupActivity() {
        if (getSelectedTvInput() == null) {
            return false;
        }
        return startSetupActivity(getSelectedTvInput());
    }

    public boolean startSetupActivity(TvInput input) {
        Intent intent = input.getIntentForSetupActivity();
        if (intent == null) {
            return false;
        }
        startActivityForResult(intent, REQUEST_START_SETUP_ACTIIVTY);
        mTvInputForSetup = input;
        mInitTvInputId = null;
        stopTv();
        return true;
    }

    public boolean startSettingsActivity() {
        TvInput input = getSelectedTvInput();
        if (input == null) {
            Log.w(TAG, "There is no selected TV input during startSettingsActivity");
            return false;
        }
        Intent intent = input.getIntentForSettingsActivity();
        if (intent == null) {
            return false;
        }
        startActivity(intent);
        return true;
    }

    public void showSimpleGuide(int initiator) {
        showSideFragment(new SimpleGuideFragment(this, mChannelMap), initiator);
    }

    public void showInputPicker(int initiator) {
        showSideFragment(new InputPickerFragment(), initiator);
    }

    public void showDisplayModeOption(int initiator) {
        showSideFragment(new DisplayModeOptionFragment(), initiator);
    }

    public void showClosedCaptionOption(int initiator) {
        showSideFragment(new ClosedCaptionOptionFragment(), initiator);
    }

    public void showSideFragment(Fragment f, int initiator) {
        mSidePanelContainer.setTag(SIDE_FRAGMENT_TAG_SHOW);
        mSidePanelContainer.setKeyDispatchable(true);

        Bundle bundle = new Bundle();
        bundle.putInt(BaseSideFragment.KEY_INITIATOR, initiator);
        f.setArguments(bundle);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.right_panel, f);
        ft.addToBackStack(null);
        ft.commit();

        mHideSideFragment.showAndHide();
    }

    public void popFragmentBackStack() {
        if (getFragmentManager().getBackStackEntryCount() > 1) {
            getFragmentManager().popBackStack();
        } else if (getFragmentManager().getBackStackEntryCount() == 1
                && mSidePanelContainer.getTag() != SIDE_FRAGMENT_TAG_RESET) {
            if (mSidePanelContainer.getTag() == SIDE_FRAGMENT_TAG_SHOW) {
                mSidePanelContainer.setKeyDispatchable(false);
                mSidePanelContainer.setTag(SIDE_FRAGMENT_TAG_HIDE);
                mHideSideFragment.hideImmediately(true);
            } else {
                // It is during fade-out animation.
            }
        } else {
            getFragmentManager().popBackStack();
        }
    }

    public void onSideFragmentCanceled(int initiator) {
        if (mSidePanelContainer.getTag() == SIDE_FRAGMENT_TAG_RESET) {
            return;
        }
        if (initiator == BaseSideFragment.INITIATOR_MENU) {
            displayMainMenu(false);
        }
    }

    private void resetSideFragment() {
        while (true) {
            if (!getFragmentManager().popBackStackImmediate()) {
                break;
            }
        }
        mSidePanelContainer.setTag(SIDE_FRAGMENT_TAG_RESET);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_START_SETUP_ACTIIVTY:
                if (resultCode == Activity.RESULT_OK && mTvInputForSetup != null) {
                    mInitTvInputId = mTvInputForSetup.getId();
                }
                break;

            default:
                //TODO: Handle failure of setup.
        }
        mTvInputForSetup = null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (DEBUG) Log.d(TAG, "dispatchKeyEvent(" + event + ")");
        int eventKeyCode = event.getKeyCode();
        if (mUseKeycodeBlacklist) {
            for (int keycode : KEYCODE_BLACKLIST) {
                if (keycode == eventKeyCode) {
                    return super.dispatchKeyEvent(event);
                }
            }
            return dispatchKeyEventToSession(event);
        } else {
            for (int keycode : KEYCODE_WHITELIST) {
                if (keycode == eventKeyCode) {
                    return dispatchKeyEventToSession(event);
                }
            }
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        mAudioFocusStatus = focusChange;
        setVolumeByAudioFocusStatus();
    }

    private void setVolumeByAudioFocusStatus() {
        if (mTvView.isPlaying()) {
            switch (mAudioFocusStatus) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    mTvView.setStreamVolume(AUDIO_MAX_VOLUME);
                    if (isShyModeSet()) {
                        setShynessMode(false);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    mTvView.setStreamVolume(AUDIO_MIN_VOLUME);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mTvView.setStreamVolume(AUDIO_DUCKING_VOLUME);
                    break;
            }
        }
        // When the activity loses the audio focus, set the Shy mode regardless of the play status.
        if (mAudioFocusStatus == AudioManager.AUDIOFOCUS_LOSS ||
                mAudioFocusStatus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (!isShyModeSet()) {
                setShynessMode(true);
            }
        }
    }

    private void startTvWithLastWatchedChannel(TvInput input) {
        long channelId = Utils.getLastWatchedChannelId(TvActivity.this, input.getId());
        startTv(input, channelId);
    }

    private void startTv(TvInput input, long channelId) {
        if (mChannelMap != null) {
            // TODO: when this case occurs, we should remove the case.
            Log.w(TAG, "The previous variables are not released in startTv");
            stopTv();
        }

        mMainMenuView.setChannelMap(null);
        int result = mAudioManager.requestAudioFocus(TvActivity.this,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        mAudioFocusStatus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) ?
                        AudioManager.AUDIOFOCUS_GAIN : AudioManager.AUDIOFOCUS_LOSS;

        // Prepare a new channel map for the current input.
        mChannelMap = input.buildChannelMap(this, channelId, mOnChannelsLoadFinished);
        mTvView.start(mTvInputManagerHelper);
        setVolumeByAudioFocusStatus();
        tune();
    }

    private void stopTv() {
        if (mTvView.isPlaying()) {
            mTvView.stop();
            mAudioManager.abandonAudioFocus(this);
        }
        if (mChannelMap != null) {
            mMainMenuView.setChannelMap(null);
            mChannelMap.close();
            mChannelMap = null;
        }
        mTunePendding = false;

        if (!isShyModeSet()) {
            setShynessMode(true);
        }
    }

    private boolean isPlaying() {
        return mTvView.isPlaying() && mTvView.getCurrentChannelId() != Channel.INVALID_ID;
    }

    private void startPip() {
        if (mPipChannelId == Channel.INVALID_ID) {
            Log.w(TAG, "PIP channel id is an invalid id.");
            return;
        }
        if (DEBUG) Log.d(TAG, "startPip()");
        mPipView.start(mTvInputManagerHelper);
        boolean success = mPipView.tuneTo(mPipChannelId, new OnTuneListener() {
            @Override
            public void onUnexpectedStop(long channelId) {
                Log.w(TAG, "The PIP is Unexpectedly stopped");
                enablePipView(false);
            }

            @Override
            public void onTuned(boolean success, long channelId) {
                if (!success) {
                    Log.w(TAG, "Fail to start the PIP during channel tunning");
                    enablePipView(false);
                } else {
                    mPipView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onStreamInfoChanged(StreamInfo info) {
                // Do nothing.
            }
        });
        if (!success) {
            Log.w(TAG, "Fail to start the PIP");
            return;
        }
        mPipView.setStreamVolume(AUDIO_MIN_VOLUME);
    }

    private void stopPip() {
        if (DEBUG) Log.d(TAG, "stopPip");
        if (mPipView.isPlaying()) {
            mPipView.setVisibility(View.INVISIBLE);
            mPipView.stop();
        }
    }

    private final Runnable mOnChannelsLoadFinished = new Runnable() {
        @Override
        public void run() {
            if (mTunePendding) {
                tune();
            }
            mMainMenuView.setChannelMap(mChannelMap);
        }
    };

    private void tune() {
        if (DEBUG) Log.d(TAG, "tune()");
        // Prerequisites to be able to tune.
        if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
            if (DEBUG) Log.d(TAG, "Channel map not ready");
            mTunePendding = true;
            return;
        }
        mTunePendding = false;
        long channelId = mChannelMap.getCurrentChannelId();
        final String inputId = mChannelMap.getTvInput().getId();
        if (channelId == Channel.INVALID_ID) {
            stopTv();
            Toast.makeText(this, R.string.input_is_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        mTvView.tuneTo(channelId, new OnTuneListener() {
            @Override
            public void onUnexpectedStop(long channelId) {
                stopTv();
                startTv(Channel.INVALID_ID);
            }

            @Override
            public void onTuned(boolean success, long channelId) {
                if (!success) {
                    Log.w(TAG, "Failed to tune to channel " + channelId);
                    // TODO: show something to user about this error.
                } else {
                    Utils.setLastWatchedChannelId(TvActivity.this, inputId,
                            mTvView.getCurrentTvInputInfo().getId(), channelId);
                }
            }

            @Override
            public void onStreamInfoChanged(StreamInfo info) {
                updateChannelBanner(false);
            }
        });
        updateChannelBanner(true);
        if (isShyModeSet()) {
            setShynessMode(false);
            // TODO: Set the shy mode to true when tune() fails.
        }
    }

    public void hideOverlays(boolean hideMainMenu, boolean hideChannelBanner,
            boolean hideSidePanel) {
        hideOverlays(hideMainMenu, hideChannelBanner, hideSidePanel, true);
    }

    public void hideOverlays(boolean hideMainMenu, boolean hideChannelBanner,
            boolean hideSidePanel, boolean withAnimation) {
        if (hideMainMenu) {
            mHideMainMenu.hideImmediately(withAnimation);
        }
        if (hideChannelBanner) {
            mHideChannelBanner.hideImmediately(withAnimation);
        }
        if (hideSidePanel) {
            if (mSidePanelContainer.getTag() != SIDE_FRAGMENT_TAG_SHOW) {
                return;
            }
            mSidePanelContainer.setTag(SIDE_FRAGMENT_TAG_HIDE);
            mHideSideFragment.hideImmediately(withAnimation);
        }
    }

    private void updateChannelBanner(final boolean showBanner) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
                    return;
                }

                mChannelBanner.updateViews(mChannelMap, mTvView);
                if (showBanner) {
                    mHideChannelBanner.showAndHide();
                }
            }
        });
    }

    private void displayMainMenu(final boolean resetSelectedItemPosition) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
                    return;
                }

                if (!mMainMenuView.isShown() && resetSelectedItemPosition) {
                    mMainMenuView.resetSelectedItemPosition();
                }
                mHideMainMenu.showAndHide();
            }
        });
    }

    public void showRecentlyWatchedDialog() {
        showDialogFragment(RecentlyWatchedDialogFragment.DIALOG_TAG,
                new RecentlyWatchedDialogFragment());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Do not save instance state because restoring instance state when TV app died
        // unexpectedly can cause some problems like initializing fragments duplicately and
        // accessing resource before it is initialzed.
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        mTvInputManagerHelper.stop();
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                popFragmentBackStack();
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }
        if (mMainMenuView.isShown() || mChannelBanner.isShown()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hideOverlays(true, true, false);
                return true;
            }
            if (mMainMenuView.isShown()) {
                return super.onKeyUp(keyCode, event);
            }
        }

        if (mHandler.hasMessages(MSG_START_TV_RETRY)) {
            // Ignore key events during startTv retry.
            return true;
        }
        if (mChannelMap == null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_H:
                    showRecentlyWatchedDialog();
                    return true;
                case KeyEvent.KEYCODE_TV_INPUT:
                case KeyEvent.KEYCODE_I:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_E:
                case KeyEvent.KEYCODE_MENU:
                    showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
                    return true;
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_H:
                    showRecentlyWatchedDialog();
                    return true;

                case KeyEvent.KEYCODE_TV_INPUT:
                case KeyEvent.KEYCODE_I:
                    showInputPicker(BaseSideFragment.INITIATOR_UNKNOWN);
                    return true;

                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_DPAD_UP:
                    channelUp();
                    return true;

                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    channelDown();
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    displayMainMenu(true);
                    return true;

                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_E:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_MENU:
                    if (event.isCanceled()) {
                        return true;
                    }
                    if (keyCode != KeyEvent.KEYCODE_MENU) {
                        updateChannelBanner(true);
                    }
                    if (keyCode != KeyEvent.KEYCODE_E) {
                        displayMainMenu(true);
                    }
                    return true;
            }
        }
        if (USE_DEBUG_KEYS) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_W: {
                    mDebugNonFullSizeScreen = !mDebugNonFullSizeScreen;
                    if (mDebugNonFullSizeScreen) {
                        mTvView.layout(100, 100, 400, 300);
                    } else {
                        ViewGroup.LayoutParams params = mTvView.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        mTvView.setLayoutParams(params);
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_P: {
                    togglePipView();
                    return true;
                }
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT: {
                    mUseKeycodeBlacklist = !mUseKeycodeBlacklist;
                    return true;
                }
                case KeyEvent.KEYCODE_O: {
                    showDisplayModeOption(BaseSideFragment.INITIATOR_SHORTCUT_KEY);
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (DEBUG) Log.d(TAG, "onKeyLongPress(" + event);
        // Treat the BACK key long press as the normal press since we changed the behavior in
        // onBackPressed().
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            super.onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() <= 0 && isPlaying()) {
            // TODO: show the following toast message in the future.
//            Toast.makeText(getApplicationContext(), getResources().getString(
//                    R.string.long_press_back), Toast.LENGTH_SHORT).show();

            // If back key would exit TV app,
            // show McLauncher instead so we can get benefit of McLauncher's shyMode.
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (mMainMenuView.getVisibility() == View.VISIBLE
                && mSidePanelContainer.getTag() != SIDE_FRAGMENT_TAG_SHOW) {
            mHideMainMenu.showAndHide();
        }
        if (mSidePanelContainer.getTag() == SIDE_FRAGMENT_TAG_SHOW) {
            mHideSideFragment.showAndHide();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMainMenuView.getVisibility() != View.VISIBLE) {
            mGestureDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    public void togglePipView() {
        enablePipView(!mPipEnabled);
    }

    public void enablePipView(boolean enable) {
        if (enable == mPipEnabled) {
            return;
        }
        if (enable) {
            long pipChannelId = mTvView.getCurrentChannelId();
            if (pipChannelId != Channel.INVALID_ID) {
                mPipEnabled = true;
                mPipChannelId = pipChannelId;
                startPip();
            }
        } else {
            mPipEnabled = false;
            mPipChannelId = Channel.INVALID_ID;
            stopPip();
        }
    }

    private boolean dispatchKeyEventToSession(final KeyEvent event) {
        if (DEBUG) Log.d(TAG, "dispatchKeyEventToSession(" + event + ")");
        if (mTvView != null) {
            return mTvView.dispatchKeyEvent(event);
        }
        return false;
    }

    public void moveToChannel(long id) {
        if (mChannelMap != null && mChannelMap.isLoadFinished()
                && id != mChannelMap.getCurrentChannelId()) {
            if (mChannelMap.moveToChannel(id)) {
                tune();
            } else if (!TextUtils.isEmpty(Utils.getInputIdForChannel(this, id))) {
                startTv(id);
            } else {
                Toast.makeText(this, R.string.input_is_not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void channelUp() {
        if (mChannelMap != null && mChannelMap.isLoadFinished()) {
            if (mChannelMap.moveToNextChannel()) {
                tune();
            } else {
                Toast.makeText(this, R.string.input_is_not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void channelDown() {
        if (mChannelMap != null && mChannelMap.isLoadFinished()) {
            if (mChannelMap.moveToPreviousChannel()) {
                tune();
            } else {
                Toast.makeText(this, R.string.input_is_not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void showDialogFragment(final String tag, final DialogFragment dialog) {
        // A tag for dialog must be added to AVAILABLE_DIALOG_TAGS to make it launchable from TV.
        if (!AVAILABLE_DIALOG_TAGS.contains(tag)) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                FragmentManager fm = getFragmentManager();
                fm.executePendingTransactions();

                for (String availableTag : AVAILABLE_DIALOG_TAGS) {
                    if (fm.findFragmentByTag(availableTag) != null) {
                        return;
                    }
                }

                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.addToBackStack(null);
                dialog.show(ft, tag);
            }
        });
    }

    public boolean isClosedCaptionEnabled() {
        return mIsClosedCaptionEnabled;
    }

    public void setClosedCaptionEnabled(boolean enable, boolean storeInPreference) {
        mIsClosedCaptionEnabled = enable;
        if (storeInPreference) {
            mSharedPreferences.edit().putBoolean(TvSettings.PREF_CLOSED_CAPTION_ENABLED, enable)
                    .apply();
        }
        // TODO: send the change to TIS
    }

    public void restoreClosedCaptionEnabled() {
        setClosedCaptionEnabled(mSharedPreferences.getBoolean(
                TvSettings.PREF_CLOSED_CAPTION_ENABLED, false), false);
    }

    // Returns a constant defined in DisplayMode.
    public int getDisplayMode() {
        return mDisplayMode;
    }

    public void setDisplayMode(int displayMode, boolean storeInPreference) {
        mDisplayMode = displayMode;
        if (storeInPreference) {
            mSharedPreferences.edit().putInt(TvSettings.PREF_DISPLAY_MODE, displayMode).apply();
        }
        // TODO: change display mode
    }

    public void restoreDisplayMode() {
        setDisplayMode(mSharedPreferences.getInt(TvSettings.PREF_DISPLAY_MODE,
                DisplayMode.MODE_NORMAL), false);
    }

    private class HideRunnable implements Runnable {
        private final View mView;
        private final long mWaitingTime;
        private boolean mOnHideAnimation;
        private final Runnable mPreShowListener;
        private final Runnable mPostHideListener;

        private HideRunnable(View view, long waitingTime) {
            this(view, waitingTime, null, null);
        }

        private HideRunnable(View view, long waitingTime, Runnable preShowListener,
                Runnable postHideListener) {
            mView = view;
            mWaitingTime = waitingTime;
            mPreShowListener = preShowListener;
            mPostHideListener = postHideListener;
        }

        @Override
        public void run() {
            startHideAnimation(false);
        }

        private void startHideAnimation(boolean fastFadeOutRequired) {
            mOnHideAnimation = true;
            Animation anim = AnimationUtils.loadAnimation(TvActivity.this,
                    android.R.anim.fade_out);
            anim.setInterpolator(AnimationUtils.loadInterpolator(TvActivity.this,
                    android.R.interpolator.fast_out_linear_in));
            if (fastFadeOutRequired) {
                anim.setDuration(mShortAnimationDuration);
            }
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mOnHideAnimation) {
                        hideView();
                    }
                }
            });

            mView.clearAnimation();
            mView.startAnimation(anim);
        }

        private void hideView() {
            mOnHideAnimation = false;
            mView.setVisibility(View.GONE);
            if (mPostHideListener != null) {
                mPostHideListener.run();
            }
        }

        private void hideImmediately(boolean withAnimation) {
            if (mView.getVisibility() != View.VISIBLE) {
                return;
            }
            if (!withAnimation) {
                mHandler.removeCallbacks(this);
                hideView();
                mView.clearAnimation();
                return;
            }
            if (!mOnHideAnimation) {
                mHandler.removeCallbacks(this);
                startHideAnimation(true);
            }
        }

        private void showAndHide() {
            if (mView.getVisibility() != View.VISIBLE) {
                if (mPreShowListener != null) {
                    mPreShowListener.run();
                }
                mView.setVisibility(View.VISIBLE);
                Animation anim = AnimationUtils.loadAnimation(TvActivity.this,
                        android.R.anim.fade_in);
                anim.setInterpolator(AnimationUtils.loadInterpolator(TvActivity.this,
                        android.R.interpolator.linear_out_slow_in));
                mView.clearAnimation();
                mView.startAnimation(anim);
            }
            // Schedule the hide animation after a few seconds.
            mHandler.removeCallbacks(this);
            if (mOnHideAnimation) {
                mOnHideAnimation = false;
                mView.clearAnimation();
                mView.setAlpha(1f);
            }
            mHandler.postDelayed(this, mWaitingTime);
        }
    }

    private void setShynessMode(boolean shyMode) {
        mIsShy = shyMode;
        Intent intent = new Intent(LEANBACK_SET_SHYNESS_BROADCAST);
        intent.putExtra(LEANBACK_SHY_MODE_EXTRA, shyMode);
        sendBroadcast(intent);
    }

    private boolean isShyModeSet() {
        return mIsShy;
    }
}

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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.TvContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;
import android.tv.TvView;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.menu.EditChannelsDialogFragment;
import com.android.tv.menu.MenuDialogFragment;
import com.android.tv.menu.PrivacySettingDialogFragment;
import com.android.tv.ui.ChannelBannerView;
import com.android.tv.ui.MainMenuView;

import java.util.Collection;
import java.util.HashSet;

/**
 * The main activity for demonstrating TV app.
 */
public class TvActivity extends Activity implements
        InputPickerDialogFragment.InputPickerDialogListener,
        AudioManager.OnAudioFocusChangeListener {
    // STOPSHIP: Turn debugging off
    private static final boolean DEBUG = true;
    private static final String TAG = "TvActivity";

    private static final int MSG_START_DEFAULT_SESSION_RETRY = 1;

    private static final int DURATION_SHOW_CHANNEL_BANNER = 2000;
    private static final int DURATION_SHOW_CONTROL_GUIDE = 1000;
    private static final int DURATION_SHOW_MAIN_MENU = 3000;
    private static final float AUDIO_MAX_VOLUME = 1.0f;
    private static final float AUDIO_MIN_VOLUME = 0.0f;
    private static final float AUDIO_DUCKING_VOLUME = 0.3f;
    private static final int DELAY_FOR_SURFACE_RELEASE = 300;
    private static final int START_DEFAULT_SESSION_MAX_RETRY = 4;
    private static final int START_DEFAULT_SESSION_RETRY_INTERVAL = 250;
    private static final String PREF_KEY_IS_UNIFIED_TV_INPUT = "unified_tv_input";
    // TODO: add more KEYCODEs to the white list.
    private static final int[] KEYCODE_WHITELIST = {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_7,
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
    private TvView mTvView;
    private LinearLayout mControlGuide;
    private MainMenuView mMainMenuView;
    private ChannelBannerView mChannelBanner;
    private HideRunnable mHideChannelBanner;
    private HideRunnable mHideControlGuide;
    private HideRunnable mHideMainMenu;
    private int mShortAnimationDuration;
    private int mDisplayWidth;
    private GestureDetector mGestureDetector;
    private ChannelMap mChannelMap;
    private long mInitChannelId;
    private TvInputManager.Session mTvSession;
    private TvInputInfo mTvInputInfo;
    private TvInputInfo mTvInputInfoForSetup;
    private boolean mIsUnifiedTvInput;
    private TvInputManagerHelper mTvInputManagerHelper;
    private AudioManager mAudioManager;
    private int mAudioFocusStatus;
    private boolean mTunePendding;
    private boolean mPipShowing;
    private boolean mDebugNonFullSizeScreen;
    private boolean mUseKeycodeBlacklist = USE_KEYCODE_BLACKLIST;
    private boolean mIsShy = true;

    static {
        AVAILABLE_DIALOG_TAGS.add(InputPickerDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(MenuDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(RecentlyWatchedDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(EditChannelsDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(EditInputDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(PrivacySettingDialogFragment.DIALOG_TAG);
    }

    private final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

        @Override
        public void surfaceCreated(SurfaceHolder holder) { }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                if (DEBUG) Log.d(TAG, "Sleep to wait destroying a surface");
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
                if (DEBUG) Log.d(TAG, "Wake up from sleeping");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    // PIP is used for debug/verification of multiple sessions rather than real PIP feature.
    // When PIP is enabled, the same channel as mTvView is tuned.
    private TvView mPipView;
    private TvInputManager.Session mPipSession;
    private TvInputInfo mPipInputInfo;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_START_DEFAULT_SESSION_RETRY) {
                Object[] arg = (Object[]) msg.obj;
                TvInputInfo input = (TvInputInfo) arg[0];
                long channelId = (Long) arg[1];
                int retryCount = msg.arg1;
                startSessionIfAvailableOrRetry(input, channelId, retryCount);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tv);
        mTvView = (TvView) findViewById(R.id.tv_view);
        mTvView.setOnUnhandledInputEventListener(new TvView.OnUnhandledInputEventListener() {
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
        mTvView.getHolder().addCallback(mSurfaceHolderCallback);
        mPipView = (TvView) findViewById(R.id.pip_view);
        mPipView.setZOrderMediaOverlay(true);
        mPipView.getHolder().addCallback(mSurfaceHolderCallback);

        mControlGuide = (LinearLayout) findViewById(R.id.control_guide);
        mChannelBanner = (ChannelBannerView) findViewById(R.id.channel_banner);
        mMainMenuView = (MainMenuView) findViewById(R.id.main_menu);
        mMainMenuView.setTvActivity(this);

        // Initially hide the channel banner and the control guide.
        mChannelBanner.setVisibility(View.GONE);
        mMainMenuView.setVisibility(View.GONE);
        mControlGuide.setVisibility(View.GONE);

        mHideControlGuide = new HideRunnable(mControlGuide, DURATION_SHOW_CONTROL_GUIDE);
        mHideChannelBanner = new HideRunnable(mChannelBanner, DURATION_SHOW_CHANNEL_BANNER);
        mHideMainMenu = new HideRunnable(mMainMenuView, DURATION_SHOW_MAIN_MENU);

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
                    showInputPickerDialog();
                    return true;
                }

                if (event.getX() > mLeftMargin && event.getX() < mRightMargin) {
                    displayMainMenu();
                    return true;
                }
                return false;
            }
        });

        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        mTvInputManagerHelper = new TvInputManagerHelper(mTvInputManager);
        mIsUnifiedTvInput = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_KEY_IS_UNIFIED_TV_INPUT, false);
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
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
        mTvInputManagerHelper.start();
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
        startDefaultSession(mInitChannelId);
        mInitChannelId = Channel.INVALID_ID;
    }

    private void startDefaultSession(long channelId) {
        if (mTvInputInfo != null) {
            // A session has already started.
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
            stopSession();
        }

        if (channelId == Channel.INVALID_ID) {
            // If any initial channel id is not given, remember the last channel the user watched.
            channelId = Utils.getLastWatchedChannelId(this);
        }
        if (channelId == Channel.INVALID_ID) {
            // If failed to pick a channel, try a different input.
            showInputPickerDialog();
            return;
        }
        String inputId = Utils.getInputIdForChannel(this, channelId);
        if (TextUtils.isEmpty(inputId)) {
            // If failed to determine the input for that channel, try a different input.
            showInputPickerDialog();
            return;
        }
        TvInputInfo input = mTvInputManagerHelper.getTvInputInfo(inputId);
        if (input == null) {
            // TODO: if the last selected TV input is uninstalled, getLastWatchedChannelId
            // should return Channel.INVALID_ID.
            Log.w(TAG, "Input (id=" + inputId + ") doesn't exist");
            showInputPickerDialog();
            return;
        }
        startSessionIfAvailableOrRetry(input, channelId, 0);
    }

    private void startSessionIfAvailableOrRetry(TvInputInfo input, long channelId, int retryCount) {
        if (!mTvInputManagerHelper.isAvailable(input.getId())) {
            if (retryCount >= START_DEFAULT_SESSION_MAX_RETRY) {
                showInputPickerDialog();
                return;
            }
            if (DEBUG) Log.d(TAG, "Retry start session (retryCount=" + retryCount + ")");
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_DEFAULT_SESSION_RETRY,
                    retryCount + 1, 0, new Object[]{input, channelId}),
                    START_DEFAULT_SESSION_RETRY_INTERVAL);
            return;
        }
        startSession(input, channelId);
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(TAG, "onStop() -- stop all sessions");
        mHandler.removeMessages(MSG_START_DEFAULT_SESSION_RETRY);
        stopSession();
        stopPipSession();
        if (!isShyModeSet()) {
            setShynessMode(true);
        }
        mTvInputManagerHelper.stop();
        super.onStop();
    }

    @Override
    public void onInputPicked(TvInputInfo input) {
        if (input == null) {
            // For unified TV input.
            if (mIsUnifiedTvInput) {
                return;
            }
            mIsUnifiedTvInput = true;
            if (mTvInputInfo == null) {
                Collection<TvInputInfo> inputs = mTvInputManagerHelper.getTvInputInfos(true);
                if (inputs.isEmpty()) {
                    Toast.makeText(this, R.string.no_available_input_device, Toast.LENGTH_SHORT)
                            .show();
                } else {
                    TvInputInfo info = inputs.iterator().next();
                    startSession(info);
                }
                return;
            } else {
                // Restart session to re-create the channel map.
                input = mTvInputInfo;
            }
        } else {
            if (mTvSession != null && input.equals(mTvInputInfo) && !mIsUnifiedTvInput) {
                // Nothing has changed thus nothing to do.
                return;
            }
            mIsUnifiedTvInput = false;
            if (!Utils.hasChannel(this, input, false)) {
                mTvInputInfoForSetup = null;
                startSetupActivity(input);
                return;
            }
        }

        // Start a new session with the new input.
        stopSession();

        // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
        // know when the surface is released at TIS, we don't need this hack.
        try {
            Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        startSession(input);
    }

    public void showEditChannelsDialog() {
        if (mTvInputInfo == null) {
            return;
        }

        EditChannelsDialogFragment f = new EditChannelsDialogFragment();
        Bundle arg = new Bundle();
        arg.putParcelable(EditChannelsDialogFragment.ARG_CURRENT_INPUT, mTvInputInfo);
        arg.putBoolean(EditChannelsDialogFragment.ARG_IS_UNIFIED_TV_INPUT, mIsUnifiedTvInput);
        f.setArguments(arg);

        showDialogFragment(EditChannelsDialogFragment.DIALOG_TAG, f);
    }

    public void showInputPickerDialog() {
        InputPickerDialogFragment f = new InputPickerDialogFragment();
        Bundle arg = new Bundle();
        if (mTvInputInfo != null) {
            arg.putString(InputPickerDialogFragment.ARG_MAIN_INPUT_ID, mTvInputInfo.getId());
            arg.putBoolean(InputPickerDialogFragment.ARG_IS_UNIFIED_TV_INPUT, mIsUnifiedTvInput);
        }
        if (mPipInputInfo != null) {
            arg.putString(InputPickerDialogFragment.ARG_SUB_INPUT_ID, mPipInputInfo.getId());
        }
        f.setArguments(arg);
        showDialogFragment(InputPickerDialogFragment.DIALOG_TAG, f);
    }

    public void startSettingsActivity() {
        if (mTvInputInfo == null) {
            Log.w(TAG, "mTvInputInfo is null in showSettingsActivity");
        }
        Utils.startActivity(this, mTvInputInfo, Utils.ACTION_SETTINGS);
    }

    public void startSetupActivity() {
        if (mTvInputInfo != null) {
            startSetupActivity(mTvInputInfo);
        }
    }

    public void startSetupActivity(TvInputInfo input) {
        if (Utils.startActivityForResult(this, input, Utils.ACTION_SETUP,
                REQUEST_START_SETUP_ACTIIVTY)) {
            mTvInputInfoForSetup = input;
            stopSession();
        } else {
            String displayName = Utils.getDisplayNameForInput(this, input, false);
            String message = String.format(getString(
                    R.string.input_setup_activity_not_found), displayName);
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(R.string.OK, null)
                    .show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_START_SETUP_ACTIIVTY:
                if (resultCode == Activity.RESULT_OK && mTvInputInfoForSetup != null) {
                    startSession(mTvInputInfoForSetup);
                }
                break;

            default:
                //TODO: Handle failure of setup.
        }
        mTvInputInfoForSetup = null;
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
        if (mTvSession != null) {
            switch (mAudioFocusStatus) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    mTvSession.setVolume(AUDIO_MAX_VOLUME);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    mTvSession.setVolume(AUDIO_MIN_VOLUME);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mTvSession.setVolume(AUDIO_DUCKING_VOLUME);
                    break;
            }
        }
    }

    private void startSession(TvInputInfo inputInfo) {
        long channelId = Utils.getLastWatchedChannelId(TvActivity.this, inputInfo.getId());
        startSession(inputInfo, channelId);
    }

    private void startSession(TvInputInfo inputInfo, long channelId) {
        if (mTvInputInfo != null || mChannelMap != null) {
            // TODO: when this case occurs, we should remove the case.
            Log.w(TAG, "The previous variables are not released in startSession");
            stopSession();
        }

        // TODO: recreate SurfaceView to prevent abusing from the previous session.
        mTvInputInfo = inputInfo;

        mMainMenuView.setChannelMap(null);

        // Prepare a new channel map for the current input.
        mChannelMap = new ChannelMap(this, mIsUnifiedTvInput ? null : inputInfo,
                channelId, mTvInputManagerHelper, mOnChannelsLoadFinished);
        // Create a new session and start.
        mTvView.bindTvInput(inputInfo.getId(), mSessionCreated);
        tune();
    }

    private void changeSession(TvInputInfo inputInfo) {
        mTvSession = null;
        mTvView.unbindTvInput();
        // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
        // know when the surface is released at TIS, we don't need this hack.
        try {
            Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mTvInputInfo = inputInfo;
        mTvView.bindTvInput(inputInfo.getId(), mSessionCreated);
        tune();
    }

    private final TvInputManager.SessionCallback mSessionCreated =
            new TvInputManager.SessionCallback() {
                @Override
                public void onSessionCreated(TvInputManager.Session session) {
                    if (session != null) {
                        mTvSession = session;
                        int result = mAudioManager.requestAudioFocus(TvActivity.this,
                                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                        mAudioFocusStatus =
                                (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) ?
                                        AudioManager.AUDIOFOCUS_GAIN
                                        : AudioManager.AUDIOFOCUS_LOSS;
                        if (mTunePendding) {
                            tune();
                        }
                    } else {
                        Log.w(TAG, "Failed to create a session");
                        // TODO: show something to user about this error.
                    }
                }
            };

    private void startPipSession() {
        if (mTvSession == null) {
            Log.w(TAG, "TV content should be playing");
            return;
        }
        if (DEBUG) Log.d(TAG, "startPipSession()");
        mPipInputInfo = mTvInputInfo;
        mPipView.bindTvInput(mPipInputInfo.getId(), mPipSessionCreated);
        mPipShowing = true;
    }

    private final TvInputManager.SessionCallback mPipSessionCreated =
            new TvInputManager.SessionCallback() {
                @Override
                public void onSessionCreated(final TvInputManager.Session session) {
                    if (DEBUG) Log.d(TAG, "PIP session is created");
                    if (mTvSession == null) {
                        Log.w(TAG, "TV content should be playing");
                        if (session != null) {
                            mPipView.unbindTvInput();
                        }
                        mPipShowing = false;
                        return;
                    }
                    if (session == null) {
                        Log.w(TAG, "Fail to create another session");
                        mPipShowing = false;
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPipSession = session;
                            mPipSession.setVolume(0);
                            mPipSession.tune(mChannelMap.getCurrentChannelUri());
                            mPipView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            };

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
        Uri currentChannelUri = mChannelMap.getCurrentChannelUri();
        if (currentChannelUri == null) {
            stopSession();
            mTunePendding = false;
            Toast.makeText(this, R.string.input_is_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        String inputId = Utils.getInputIdForChannel(this, currentChannelUri);
        if (!mTvInputInfo.getId().equals(inputId)) {
            if (DEBUG) Log.d(TAG, "TV input is changed");
            changeSession(mTvInputManagerHelper.getTvInputInfo(inputId));
            mTunePendding = true;
            return;
        }
        if (mTvSession == null) {
            if (DEBUG) Log.d(TAG, "Session is not created yet");
            mTunePendding = true;
            return;
        }
        setVolumeByAudioFocusStatus();

        if (currentChannelUri != null) {
            // TODO: implement 'no signal'
            // TODO: add result callback and show a message on failure.
            Utils.setLastWatchedChannel(this, mTvInputInfo.getId(), currentChannelUri);
            mTvSession.tune(currentChannelUri);
            if (isShyModeSet()) {
                setShynessMode(false);
                // TODO: Set the shy mode to true when tune() fails.
            }
            displayChannelBanner();
        }
        mTunePendding = false;
    }

    private void displayChannelBanner() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
                    return;
                }

                mChannelBanner.updateViews(mChannelMap);
                mHideChannelBanner.showAndHide();
            }
        });
    }

    private void displayMainMenu() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
                    return;
                }

                mHideMainMenu.showAndHide();
            }
        });
    }

    public void showRecentlyWatchedDialog() {
        showDialogFragment(RecentlyWatchedDialogFragment.DIALOG_TAG,
                new RecentlyWatchedDialogFragment());
    }

    private void stopSession() {
        if (mTvInputInfo != null) {
            if (mTvSession != null) {
                mTvSession.setVolume(AUDIO_MIN_VOLUME);
                mAudioManager.abandonAudioFocus(this);
                mTvSession = null;
            }
            mTvView.unbindTvInput();
            mTvInputInfo = null;
        }
        if (mChannelMap != null) {
            mMainMenuView.setChannelMap(null);
            mChannelMap.close();
            mChannelMap = null;
        }
        mTunePendding = false;
    }

    private void stopPipSession() {
        if (DEBUG) Log.d(TAG, "stopPipSession");
        if (mPipSession != null) {
            mPipView.setVisibility(View.INVISIBLE);
            mPipView.unbindTvInput();
            mPipSession = null;
            mPipInputInfo = null;
        }
        mPipShowing = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Do not save instance state because restoring instance state when TV app died
        // unexpectedly can cause some problems like initializing fragments duplicately and
        // accessing resource before it is initialzed.
    }

    @Override
    protected void onDestroy() {
        mTvView.getHolder().removeCallback(mSurfaceHolderCallback);
        mPipView.getHolder().removeCallback(mSurfaceHolderCallback);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(PREF_KEY_IS_UNIFIED_TV_INPUT, mIsUnifiedTvInput).apply();
        if (DEBUG) Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mMainMenuView.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mMainMenuView.setVisibility(View.GONE);
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }

        if (mHandler.hasMessages(MSG_START_DEFAULT_SESSION_RETRY)) {
            // Ignore key events during startDefaultSession retry.
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
                    showInputPickerDialog();
                    return true;
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_H:
                    showRecentlyWatchedDialog();
                    return true;

                case KeyEvent.KEYCODE_TV_INPUT:
                case KeyEvent.KEYCODE_I:
                    showInputPickerDialog();
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
                    displayMainMenu();
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
                        displayChannelBanner();
                    }
                    if (keyCode != KeyEvent.KEYCODE_E) {
                        displayMainMenu();
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
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (mMainMenuView.getVisibility() == View.VISIBLE) {
            mHideMainMenu.showAndHide();
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
        if (mPipShowing) {
            stopPipSession();
        } else {
            startPipSession();
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

    private void showMenu() {
        MenuDialogFragment f = new MenuDialogFragment();
        if (mTvSession != null) {
            Bundle arg = new Bundle();
            arg.putParcelable(MenuDialogFragment.ARG_CURRENT_INPUT, mTvInputInfo);
            arg.putBoolean(MenuDialogFragment.ARG_IS_UNIFIED_TV_INPUT, mIsUnifiedTvInput);
            f.setArguments(arg);
        }

        showDialogFragment(MenuDialogFragment.DIALOG_TAG, f);
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

    private class HideRunnable implements Runnable {
        private final View mView;
        private final long mWaitingTime;
        private boolean mOnHideAnimation;

        public HideRunnable(View view, long waitingTime) {
            mView = view;
            mWaitingTime = waitingTime;
        }

        @Override
        public void run() {
            mOnHideAnimation = true;
            mView.animate()
                    .alpha(0f)
                    .setDuration(mShortAnimationDuration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mOnHideAnimation = false;
                            mView.setVisibility(View.GONE);
                        }
                    });
        }

        private void showAndHide() {
            if (mView.getVisibility() != View.VISIBLE) {
                mView.setAlpha(0f);
                mView.setVisibility(View.VISIBLE);
                mView.animate()
                        .alpha(1f)
                        .setDuration(mShortAnimationDuration)
                        .setListener(null);
            }
            // Schedule the hide animation after a few seconds.
            mHandler.removeCallbacks(this);
            if (mOnHideAnimation) {
                mView.clearAnimation();
                mOnHideAnimation = false;
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

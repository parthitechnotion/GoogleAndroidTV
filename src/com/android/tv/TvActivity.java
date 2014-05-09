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
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.TvContract;
import android.tv.TvInputInfo;
import android.tv.TvInputManager;
import android.tv.TvInputService;
import android.tv.TvView;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.menu.MenuDialogFragment;

import java.util.List;

/**
 * The main activity for demonstrating TV app.
 */
public class TvActivity extends Activity implements
        InputPickerDialogFragment.InputPickerDialogListener,
        AudioManager.OnAudioFocusChangeListener {
    // STOPSHIP: Turn debugging off
    private static final boolean DEBUG = true;
    private static final String TAG = "TvActivity";

    private static final int DURATION_SHOW_CHANNEL_BANNER = 2000;
    private static final int DURATION_SHOW_CONTROL_GUIDE = 1000;
    private static final float AUDIO_MAX_VOLUME = 1.0f;
    private static final float AUDIO_MIN_VOLUME = 0.0f;
    private static final float AUDIO_DUCKING_VOLUME = 0.3f;
    private static final int DELAY_FOR_SURFACE_RELEASE = 300;
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

    private TvInputManager mTvInputManager;
    private TvView mTvView;
    private LinearLayout mControlGuide;
    private LinearLayout mChannelBanner;
    private Runnable mHideChannelBanner;
    private Runnable mHideControlGuide;
    private TextView mChannelTextView;
    private TextView mProgramTextView;
    private int mShortAnimationDuration;
    private int mDisplayWidth;
    private GestureDetector mGestureDetector;
    private ChannelMap mChannelMap;
    private TvInputManager.Session mTvSession;
    private TvInputInfo mTvInputInfo;
    private TvInputInfo mTvInputInfoForSetup;
    private AudioManager mAudioManager;
    private int mAudioFocusStatus;
    private final Handler mHandler = new Handler();
    private boolean mDefaultSessionRequested;
    private boolean mTunePendding;
    private boolean mPipShowing;
    private boolean mDebugNonFullSizeScreen;
    private boolean mUseKeycodeBlacklist = USE_KEYCODE_BLACKLIST;
    private boolean mIsShy = true;

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
        mChannelBanner = (LinearLayout) findViewById(R.id.channel_banner);

        // Initially hide the channel banner and the control guide.
        mChannelBanner.setVisibility(View.GONE);
        mControlGuide.setVisibility(View.GONE);

        mHideControlGuide = new HideRunnable(mControlGuide);
        mHideChannelBanner = new HideRunnable(mChannelBanner);

        mChannelTextView = (TextView) findViewById(R.id.channel_text);
        mProgramTextView = (TextView) findViewById(R.id.program_text);

        mShortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioFocusStatus = AudioManager.AUDIOFOCUS_LOSS;
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mDisplayWidth = size.x;

        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            static final float CONTROL_MARGIN = 0.2f;
            final float mLeftMargin = mDisplayWidth * CONTROL_MARGIN;
            final float mRightMargin = mDisplayWidth * (1 - CONTROL_MARGIN);

            @Override
            public boolean onDown(MotionEvent event) {
                Log.d(TAG, "onDown: " + event.toString());
                if (mChannelMap == null) {
                    return false;
                }

                showAndHide(mControlGuide, mHideControlGuide, DURATION_SHOW_CONTROL_GUIDE);

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
                    showMenu();
                    return true;
                }
                return false;
            }
        });

        getContentResolver().registerContentObserver(TvContract.Programs.CONTENT_URI, true,
                mProgramUpdateObserver);

        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startDefaultSession();
    }

    private void startDefaultSession() {
        if (mTvInputManager == null) {
            return;
        }
        // TODO: Remove this check after TvInputManagerService becomes system service.
        if (mDefaultSessionRequested) {
            return;
        }
        mDefaultSessionRequested = true;
        if (mTunePendding) {
            return;
        }

        // Check whether the system has at least one TvInputService app installed.
        final List<ResolveInfo> services = getPackageManager().queryIntentServices(
                new Intent(TvInputService.SERVICE_INTERFACE), PackageManager.GET_SERVICES);
        if (services == null || services.isEmpty()) {
            Toast.makeText(this, R.string.no_input_device_found, Toast.LENGTH_SHORT).show();
            // TODO: Direct the user to a Play Store landing page for TvInputService apps.
            return;
        }

        // Figure out the initial channel to tune to.
        long channelId = Channel.INVALID_ID;
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // In case the channel is given explicitly, use it.
            channelId = ContentUris.parseId(intent.getData());
        }
        if (channelId == Channel.INVALID_ID) {
            // Otherwise, remember the last channel the user watched.
            channelId = TvInputUtils.getLastWatchedChannelId(this);
        }
        if (channelId == Channel.INVALID_ID) {
            // If failed to pick a channel, try a different input.
            showInputPickerDialog();
            return;
        }
        ComponentName inputName = TvInputUtils.getInputNameForChannel(this, channelId);
        if (inputName == null) {
            // If failed to determine the input for that channel, try a different input.
            showInputPickerDialog();
            return;
        }
        // If the session is already started, simply adjust the volume without tune.
        if (mTvSession != null) {
            setVolumeByAudioFocusStatus();
        } else {
            List<TvInputInfo> inputList = mTvInputManager.getTvInputList();
            for (TvInputInfo info : inputList) {
                if (inputName.equals(info.getComponent())) {
                    startSession(info, channelId);
                    break;
                }
            }
        }
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(TAG, "onStop() -- stop all sessions");
        stopSession();
        stopPipSession();
        mDefaultSessionRequested = false;
        super.onStop();
    }

    public void showInputPickerDialog() {
        showDialogFragment(InputPickerDialogFragment.DIALOG_TAG, new InputPickerDialogFragment());
    }

    @Override
    public void onInputPicked(final TvInputInfo selectedTvInput, final String displayName) {
        if (mTvSession != null && selectedTvInput.equals(mTvInputInfo)) {
            // Nothing has changed thus nothing to do.
            return;
        }

        if (!TvInputUtils.hasChannel(this, selectedTvInput)) {
            mTvInputInfoForSetup = null;
            if (showSetupActivity(selectedTvInput, displayName)) {
                stopSession();
            }
            return;
        }

        // Start a new session with the new input.
        stopSession();

        // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
        // know when the surface is released at TIS, we don't need this hack.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startSession(selectedTvInput);
            }
        }, DELAY_FOR_SURFACE_RELEASE);
    }

    private boolean showSetupActivity(TvInputInfo inputInfo, String displayName) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activityInfos = pm.queryIntentActivities(
                new Intent(TvInputUtils.ACTION_SETUP), PackageManager.GET_ACTIVITIES);
        ResolveInfo setupActivity = null;
        if (activityInfos != null) {
            for (ResolveInfo info : activityInfos) {
                if (info.activityInfo.packageName.equals(inputInfo.getPackageName())) {
                    setupActivity = info;
                }
            }
        }

        if (setupActivity == null) {
            String message = String.format(getString(R.string.input_setup_activity_not_found),
                    displayName);
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(R.string.OK, null)
                    .show();
            return false;
        }

        mTvInputInfoForSetup = inputInfo;
        Intent intent = new Intent(TvInputUtils.ACTION_SETUP);
        intent.setClassName(setupActivity.activityInfo.packageName,
                setupActivity.activityInfo.name);
        startActivityForResult(intent, REQUEST_START_SETUP_ACTIIVTY);

        return true;
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

    private void startSession(TvInputInfo selectedTvInput) {
        long channelId = TvInputUtils.getLastWatchedChannelId(TvActivity.this,
                selectedTvInput.getId());
        startSession(selectedTvInput, channelId);
    }

    private void startSession(TvInputInfo inputInfo, long channelId) {
        // TODO: recreate SurfaceView to prevent abusing from the previous session.
        mTvInputInfo = inputInfo;
        // Prepare a new channel map for the current input.
        mChannelMap = new ChannelMap(this, inputInfo.getComponent(), channelId,
                mOnChannelsLoadFinished);
        // Create a new session and start.
        mTvView.bindTvInput(inputInfo.getComponent(), mSessionCreated);
        tune();
    }

    private final TvInputManager.SessionCreateCallback mSessionCreated =
            new TvInputManager.SessionCreateCallback() {
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
            Log.w(TAG, "TV content should be playing.");
            return;
        }
        Log.d(TAG, "startPipSession");
        mPipInputInfo = mTvInputInfo;
        mPipView.bindTvInput(mPipInputInfo.getComponent(), mPipSessionCreated);
        mPipShowing = true;
    }

    private final TvInputManager.SessionCreateCallback mPipSessionCreated =
            new TvInputManager.SessionCreateCallback() {
                @Override
                public void onSessionCreated(final TvInputManager.Session session) {
                    Log.d(TAG, "PIP session is created.");
                    if (mTvSession == null) {
                        Log.w(TAG, "TV content should be playing.");
                        if (session != null) {
                            mPipView.unbindTvInput();
                        }
                        mPipShowing = false;
                        return;
                    }
                    if (session == null) {
                        Log.w(TAG, "Fail to create another session.");
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

    private final ContentObserver mProgramUpdateObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
                return;
            }
            Uri channelUri = mChannelMap.getCurrentChannelUri();
            if (channelUri == null) {
                return;
            }
            Program program = TvInputUtils.getCurrentProgram(TvActivity.this, channelUri);
            if (program == null) {
                return;
            }
            mProgramTextView.setText(program.getTitle());
        }
    };

    private final Runnable mOnChannelsLoadFinished = new Runnable() {
        @Override
        public void run() {
            if (mTunePendding) {
                tune();
            }
        }
    };

    private void tune() {
        Log.d(TAG, "tune()");
        // Prerequisites to be able to tune.
        if (mChannelMap == null || !mChannelMap.isLoadFinished()) {
            Log.d(TAG, "Channel map not ready");
            mTunePendding = true;
            return;
        }
        if (mTvSession == null) {
            Log.d(TAG, "Service not connected");
            mTunePendding = true;
            return;
        }
        setVolumeByAudioFocusStatus();

        Uri currentChannelUri = mChannelMap.getCurrentChannelUri();
        if (currentChannelUri != null) {
            // TODO: implement 'no signal'
            // TODO: add result callback and show a message on failure.
            TvInputUtils.setLastWatchedChannel(this, mTvInputInfo.getId(), currentChannelUri);
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

                // TODO: Show a beautiful channel banner instead.
                String channelBannerString = "";
                String displayNumber = mChannelMap.getCurrentDisplayNumber();
                if (displayNumber != null) {
                    channelBannerString += displayNumber;
                }
                String displayName = mChannelMap.getCurrentDisplayName();
                if (displayName != null) {
                    channelBannerString += " " + displayName;
                }
                mChannelTextView.setText(channelBannerString);

                Program program = TvInputUtils.getCurrentProgram(TvActivity.this,
                        mChannelMap.getCurrentChannelUri());
                String programTitle = program != null ? program.getTitle() : null;
                // Program title might not be available at this point. Setting the text to null to
                // clear the previous program title for now. It will be filled as soon as we get the
                // updated program information.
                mProgramTextView.setText(programTitle);

                showAndHide(mChannelBanner, mHideChannelBanner, DURATION_SHOW_CHANNEL_BANNER);
            }
        });
    }

    public void showRecentlyWatchedDialog() {
        showDialogFragment(RecentlyWatchedDialogFragment.DIALOG_TAG,
                new RecentlyWatchedDialogFragment());
    }

    private void stopSession() {
        if (mTvSession != null) {
            mTvSession.setVolume(AUDIO_MIN_VOLUME);
            mAudioManager.abandonAudioFocus(this);
            mTvView.unbindTvInput();
            mTvSession = null;
            mTvInputInfo = null;
        }
        if (mChannelMap != null) {
            mChannelMap.close();
            mChannelMap = null;
        }
    }

    private void stopPipSession() {
        Log.d(TAG, "stopPipSession");
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
        getContentResolver().unregisterContentObserver(mProgramUpdateObserver);
        mTvView.getHolder().removeCallback(mSurfaceHolderCallback);
        mPipView.getHolder().removeCallback(mSurfaceHolderCallback);
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
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

                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_E:
                    displayChannelBanner();
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_MENU:
                    if (event.isCanceled()) {
                        return true;
                    }
                    showMenu();
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
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
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

    private void channelUp() {
        if (mChannelMap != null && mChannelMap.isLoadFinished()) {
            mChannelMap.moveToNextChannel();
            tune();
        }
    }

    private void channelDown() {
        if (mChannelMap != null && mChannelMap.isLoadFinished()) {
            mChannelMap.moveToPreviousChannel();
            tune();
        }
    }

    private void showMenu() {
        MenuDialogFragment f = new MenuDialogFragment();
        if (mTvSession != null) {
            Bundle arg = new Bundle();
            arg.putString(MenuDialogFragment.ARG_CURRENT_PACKAGE_NAME,
                    mTvInputInfo.getPackageName());
            arg.putString(MenuDialogFragment.ARG_CURRENT_SERVICE_NAME,
                    mTvInputInfo.getServiceName());
            f.setArguments(arg);
        }

        showDialogFragment(MenuDialogFragment.DIALOG_TAG, f);
    }

    private void showDialogFragment(String tag, DialogFragment dialog) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(tag);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        dialog.show(ft, tag);
    }

    private final Handler mHideHandler = new Handler();

    private class HideRunnable implements Runnable {
        private final View mView;

        public HideRunnable(View view) {
            mView = view;
        }

        @Override
        public void run() {
            mView.animate()
                    .alpha(0f)
                    .setDuration(mShortAnimationDuration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mView.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void showAndHide(View view, Runnable hide, long duration) {
        if (view.getVisibility() == View.VISIBLE) {
            // Skip the show animation if the view is already visible and cancel the scheduled hide
            // animation.
            mHideHandler.removeCallbacks(hide);
        } else {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate()
                    .alpha(1f)
                    .setDuration(mShortAnimationDuration)
                    .setListener(null);
        }
        // Schedule the hide animation after a few seconds.
        mHideHandler.postDelayed(hide, duration);
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

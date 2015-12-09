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

package com.android.tv;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView.OnUnhandledInputEventListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.tv.analytics.DurationTimer;
import com.android.tv.analytics.SendConfigInfoRunnable;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.WeakHandler;
import com.android.tv.common.dvr.DvrSessionClient;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.OnCurrentProgramUpdatedListener;
import com.android.tv.data.Program;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.data.StreamInfo;
import com.android.tv.data.WatchedHistoryManager;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dialog.SafeDismissDialogFragment;
import com.android.tv.dvr.DvrManager;
import com.android.tv.menu.Menu;
import com.android.tv.onboarding.OnboardingActivity;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.receiver.AudioCapabilitiesReceiver;
import com.android.tv.recommendation.NotificationService;
import com.android.tv.search.ProgramGuideSearchFragment;
import com.android.tv.ui.AppLayerTvView;
import com.android.tv.ui.ChannelBannerView;
import com.android.tv.ui.InputBannerView;
import com.android.tv.ui.KeypadChannelSwitchView;
import com.android.tv.ui.OverlayRootView;
import com.android.tv.ui.SelectInputView;
import com.android.tv.ui.SelectInputView.OnInputSelectedCallback;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.OnTuneListener;
import com.android.tv.ui.TvOverlayManager;
import com.android.tv.ui.TvViewUiManager;
import com.android.tv.ui.sidepanel.ChannelSourcesFragment;
import com.android.tv.ui.sidepanel.ClosedCaptionFragment;
import com.android.tv.ui.sidepanel.CustomizeChannelListFragment;
import com.android.tv.ui.sidepanel.DebugOptionFragment;
import com.android.tv.ui.sidepanel.DisplayModeFragment;
import com.android.tv.ui.sidepanel.MultiAudioFragment;
import com.android.tv.ui.sidepanel.SideFragment;
import com.android.tv.ui.sidepanel.parentalcontrols.ParentalControlsFragment;
import com.android.tv.util.CaptionSettings;
import com.android.tv.util.ImageCache;
import com.android.tv.util.MemoryManageable;
import com.android.tv.util.OnboardingUtils;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.PipInputManager;
import com.android.tv.util.PipInputManager.PipInput;
import com.android.tv.util.RecurringRunner;
import com.android.tv.util.SearchManagerHelper;
import com.android.tv.util.SetupUtils;
import com.android.tv.util.SoftPreconditions;
import com.android.tv.util.SystemProperties;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.TvSettings;
import com.android.tv.util.TvSettings.PipSound;
import com.android.tv.util.TvTrackInfoUtils;
import com.android.tv.util.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The main activity for the Live TV app.
 */
public class MainActivity extends Activity implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "MainActivity";
    private static final boolean DEBUG = false;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({KEY_EVENT_HANDLER_RESULT_PASSTHROUGH, KEY_EVENT_HANDLER_RESULT_NOT_HANDLED,
        KEY_EVENT_HANDLER_RESULT_HANDLED, KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY})
    public @interface KeyHandlerResultType {}
    public static final int KEY_EVENT_HANDLER_RESULT_PASSTHROUGH = 0;
    public static final int KEY_EVENT_HANDLER_RESULT_NOT_HANDLED = 1;
    public static final int KEY_EVENT_HANDLER_RESULT_HANDLED = 2;
    public static final int KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY = 3;

    private static final boolean USE_BACK_KEY_LONG_PRESS = false;

    private static final float AUDIO_MAX_VOLUME = 1.0f;
    private static final float AUDIO_MIN_VOLUME = 0.0f;
    private static final float AUDIO_DUCKING_VOLUME = 0.3f;
    private static final float FRAME_RATE_FOR_FILM = 23.976f;
    private static final float FRAME_RATE_EPSILON = 0.1f;

    private static final float MEDIA_SESSION_STOPPED_SPEED = 0.0f;
    private static final float MEDIA_SESSION_PLAYING_SPEED = 1.0f;


    private static final int PERMISSIONS_REQUEST_READ_TV_LISTINGS = 1;
    private static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";

    private static final String USB_TV_TUNER_INPUT_ID =
            "com.android.tv/com.android.usbtuner.tvinput.UsbTunerTvInputService";
    private static final String DVR_TEST_INPUT_ID = USB_TV_TUNER_INPUT_ID;

    // Tracker screen names.
    public static final String SCREEN_NAME = "Main";
    private static final String SCREEN_BEHIND_NAME = "Behind";

    private static final float REFRESH_RATE_EPSILON = 0.01f;
    private static final HashSet<Integer> BLACKLIST_KEYCODE_TO_TIS;
    // These keys won't be passed to TIS in addition to gamepad buttons.
    static {
        BLACKLIST_KEYCODE_TO_TIS = new HashSet<>();
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_TV_INPUT);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_MENU);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_CHANNEL_UP);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_CHANNEL_DOWN);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_VOLUME_UP);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_VOLUME_DOWN);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_VOLUME_MUTE);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_MUTE);
        BLACKLIST_KEYCODE_TO_TIS.add(KeyEvent.KEYCODE_SEARCH);
    }

    private static final int REQUEST_CODE_START_SETUP_ACTIVITY = 1;
    private static final int REQUEST_CODE_START_SYSTEM_CAPTIONING_SETTINGS = 2;

    private static final String KEY_INIT_CHANNEL_ID = "com.android.tv.init_channel_id";

    private static final String MEDIA_SESSION_TAG = "com.android.tv.mediasession";

    // Change channels with key long press.
    private static final int CHANNEL_CHANGE_NORMAL_SPEED_DURATION_MS = 3000;
    private static final int CHANNEL_CHANGE_DELAY_MS_IN_MAX_SPEED = 50;
    private static final int CHANNEL_CHANGE_DELAY_MS_IN_NORMAL_SPEED = 200;
    private static final int CHANNEL_CHANGE_INITIAL_DELAY_MILLIS = 500;
    private static final int FIRST_STREAM_INFO_UPDATE_DELAY_MILLIS = 500;

    private static final int MSG_CHANNEL_DOWN_PRESSED = 1000;
    private static final int MSG_CHANNEL_UP_PRESSED = 1001;
    private static final int MSG_UPDATE_CHANNEL_BANNER_BY_INFO_UPDATE = 1002;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW, UPDATE_CHANNEL_BANNER_REASON_TUNE,
        UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST, UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO,
        UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK})
    private @interface ChannelBannerUpdateReason {}
    private static final int UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW = 1;
    private static final int UPDATE_CHANNEL_BANNER_REASON_TUNE = 2;
    private static final int UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST = 3;
    private static final int UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO = 4;
    private static final int UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK = 5;

    private static final int TVVIEW_SET_MAIN_TIMEOUT_MS = 3000;

    // Lazy initialization.
    // Delay 1 second in order not to interrupt the first tune.
    private static final long LAZY_INITIALIZATION_DELAY = TimeUnit.SECONDS.toMillis(1);

    private AccessibilityManager mAccessibilityManager;
    private ChannelDataManager mChannelDataManager;
    private ProgramDataManager mProgramDataManager;
    private WatchedHistoryManager mWatchedHistoryManager;
    private TvInputManagerHelper mTvInputManagerHelper;
    private ChannelTuner mChannelTuner;
    private PipInputManager mPipInputManager;
    private final TvOptionsManager mTvOptionsManager = new TvOptionsManager(this);
    private TvViewUiManager mTvViewUiManager;
    private TimeShiftManager mTimeShiftManager;
    private DvrManager mDvrManager;
    private Tracker mTracker;
    private final DurationTimer mMainDurationTimer = new DurationTimer();
    private final DurationTimer mTuneDurationTimer = new DurationTimer();
    private DvrSessionClient mDvrSessionClientForDebug;

    private TunableTvView mTvView;
    private TunableTvView mPipView;
    private OverlayRootView mOverlayRootView;
    private Bundle mTuneParams;
    private boolean mChannelBannerHiddenBySideFragment;
    // TODO: Move the scene views into TvTransitionManager or TvOverlayManager.
    private ChannelBannerView mChannelBannerView;
    private KeypadChannelSwitchView mKeypadChannelSwitchView;
    @Nullable
    private Uri mInitChannelUri;
    @Nullable
    private String mParentInputIdWhenScreenOff;
    private boolean mShowProgramGuide;
    private boolean mShowSelectInputView;
    private TvInputInfo mInputToSetUp;
    private final List<MemoryManageable> mMemoryManageables = new ArrayList<>();
    private MediaSession mMediaSession;
    private int mNowPlayingCardWidth;
    private int mNowPlayingCardHeight;

    private String mInputIdUnderSetup;
    private boolean mIsSetupActivityCalledByDialog;
    private AudioManager mAudioManager;
    private int mAudioFocusStatus;
    private boolean mTunePending;
    private boolean mPipEnabled;
    private Channel mPipChannel;
    private boolean mPipSwap;
    @PipSound private int mPipSound = TvSettings.PIP_SOUND_MAIN; // Default
    private boolean mDebugNonFullSizeScreen;
    private boolean mActivityResumed;
    private boolean mActivityStarted;
    private boolean mLaunchedByLauncher;
    private boolean mUseKeycodeBlacklist;
    private boolean mShowLockedChannelsTemporarily;
    private boolean mBackKeyPressed;
    private boolean mNeedShowBackKeyGuide;
    private boolean mVisibleBehind;
    private boolean mAc3PassthroughSupported;

    private boolean mIsFilmModeSet;
    private float mDefaultRefreshRate;

    private TvOverlayManager mOverlayManager;

    // mIsCurrentChannelUnblockedByUser and mWasChannelUnblockedBeforeShrunkenByUser are used for
    // keeping the channel unblocking status while TV view is shrunken.
    private boolean mIsCurrentChannelUnblockedByUser;
    private boolean mWasChannelUnblockedBeforeShrunkenByUser;
    private Channel mChannelBeforeShrunkenTvView;
    private Channel mPipChannelBeforeShrunkenTvView;
    private boolean mIsCompletingShrunkenTvView;

    // TODO: Need to consider the case that TIS explicitly request PIN code while TV view is
    // shrunken.
    private TvContentRating mLastAllowedRatingForCurrentChannel;
    private TvContentRating mAllowedRatingBeforeShrunken;

    private CaptionSettings mCaptionSettings;
    // Lazy initialization
    private boolean mLazyInitialized;

    private static final int MAX_RECENT_CHANNELS = 5;
    private final ArrayDeque<Long> mRecentChannels = new ArrayDeque<>(MAX_RECENT_CHANNELS);

    private AudioCapabilitiesReceiver mAudioCapabilitiesReceiver;
    private RecurringRunner mSendConfigInfoRecurringRunner;

    // A caller which started this activity. (e.g. TvSearch)
    private String mSource;

    private Handler mHandler = new MainActivityHandler(this);

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (DEBUG) Log.d(TAG, "Received ACTION_SCREEN_OFF");
                // We need to stop TvView, when the screen is turned off. If not and TIS uses
                // MediaPlayer, a device may not go to the sleep mode and audio can be heard,
                // because MediaPlayer keeps playing media by its wake lock.
                mInitChannelUri = mChannelTuner.getCurrentChannelUri();
                if (mChannelTuner.isCurrentChannelPassthrough()) {
                    // When ACTION_SCREEN_OFF is invoked, some CEC devices may be already
                    // removed. So we need to get the input info from ChannelTuner instead of
                    // TvInputManagerHelper.
                    TvInputInfo input = mChannelTuner.getCurrentInputInfo();
                    mParentInputIdWhenScreenOff = input.getParentId();
                    if (DEBUG) Log.d(TAG, "Parent input: " + mParentInputIdWhenScreenOff);
                }
                stopAll(true);
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (DEBUG) Log.d(TAG, "Received ACTION_SCREEN_ON");
                if (!mActivityResumed && mVisibleBehind) {
                    // ACTION_SCREEN_ON is usually called after onResume. But, if media is played
                    // under launcher with requestVisibleBehind(true), onResume will not be called.
                    // In this case, we need to resume TvView and PipView explicitly.
                    resumeTvIfNeeded();
                    resumePipIfNeeded();
                }
            } else if (intent.getAction().equals(
                    TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED)) {
                if (DEBUG) Log.d(TAG, "Received parental control settings change");
                checkChannelLockNeeded(mTvView);
                checkChannelLockNeeded(mPipView);
                applyParentalControlSettings();
            }
        }
    };

    private final OnCurrentProgramUpdatedListener mOnCurrentProgramUpdatedListener =
            new OnCurrentProgramUpdatedListener() {
        @Override
        public void onCurrentProgramUpdated(long channelId, Program program) {
            // Do not update channel banner by this notification
            // when the time shifting is available.
            if (mTimeShiftManager.isAvailable()) {
                return;
            }
            Channel channel = mTvView.getCurrentChannel();
            if (channel != null && channel.getId() == channelId) {
                updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO);
            }
        }
    };

    private final ChannelTuner.Listener mChannelTunerListener =
            new ChannelTuner.Listener() {
                @Override
                public void onLoadFinished() {
                    markNewChannelsBrowsable();
                    if (mActivityResumed) {
                        resumeTvIfNeeded();
                        resumePipIfNeeded();
                    }
                    mKeypadChannelSwitchView.setChannels(mChannelTuner.getBrowsableChannelList());
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mOverlayManager.getMenu().setChannelTuner(mChannelTuner);
                        }
                    });
                }

                @Override
                public void onBrowsableChannelListChanged() {
                    mKeypadChannelSwitchView.setChannels(mChannelTuner.getBrowsableChannelList());
                }

                @Override
                public void onCurrentChannelUnavailable(Channel channel) {
                    // TODO: handle the case that a channel is suddenly removed from DB.
                }

                @Override
                public void onChannelChanged(Channel previousChannel, Channel currentChannel) {
                }
            };

    private final Runnable mRestoreMainViewRunnable =
            new Runnable() {
                @Override
                public void run() {
                    restoreMainTvView();
                }
            };
    private ProgramGuideSearchFragment mSearchFragment;

    private void applyParentalControlSettings() {
        boolean parentalControlEnabled = mTvInputManagerHelper.getParentalControlSettings()
                .isParentalControlsEnabled();
        mTvView.onParentalControlChanged(parentalControlEnabled);
        mPipView.onParentalControlChanged(parentalControlEnabled);
        mTvOptionsManager.onParentalControlChanged(parentalControlEnabled);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG,"onCreate()");
        super.onCreate(savedInstanceState);
        TvApplication tvApplication = (TvApplication) getApplication();
        tvApplication.setMainActivity(this);

        if (Features.ONBOARDING_EXPERIENCE.isEnabled(this)
                && OnboardingUtils.needToShowOnboarding(this)
                && !TvCommonUtils.isRunningInTest()) {
            // TODO: We turn off the new onboarding for test, because tests are broken by
            // the new onboarding. We need to enable the feature for tests later.
            startActivity(OnboardingActivity.buildIntent(this, getIntent()));
            finish();
            return;
        }

        if (BuildConfig.ENG && SystemProperties.ALLOW_STRICT_MODE.getValue()) {
            Toast.makeText(this, "Using Strict Mode for eng builds", Toast.LENGTH_SHORT).show();
        }
        mTracker = tvApplication.getTracker();
        mTvInputManagerHelper = tvApplication.getTvInputManagerHelper();
        mChannelDataManager = tvApplication.getChannelDataManager();
        mProgramDataManager = tvApplication.getProgramDataManager();
        mProgramDataManager.addOnCurrentProgramUpdatedListener(Channel.INVALID_ID,
                mOnCurrentProgramUpdatedListener);
        mProgramDataManager.setPrefetchEnabled(true);
        mChannelTuner = new ChannelTuner(mChannelDataManager, mTvInputManagerHelper);
        mChannelTuner.addListener(mChannelTunerListener);
        mChannelTuner.start();
        mPipInputManager = new PipInputManager(this, mTvInputManagerHelper, mChannelTuner);
        mPipInputManager.start();
        mMemoryManageables.add(mProgramDataManager);
        mMemoryManageables.add(ImageCache.getInstance());
        mDvrManager = tvApplication.getDvrManager();

        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Point size = new Point();
        display.getSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;
        mDefaultRefreshRate = display.getRefreshRate();

        mOverlayRootView = (OverlayRootView) getLayoutInflater().inflate(
                R.layout.overlay_root_view, null, false);
        setContentView(R.layout.activity_tv);
        mTvView = (TunableTvView) findViewById(R.id.main_tunable_tv_view);
        int shrunkenTvViewHeight = getResources().getDimensionPixelSize(
                R.dimen.shrunken_tvview_height);
        mTvView.initialize((AppLayerTvView) findViewById(R.id.main_tv_view), false, screenHeight,
                shrunkenTvViewHeight);
        mTvView.setOnUnhandledInputEventListener(new OnUnhandledInputEventListener() {
            @Override
            public boolean onUnhandledInputEvent(InputEvent event) {
                if (isKeyEventBlocked()) {
                    return true;
                }
                if (event instanceof KeyEvent) {
                    KeyEvent keyEvent = (KeyEvent) event;
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.isLongPress()) {
                        if (onKeyLongPress(keyEvent.getKeyCode(), keyEvent)) {
                            return true;
                        }
                    }
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        return onKeyUp(keyEvent.getKeyCode(), keyEvent);
                    } else if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        return onKeyDown(keyEvent.getKeyCode(), keyEvent);
                    }
                }
                return false;
            }
        });
        mTimeShiftManager = new TimeShiftManager(this, mTvView, mProgramDataManager, mTracker,
                new OnCurrentProgramUpdatedListener() {
                    @Override
                    public void onCurrentProgramUpdated(long channelId, Program program) {
                        switch (mTimeShiftManager.getLastActionId()) {
                            case TimeShiftManager.TIME_SHIFT_ACTION_ID_REWIND:
                            case TimeShiftManager.TIME_SHIFT_ACTION_ID_FAST_FORWARD:
                            case TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_PREVIOUS:
                            case TimeShiftManager.TIME_SHIFT_ACTION_ID_JUMP_TO_NEXT:
                                updateChannelBannerAndShowIfNeeded(
                                        UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW);
                                break;
                            default:
                                updateChannelBannerAndShowIfNeeded(
                                        UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO);
                                break;
                        }
                    }
                });

        mPipView = (TunableTvView) findViewById(R.id.pip_tunable_tv_view);
        mPipView.initialize((AppLayerTvView) findViewById(R.id.pip_tv_view), true, screenHeight,
                shrunkenTvViewHeight);

        if (!PermissionUtils.hasAccessWatchedHistory(this)) {
            mWatchedHistoryManager = new WatchedHistoryManager(getApplicationContext());
            mWatchedHistoryManager.start();
            mTvView.setWatchedHistoryManager(mWatchedHistoryManager);
        }
        mTvViewUiManager = new TvViewUiManager(this, mTvView, mPipView,
                (FrameLayout) findViewById(android.R.id.content), mTvOptionsManager);

        mPipView.setFixedSurfaceSize(screenWidth / 2, screenHeight / 2);
        mPipView.setBlockScreenType(TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW);

        ViewGroup sceneContainer = (ViewGroup) findViewById(R.id.scene_container);
        mChannelBannerView = (ChannelBannerView) getLayoutInflater().inflate(
                R.layout.channel_banner, sceneContainer, false);
        mKeypadChannelSwitchView = (KeypadChannelSwitchView) getLayoutInflater().inflate(
                R.layout.keypad_channel_switch, sceneContainer, false);
        InputBannerView inputBannerView = (InputBannerView) getLayoutInflater()
                .inflate(R.layout.input_banner, sceneContainer, false);
        SelectInputView selectInputView = (SelectInputView) getLayoutInflater()
                .inflate(R.layout.select_input, sceneContainer, false);
        selectInputView.setOnInputSelectedCallback(new OnInputSelectedCallback() {
            @Override
            public void onTunerInputSelected() {
                Channel currentChannel = mChannelTuner.getCurrentChannel();
                if (currentChannel != null && !currentChannel.isPassthrough()) {
                    hideOverlays();
                } else {
                    tuneToLastWatchedChannelForTunerInput();
                }
            }

            @Override
            public void onPassthroughInputSelected(TvInputInfo input) {
                Channel currentChannel = mChannelTuner.getCurrentChannel();
                String currentInputId = currentChannel == null ? null : currentChannel.getInputId();
                if (TextUtils.equals(input.getId(), currentInputId)) {
                    hideOverlays();
                } else {
                    tuneToChannel(Channel.createPassthroughChannel(input.getId()));
                }
            }

            private void hideOverlays() {
                getOverlayManager().hideOverlays(
                        TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_MENU);
            }
        });
        mSearchFragment = new ProgramGuideSearchFragment();
        mOverlayManager = new TvOverlayManager(this, mChannelTuner,
                mKeypadChannelSwitchView, mChannelBannerView, inputBannerView,
                selectInputView, sceneContainer, mSearchFragment);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioFocusStatus = AudioManager.AUDIOFOCUS_LOSS;

        mMediaSession = new MediaSession(this, MEDIA_SESSION_TAG);
        mMediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                // Consume the media button event here. Should not send it to other apps.
                return true;
            }
        });
        mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mNowPlayingCardWidth = getResources().getDimensionPixelSize(
                R.dimen.notif_card_img_max_width);
        mNowPlayingCardHeight = getResources().getDimensionPixelSize(R.dimen.notif_card_img_height);

        mTvViewUiManager.restoreDisplayMode(false);
        if (!handleIntent(getIntent())) {
            finish();
            return;
        }

        mAudioCapabilitiesReceiver = new AudioCapabilitiesReceiver(this,
                new AudioCapabilitiesReceiver.OnAc3PassthroughCapabilityChangeListener() {
                    @Override
                    public void onAc3PassthroughCapabilityChange(boolean capability) {
                        mAc3PassthroughSupported = capability;
                    }
                });
        mAudioCapabilitiesReceiver.register();

        mAccessibilityManager =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        mSendConfigInfoRecurringRunner = new RecurringRunner(this, TimeUnit.DAYS.toMillis(1),
                new SendConfigInfoRunnable(mTracker, mTvInputManagerHelper));
        mSendConfigInfoRecurringRunner.start();
        initForTest();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_TV_LISTINGS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Restart live channels.
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.msg_read_tv_listing_permission_denied,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL, 0, PixelFormat.TRANSPARENT);
        windowParams.token = getWindow().getDecorView().getWindowToken();
        ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).addView(mOverlayRootView,
                windowParams);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(mOverlayRootView);
    }

    private int getDesiredBlockScreenType() {
        if (!mActivityResumed) {
            return TunableTvView.BLOCK_SCREEN_TYPE_NO_UI;
        }
        if (isUnderShrunkenTvView()) {
            return TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW;
        }
        if (mOverlayManager.needHideTextOnMainView()) {
            return TunableTvView.BLOCK_SCREEN_TYPE_NO_UI;
        }
        SafeDismissDialogFragment currentDialog = mOverlayManager.getCurrentDialog();
        if (currentDialog != null) {
            // If PIN dialog is shown for unblocking the channel lock or content ratings lock,
            // keeping the unlocking message is more natural instead of changing it.
            if (currentDialog instanceof PinDialogFragment) {
                int type = ((PinDialogFragment) currentDialog).getType();
                if (type == PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_CHANNEL
                        || type == PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_PROGRAM) {
                    return TunableTvView.BLOCK_SCREEN_TYPE_NORMAL;
                }
            }
            return TunableTvView.BLOCK_SCREEN_TYPE_NO_UI;
        }
        return TunableTvView.BLOCK_SCREEN_TYPE_NORMAL;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        mOverlayManager.getSideFragmentManager().hideAll(false);
        if (!handleIntent(intent) && !mActivityStarted) {
            // If the activity is stopped and not destroyed, finish the activity.
            // Otherwise, just ignore the intent.
            finish();
        }
    }

    @Override
    protected void onStart() {
        if (DEBUG) Log.d(TAG,"onStart()");
        super.onStart();
        mActivityStarted = true;
        mTracker.sendMainStart();
        mMainDurationTimer.start();

        applyParentalControlSettings();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mBroadcastReceiver, intentFilter);

        Intent notificationIntent = new Intent(this, NotificationService.class);
        notificationIntent.setAction(NotificationService.ACTION_SHOW_RECOMMENDATION);
        startService(notificationIntent);
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(TAG, "onResume()");
        super.onResume();
        if (!PermissionUtils.hasAccessAllEpg(this)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Toast.makeText(this, R.string.msg_not_supported_device, Toast.LENGTH_LONG).show();
                finish();
            } else if (checkSelfPermission(PERMISSION_READ_TV_LISTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{PERMISSION_READ_TV_LISTINGS},
                        PERMISSIONS_REQUEST_READ_TV_LISTINGS);
            }
        }
        mTracker.sendScreenView(SCREEN_NAME);

        SystemProperties.updateSystemProperties();
        mNeedShowBackKeyGuide = true;
        mActivityResumed = true;
        int result = mAudioManager.requestAudioFocus(MainActivity.this,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        mAudioFocusStatus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) ?
                AudioManager.AUDIOFOCUS_GAIN : AudioManager.AUDIOFOCUS_LOSS;
        setVolumeByAudioFocusStatus();

        if (mTvView.isPlaying()) {
            // Every time onResume() is called the activity will be assumed to not have requested
            // visible behind.
            requestVisibleBehind(true);
        }
        if (mChannelTuner.areAllChannelsLoaded()) {
            markNewChannelsBrowsable();
            resumeTvIfNeeded();
            resumePipIfNeeded();
        }
        mOverlayManager.showMenuWithTimeShiftPauseIfNeeded();

        // Note: The following codes are related to pop up an overlay UI after resume.
        // When the following code is changed, please check the variable
        // willShowOverlayUiAfterResume in updateChannelBannerAndShowIfNeeded.
        if (mInputToSetUp != null) {
            startSetupActivity(mInputToSetUp, false);
            mInputToSetUp = null;
        } else if (mShowProgramGuide) {
            mShowProgramGuide = false;
            mHandler.post(new Runnable() {
                // This will delay the start of the animation until after the Live Channel app is
                // shown. Without this the animation is completed before it is actually visible on
                // the screen.
                @Override
                public void run() {
                    mOverlayManager.showProgramGuide();
                }
            });
        } else if (mShowSelectInputView) {
            mShowSelectInputView = false;
            mHandler.post(new Runnable() {
                // mShowSelectInputView is true when the activity is started/resumed because the
                // TV_INPUT button was pressed in a different app.
                // This will delay the start of the animation until after the Live Channel app is
                // shown. Without this the animation is completed before it is actually visible on
                // the screen.
                @Override
                public void run() {
                    mOverlayManager.showSelectInputView();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(TAG, "onPause()");
        finishChannelChangeIfNeeded();
        mActivityResumed = false;
        mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_DEFAULT);
        mTvView.setBlockScreenType(TunableTvView.BLOCK_SCREEN_TYPE_NO_UI);
        if (mPipEnabled) {
            mTvViewUiManager.hidePipForPause();
        }
        mBackKeyPressed = false;
        mShowLockedChannelsTemporarily = false;
        mLaunchedByLauncher = false;
        if (!mVisibleBehind) {
            mAudioFocusStatus = AudioManager.AUDIOFOCUS_LOSS;
            mAudioManager.abandonAudioFocus(this);
            if (mMediaSession.isActive()) {
                mMediaSession.setActive(false);
            }
            mTracker.sendScreenView("");
        } else {
            mTracker.sendScreenView(SCREEN_BEHIND_NAME);
        }
        super.onPause();
    }

    /**
     * Returns true if {@link #onResume} is called and {@link #onPause} is not called yet.
     */
    public boolean isActivityResumed() {
        return mActivityResumed;
    }

    /**
     * Returns true if {@link #onStart} is called and {@link #onStop} is not called yet.
     */
    public boolean isActivityStarted() {
        return mActivityStarted;
    }

    @Override
    public boolean requestVisibleBehind(boolean enable) {
        boolean state = super.requestVisibleBehind(enable);
        mVisibleBehind = state;
        return state;
    }

    private void resumeTvIfNeeded() {
        if (DEBUG) Log.d(TAG, "resumeTvIfNeeded()");
        if (!mTvView.isPlaying() || mInitChannelUri != null
                || (mLaunchedByLauncher && mChannelTuner.isCurrentChannelPassthrough())) {
            if (TvContract.isChannelUriForPassthroughInput(mInitChannelUri)) {
                // The target input may not be ready yet, especially, just after screen on.
                String inputId = mInitChannelUri.getPathSegments().get(1);
                TvInputInfo input = mTvInputManagerHelper.getTvInputInfo(inputId);
                if (input == null) {
                    input = mTvInputManagerHelper.getTvInputInfo(mParentInputIdWhenScreenOff);
                    if (input == null) {
                        SoftPreconditions.checkState(false, TAG, "Input disappear." + input);
                        finish();
                    } else {
                        mInitChannelUri =
                                TvContract.buildChannelUriForPassthroughInput(input.getId());
                    }
                }
            }
            mParentInputIdWhenScreenOff = null;
            startTv(mInitChannelUri);
            mInitChannelUri = null;
        }
        // Make sure TV app has the main TV view to handle the case that TvView is used in other
        // application.
        restoreMainTvView();
        mTvView.setBlockScreenType(getDesiredBlockScreenType());
    }

    private void resumePipIfNeeded() {
        if (mPipEnabled && !(mPipView.isPlaying() && mPipView.isShown())) {
            if (mPipInputManager.areInSamePipInput(
                    mChannelTuner.getCurrentChannel(), mPipChannel)) {
                enablePipView(false, false);
            } else {
                if (!mPipView.isPlaying()) {
                    startPip(false);
                } else {
                    mTvViewUiManager.showPipForResume();
                }
            }
        }
    }

    private void markNewChannelsBrowsable() {
        SetupUtils setupUtils = SetupUtils.getInstance(MainActivity.this);
        Set<String> newInputsWithChannels = new HashSet<>();
        for (TvInputInfo input : mTvInputManagerHelper.getTvInputInfos(true, true)) {
            String inputId = input.getId();
            if (!setupUtils.isSetupDone(inputId)
                    && mChannelDataManager.getChannelCountForInput(inputId) > 0) {
                setupUtils.onSetupDone(inputId);
                newInputsWithChannels.add(inputId);
                if (DEBUG) {
                    Log.d(TAG, "New input " + inputId + " has "
                            + mChannelDataManager.getChannelCountForInput(inputId)
                            + " channels");
                }
            }
        }
        if (!newInputsWithChannels.isEmpty()) {
            for (Channel channel : mChannelDataManager.getChannelList()) {
                if (newInputsWithChannels.contains(channel.getInputId())) {
                    mChannelDataManager.updateBrowsable(channel.getId(), true);
                }
            }
            mChannelDataManager.applyUpdatedValuesToDb();
        }
    }

    private void startTv(Uri channelUri) {
        if (DEBUG) Log.d(TAG, "startTv Uri=" + channelUri);
        if ((channelUri == null || !TvContract.isChannelUriForPassthroughInput(channelUri))
                && mChannelTuner.isCurrentChannelPassthrough()) {
            // For passthrough TV input, channelUri is always given. If TV app is launched
            // by TV app icon in a launcher, channelUri is null. So if passthrough TV input
            // is playing, we stop the passthrough TV input.
            stopTv();
        }
        SoftPreconditions.checkState(TvContract.isChannelUriForPassthroughInput(channelUri)
                || mChannelTuner.areAllChannelsLoaded(),
                TAG, "startTV assumes that ChannelDataManager is already loaded.");
        if (mTvView.isPlaying()) {
            // TV has already started.
            if (channelUri == null) {
                // Simply adjust the volume without tune.
                setVolumeByAudioFocusStatus();
                return;
            }
            if (channelUri.equals(mChannelTuner.getCurrentChannelUri())) {
                // The requested channel is already tuned.
                setVolumeByAudioFocusStatus();
                return;
            }
            stopTv();
        }
        if (mChannelTuner.getCurrentChannel() != null) {
            Log.w(TAG, "The current channel should be reset before");
            mChannelTuner.resetCurrentChannel();
        }
        if (channelUri == null) {
            // If any initial channel id is not given, remember the last channel the user watched.
            long channelId = Utils.getLastWatchedChannelId(this);
            if (channelId != Channel.INVALID_ID) {
                channelUri = TvContract.buildChannelUri(channelId);
            }
        }

        if (channelUri == null) {
            mChannelTuner.moveToChannel(mChannelTuner.findNearestBrowsableChannel(0));
        } else {
            if (TvContract.isChannelUriForPassthroughInput(channelUri)) {
                Channel channel = Channel.createPassthroughChannel(channelUri);
                mChannelTuner.moveToChannel(channel);
            } else {
                long channelId = ContentUris.parseId(channelUri);
                Channel channel = mChannelDataManager.getChannel(channelId);
                if (channel == null || !mChannelTuner.moveToChannel(channel)) {
                    mChannelTuner.moveToChannel(mChannelTuner.findNearestBrowsableChannel(0));
                    Log.w(TAG, "The requested channel (id=" + channelId + ") doesn't exist. "
                            + "The first channel will be tuned to.");
                }
            }
        }

        mTvView.start(mTvInputManagerHelper);
        setVolumeByAudioFocusStatus();
        tune();
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(TAG, "onStop()");
        mActivityStarted = false;
        stopAll(false);
        unregisterReceiver(mBroadcastReceiver);
        mTracker.sendMainStop(mMainDurationTimer.reset());
        super.onStop();
    }

    private void stopAll(boolean keepVisibleBehind) {
        mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION);
        stopTv("stopAll()", keepVisibleBehind);
        stopPip();
    }

    public TvInputManagerHelper getTvInputManagerHelper() {
        return mTvInputManagerHelper;
    }

    /**
     * Starts setup activity for the given input {@code input}.
     *
     * @param calledByDialog If true, startSetupActivity is invoked from the setup dialog.
     */
    public void startSetupActivity(TvInputInfo input, boolean calledByDialog) {
        Intent intent = TvCommonUtils.createSetupIntent(input);
        if (intent == null) {
            Toast.makeText(this, R.string.msg_no_setup_activity, Toast.LENGTH_SHORT).show();
            return;
        }
        // Even though other app can handle the intent, the setup launched by Live channels
        // should go through Live channels SetupPassthroughActivity.
        intent.setComponent(new ComponentName(this, SetupPassthroughActivity.class));
        try {
            // Now we know that the user intends to set up this input. Grant permission for writing
            // EPG data.
            SetupUtils.grantEpgPermission(this, input.getServiceInfo().packageName);

            mInputIdUnderSetup = input.getId();
            mIsSetupActivityCalledByDialog = calledByDialog;
            // Call requestVisibleBehind(false) before starting other activity.
            // In Activity.requestVisibleBehind(false), this activity is scheduled to be stopped
            // immediately if other activity is about to start. And this activity is scheduled to
            // to be stopped again after onPause().
            stopTv("startSetupActivity()", false);
            startActivityForResult(intent, REQUEST_CODE_START_SETUP_ACTIVITY);
        } catch (ActivityNotFoundException e) {
            mInputIdUnderSetup = null;
            Toast.makeText(this, getString(R.string.msg_unable_to_start_setup_activity,
                    input.loadLabel(this)), Toast.LENGTH_SHORT).show();
            return;
        }
        if (calledByDialog) {
            mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION
                    | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG);
        } else {
            mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION
                    | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY);
        }
    }

    public boolean hasCaptioningSettingsActivity() {
        return Utils.isIntentAvailable(this, new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
    }

    public void startSystemCaptioningSettingsActivity() {
        Intent intent = new Intent(Settings.ACTION_CAPTIONING_SETTINGS);
        mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION
                | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY);
        try {
            startActivityForResultSafe(intent, REQUEST_CODE_START_SYSTEM_CAPTIONING_SETTINGS);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.msg_unable_to_start_system_captioning_settings),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public ChannelDataManager getChannelDataManager() {
        return mChannelDataManager;
    }

    public ProgramDataManager getProgramDataManager() {
        return mProgramDataManager;
    }

    public PipInputManager getPipInputManager() {
        return mPipInputManager;
    }

    public TvOptionsManager getTvOptionsManager() {
        return mTvOptionsManager;
    }

    public TvViewUiManager getTvViewUiManager() {
        return mTvViewUiManager;
    }

    public TimeShiftManager getTimeShiftManager() {
        return mTimeShiftManager;
    }

    /**
     * Returns the instance of {@link TvOverlayManager}.
     */
    public TvOverlayManager getOverlayManager() {
        return mOverlayManager;
    }

    public Channel getCurrentChannel() {
        return mChannelTuner.getCurrentChannel();
    }

    public long getCurrentChannelId() {
        return mChannelTuner.getCurrentChannelId();
    }

    /**
     * Returns true if the current connected TV supports AC3 passthough.
     */
    public boolean isAc3PassthroughSupported() {
        return mAc3PassthroughSupported;
    }

    /**
     * Returns the current program which the user is watching right now.<p>
     *
     * If the time shifting is available, it can be a past program.
     */
    public Program getCurrentProgram() {
        if (mTimeShiftManager.isAvailable()) {
            return mTimeShiftManager.getCurrentProgram();
        }
        return mProgramDataManager.getCurrentProgram(getCurrentChannelId());
    }

    /**
     * Returns the current playing time in milliseconds.<p>
     *
     * If the time shifting is available, the time is the playing position of the program,
     * otherwise, the system current time.
     */
    public long getCurrentPlayingPosition() {
        if (mTimeShiftManager.isAvailable()) {
            return mTimeShiftManager.getCurrentPositionMs();
        }
        return System.currentTimeMillis();
    }

    public Channel getBrowsableChannel() {
        // TODO: mChannelMap could be dirty for a while when the browsablity of channels
        // are changed. In that case, we shouldn't use the value from mChannelMap.
        Channel curChannel = mChannelTuner.getCurrentChannel();
        if (curChannel != null && curChannel.isBrowsable()) {
            return curChannel;
        } else {
            return mChannelTuner.getAdjacentBrowsableChannel(true);
        }
    }

    /**
     * Call {@link Activity#startActivity} in a safe way.
     *
     * @see LauncherActivity
     */
    public void startActivitySafe(Intent intent) {
        LauncherActivity.startActivitySafe(this, intent);
    }

    /**
     * Call {@link Activity#startActivityForResult} in a safe way.
     *
     * @see LauncherActivity
     */
    private void startActivityForResultSafe(Intent intent, int requestCode) {
        LauncherActivity.startActivityForResultSafe(this, intent, requestCode);
    }

    /**
     * Show channel sources fragment.
     */
    public void showChannelSourcesFragment() {
        if (!mChannelTuner.areAllChannelsLoaded()) {
            // Show ChannelSourcesFragment only if all the channels are loaded.
            return;
        }
        Channel currentChannel = mChannelTuner.getCurrentChannel();
        long channelId = currentChannel == null ? Channel.INVALID_ID : currentChannel.getId();
        mOverlayManager.getSideFragmentManager().show(new ChannelSourcesFragment(channelId));
    }

    // TODO: Refactor this.
    public void showParentalControlFragment() {
        mOverlayManager.showDialogFragment(PinDialogFragment.DIALOG_TAG,
                new PinDialogFragment(PinDialogFragment.PIN_DIALOG_TYPE_ENTER_PIN,
                        new PinDialogFragment.ResultListener() {
                            @Override
                            public void done(boolean success) {
                                if (success) {
                                    mOverlayManager.getSideFragmentManager()
                                            .show(new ParentalControlsFragment());
                                }
                            }
                        }), false);
    }

    /**
     * It is called when shrunken TvView is desired, such as EditChannelFragment and
     * ChannelsLockedFragment.
     */
    public void startShrunkenTvView(boolean showLockedChannelsTemporarily,
            boolean willMainViewBeTunerInput) {
        mChannelBeforeShrunkenTvView = mTvView.getCurrentChannel();
        mWasChannelUnblockedBeforeShrunkenByUser = mIsCurrentChannelUnblockedByUser;
        mAllowedRatingBeforeShrunken = mLastAllowedRatingForCurrentChannel;

        if (willMainViewBeTunerInput && mChannelTuner.isCurrentChannelPassthrough()
                && mPipEnabled) {
            mPipChannelBeforeShrunkenTvView = mPipChannel;
            enablePipView(false, false);
        } else {
            mPipChannelBeforeShrunkenTvView = null;
        }
        mTvViewUiManager.startShrunkenTvView();

        if (showLockedChannelsTemporarily) {
            mShowLockedChannelsTemporarily = true;
            checkChannelLockNeeded(mTvView);
        }

        mTvView.setBlockScreenType(getDesiredBlockScreenType());
    }

    /**
     * It is called when shrunken TvView is no longer desired, such as EditChannelFragment and
     * ChannelsLockedFragment.
     */
    public void endShrunkenTvView() {
        mTvViewUiManager.endShrunkenTvView();
        mIsCompletingShrunkenTvView = true;

        Channel returnChannel = mChannelBeforeShrunkenTvView;
        if (returnChannel == null
                || (!returnChannel.isPassthrough() && !returnChannel.isBrowsable())) {
            // Try to tune to the next best channel instead.
            returnChannel = getBrowsableChannel();
        }
        mShowLockedChannelsTemporarily = false;

        // The current channel is mTvView.getCurrentChannel() and need to tune to the returnChannel.
        if (!Objects.equals(mTvView.getCurrentChannel(), returnChannel)) {
            final Channel channel = returnChannel;
            Runnable tuneAction = new Runnable() {
                @Override
                public void run() {
                    tuneToChannel(channel);
                    if (mChannelBeforeShrunkenTvView == null
                            || !mChannelBeforeShrunkenTvView.equals(channel)) {
                        Utils.setLastWatchedChannel(MainActivity.this, channel);
                    }
                    mIsCompletingShrunkenTvView = false;
                    mIsCurrentChannelUnblockedByUser = mWasChannelUnblockedBeforeShrunkenByUser;
                    mTvView.setBlockScreenType(getDesiredBlockScreenType());
                    if (mPipChannelBeforeShrunkenTvView != null) {
                        enablePipView(true, false);
                        mPipChannelBeforeShrunkenTvView = null;
                    }
                }
            };
            mTvViewUiManager.fadeOutTvView(tuneAction);
            // Will automatically fade-in when video becomes available.
        } else {
            checkChannelLockNeeded(mTvView);
            mIsCompletingShrunkenTvView = false;
            mIsCurrentChannelUnblockedByUser = mWasChannelUnblockedBeforeShrunkenByUser;
            mTvView.setBlockScreenType(getDesiredBlockScreenType());
            if (mPipChannelBeforeShrunkenTvView != null) {
                enablePipView(true, false);
                mPipChannelBeforeShrunkenTvView = null;
            }
        }
    }

    private boolean isUnderShrunkenTvView() {
        return mTvViewUiManager.isUnderShrunkenTvView() || mIsCompletingShrunkenTvView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_START_SETUP_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    int count = mChannelDataManager.getChannelCountForInput(mInputIdUnderSetup);
                    String text;
                    if (count > 0) {
                        text = getResources().getQuantityString(R.plurals.msg_channel_added,
                                count, count);
                    } else {
                        text = getString(R.string.msg_no_channel_added);
                    }
                    Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
                    mInputIdUnderSetup = null;
                    if (mChannelTuner.getCurrentChannel() == null) {
                        mChannelTuner.moveToAdjacentBrowsableChannel(true);
                    }
                    if (mTunePending) {
                        tune();
                    }
                } else {
                    mInputIdUnderSetup = null;
                }
                if (!mIsSetupActivityCalledByDialog) {
                    mOverlayManager.getSideFragmentManager().showSidePanel(false);
                }
                break;
            case REQUEST_CODE_START_SYSTEM_CAPTIONING_SETTINGS:
                mOverlayManager.getSideFragmentManager().showSidePanel(false);
                break;
        }
        if (data != null) {
            String errorMessage = data.getStringExtra(LauncherActivity.ERROR_MESSAGE);
            if (!TextUtils.isEmpty(errorMessage)) {
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public View findViewById(int id) {
        // In order to locate fragments in non-application window, we should override findViewById.
        // Internally, Activity.findViewById is called to attach a view of a fragment into its
        // container. Without the override, we'll get crash during the fragment attachment.
        View v = mOverlayRootView != null ? mOverlayRootView.findViewById(id) : null;
        return v == null ? super.findViewById(id) : v;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (SystemProperties.LOG_KEYEVENT.getValue()) Log.d(TAG, "dispatchKeyEvent(" + event + ")");
        // If an activity is closed on a back key down event, back key down events with none zero
        // repeat count or a back key up event can be happened without the first back key down
        // event which should be ignored in this activity.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                mBackKeyPressed = true;
            }
            if (!mBackKeyPressed) {
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                mBackKeyPressed = false;
            }
        }

        // When side panel is closing, it has the focus.
        // Keep the focus, but just don't deliver the key events.
        if ((mOverlayRootView.hasFocusable()
                && !mOverlayManager.getSideFragmentManager().isHiding())
                || mOverlayManager.getSideFragmentManager().isActive()) {
            return super.dispatchKeyEvent(event);
        }
        if (BLACKLIST_KEYCODE_TO_TIS.contains(event.getKeyCode())
                || KeyEvent.isGamepadButton(event.getKeyCode())) {
            // If the event is in blacklisted or gamepad key, do not pass it to session.
            // Gamepad keys are blacklisted to support TV UIs and here's the detail.
            // If there's a TIS granted RECEIVE_INPUT_EVENT, TIF sends key events to TIS
            // and return immediately saying that the event is handled.
            // In this case, fallback key will be injected but with FLAG_CANCELED
            // while gamepads support DPAD_CENTER and BACK by fallback.
            // Since we don't expect that TIS want to handle gamepad buttons now,
            // blacklist gamepad buttons and wait for next fallback keys.
            // TODO) Need to consider other fallback keys (e.g. ESCAPE)
            return super.dispatchKeyEvent(event);
        }
        return dispatchKeyEventToSession(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        mAudioFocusStatus = focusChange;
        setVolumeByAudioFocusStatus();
    }

    /**
     * Notifies the key input focus is changed to the TV view.
     */
    public void updateKeyInputFocus() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTvView.setBlockScreenType(getDesiredBlockScreenType());
            }
        });
    }

    // It should be called before onResume.
    private boolean handleIntent(Intent intent) {
        // Reset the closed caption settings when the activity is 1)created or 2) restarted.
        // And do not reset while TvView is playing.
        if (!mTvView.isPlaying()) {
            mCaptionSettings = new CaptionSettings(this);
        }

        // Handle the passed key press, if any. Note that only the key codes that are currently
        // handled in the TV app will be handled via Intent.
        // TODO: Consider defining a separate intent filter as passing data of mime type
        // vnd.android.cursor.item/channel isn't really necessary here.
        int keyCode = intent.getIntExtra(Utils.EXTRA_KEY_KEYCODE, KeyEvent.KEYCODE_UNKNOWN);
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            if (DEBUG) Log.d(TAG, "Got an intent with keycode: " + keyCode);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            onKeyUp(keyCode, event);
            return true;
        }
        mLaunchedByLauncher = intent.getBooleanExtra(Utils.EXTRA_KEY_FROM_LAUNCHER, false);
        mInitChannelUri = null;

        String extraAction = intent.getStringExtra(Utils.EXTRA_KEY_ACTION);
        if (!TextUtils.isEmpty(extraAction)) {
            if (DEBUG) Log.d(TAG, "Got an extra action: " + extraAction);
            if (Utils.EXTRA_ACTION_SHOW_TV_INPUT.equals(extraAction)) {
                String lastWatchedChannelUri = Utils.getLastWatchedChannelUri(this);
                if (lastWatchedChannelUri != null) {
                    mInitChannelUri = Uri.parse(lastWatchedChannelUri);
                }
                mShowSelectInputView = true;
            }
        }

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            try {
                mSource = uri.getQueryParameter(Utils.PARAM_SOURCE);
            } catch (UnsupportedOperationException e) {
                // ignore this exception.
            }
            // When the URI points to the programs (directory, not an individual item), go to the
            // program guide. The intention here is to respond to
            // "content://android.media.tv/program", not "content://android.media.tv/program/XXX".
            // Later, we might want to add handling of individual programs too.
            if (Utils.isProgramsUri(uri)) {
                // The given data is a programs URI. Open the Program Guide.
                mShowProgramGuide = true;
                return true;
            }
            // In case the channel is given explicitly, use it.
            mInitChannelUri = uri;
            if (DEBUG) Log.d(TAG, "ACTION_VIEW with " + mInitChannelUri);
            if (Channels.CONTENT_URI.equals(mInitChannelUri)) {
                // Tune to default channel.
                mInitChannelUri = null;
                return true;
            }
            if ((!Utils.isChannelUriForOneChannel(mInitChannelUri)
                    && !Utils.isChannelUriForInput(mInitChannelUri))) {
                Log.w(TAG, "Malformed channel uri " + mInitChannelUri
                        + " tuning to default instead");
                mInitChannelUri = null;
                return true;
            }
            mTuneParams = intent.getExtras();
            if (mTuneParams == null) {
                mTuneParams = new Bundle();
            }
            if (Utils.isChannelUriForTunerInput(mInitChannelUri)) {
                long channelId = ContentUris.parseId(mInitChannelUri);
                mTuneParams.putLong(KEY_INIT_CHANNEL_ID, channelId);
            } else if (TvContract.isChannelUriForPassthroughInput(mInitChannelUri)) {
                // If mInitChannelUri is for a passthrough TV input.
                String inputId = mInitChannelUri.getPathSegments().get(1);
                TvInputInfo input = mTvInputManagerHelper.getTvInputInfo(inputId);
                if (input == null) {
                    mInitChannelUri = null;
                    Toast.makeText(this, R.string.msg_no_specific_input, Toast.LENGTH_SHORT).show();
                    return false;
                } else if (!input.isPassthroughInput()) {
                    mInitChannelUri = null;
                    Toast.makeText(this, R.string.msg_not_passthrough_input, Toast.LENGTH_SHORT)
                            .show();
                    return false;
                }
            } else if (mInitChannelUri != null) {
                // Handle the URI built by TvContract.buildChannelsUriForInput().
                // TODO: Change hard-coded "input" to TvContract.PARAM_INPUT.
                String inputId = mInitChannelUri.getQueryParameter("input");
                long channelId = Utils.getLastWatchedChannelIdForInput(this, inputId);
                if (channelId == Channel.INVALID_ID) {
                    String[] projection = { Channels._ID };
                    try (Cursor cursor = getContentResolver().query(uri, projection,
                            null, null, null)) {
                        if (cursor != null && cursor.moveToNext()) {
                            channelId = cursor.getLong(0);
                        }
                    }
                }
                if (channelId == Channel.INVALID_ID) {
                    // Couldn't find any channel probably because the input hasn't been set up.
                    // Try to set it up.
                    mInitChannelUri = null;
                    mInputToSetUp = mTvInputManagerHelper.getTvInputInfo(inputId);
                } else {
                    mInitChannelUri = TvContract.buildChannelUri(channelId);
                    mTuneParams.putLong(KEY_INIT_CHANNEL_ID, channelId);
                }
            }
        }
        return true;
    }

    private void setVolumeByAudioFocusStatus() {
        if (mPipSound == TvSettings.PIP_SOUND_MAIN) {
            setVolumeByAudioFocusStatus(mTvView);
        } else { // mPipSound == TvSettings.PIP_SOUND_PIP_WINDOW
            setVolumeByAudioFocusStatus(mPipView);
        }
    }

    private void setVolumeByAudioFocusStatus(TunableTvView tvView) {
        if (tvView.isPlaying()) {
            switch (mAudioFocusStatus) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    tvView.setStreamVolume(AUDIO_MAX_VOLUME);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    tvView.setStreamVolume(AUDIO_MIN_VOLUME);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    tvView.setStreamVolume(AUDIO_DUCKING_VOLUME);
                    break;
            }
        }
    }

    private void stopTv() {
        stopTv(null, false);
    }

    private void stopTv(String logForCaller, boolean keepVisibleBehind) {
        if (logForCaller != null) {
            Log.i(TAG, "stopTv is called at " + logForCaller + ".");
        } else {
            if (DEBUG) Log.d(TAG, "stopTv()");
        }
        if (mTvView.isPlaying()) {
            mTvView.stop();
            if (!keepVisibleBehind) {
                requestVisibleBehind(false);
            }
            mAudioManager.abandonAudioFocus(this);
            if (mMediaSession.isActive()) {
                mMediaSession.setActive(false);
            }
        }
        mChannelTuner.resetCurrentChannel();
        mTunePending = false;
    }

    private boolean isPlaying() {
        return mTvView.isPlaying() && mTvView.getCurrentChannel() != null;
    }

    private void startPip(final boolean fromUserInteraction) {
        if (mPipChannel == null) {
            Log.w(TAG, "PIP channel id is an invalid id.");
            return;
        }
        if (DEBUG) Log.d(TAG, "startPip() " + mPipChannel);
        mPipView.start(mTvInputManagerHelper);
        boolean success = mPipView.tuneTo(mPipChannel, null, new OnTuneListener() {
            @Override
            public void onUnexpectedStop(Channel channel) {
                Log.w(TAG, "The PIP is Unexpectedly stopped");
                enablePipView(false, false);
            }

            @Override
            public void onTuneFailed(Channel channel) {
                Log.w(TAG, "Fail to start the PIP during channel tuning");
                if (fromUserInteraction) {
                    Toast.makeText(MainActivity.this, R.string.msg_no_pip_support,
                            Toast.LENGTH_SHORT).show();
                    enablePipView(false, false);
                }
            }

            @Override
            public void onStreamInfoChanged(StreamInfo info) {
                mTvViewUiManager.updatePipView();
                mHandler.removeCallbacks(mRestoreMainViewRunnable);
                restoreMainTvView();
            }

            @Override
            public void onChannelRetuned(Uri channel) {
                if (channel == null) {
                    return;
                }
                Channel currentChannel =
                        mChannelDataManager.getChannel(ContentUris.parseId(channel));
                if (currentChannel == null) {
                    Log.e(TAG, "onChannelRetuned is called from PIP input but can't find a channel"
                            + " with the URI " + channel);
                    return;
                }
                if (isChannelChangeKeyDownReceived()) {
                    // Ignore this message if the user is changing the channel.
                    return;
                }
                mPipChannel = currentChannel;
                mPipView.setCurrentChannel(mPipChannel);
            }

            @Override
            public void onContentBlocked() {
                updateMediaSession();
            }

            @Override
            public void onContentAllowed() {
                updateMediaSession();
            }
        });
        if (!success) {
            Log.w(TAG, "Fail to start the PIP");
            return;
        }
        if (fromUserInteraction) {
            checkChannelLockNeeded(mPipView);
        }
        // Explicitly make the PIP view main to make the selected input an HDMI-CEC active source.
        mPipView.setMain();
        scheduleRestoreMainTvView();
        mTvViewUiManager.onPipStart();
        mPipView.setStreamVolume(AUDIO_MIN_VOLUME);
    }

    private void scheduleRestoreMainTvView() {
        mHandler.removeCallbacks(mRestoreMainViewRunnable);
        mHandler.postDelayed(mRestoreMainViewRunnable, TVVIEW_SET_MAIN_TIMEOUT_MS);
    }

    private void stopPip() {
        if (DEBUG) Log.d(TAG, "stopPip");
        if (mPipView.isPlaying()) {
            mPipView.stop();
            mPipSwap = false;
            mTvViewUiManager.onPipStop();
        }
    }

    /**
     * Says {@code text} when accessibility is turned on.
     */
    public void sendAccessibilityText(String text) {
        if (mAccessibilityManager.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setClassName(getClass().getName());
            event.setPackageName(getPackageName());
            event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            event.getText().add(text);
            mAccessibilityManager.sendAccessibilityEvent(event);
        }
    }

    private void tune() {
        if (DEBUG) Log.d(TAG, "tune()");
        mTuneDurationTimer.start();

        lazyInitializeIfNeeded(LAZY_INITIALIZATION_DELAY);

        // Prerequisites to be able to tune.
        if (mInputIdUnderSetup != null) {
            mTunePending = true;
            return;
        }
        mTunePending = false;
        if (!mChannelTuner.isCurrentChannelPassthrough()
                && mTvInputManagerHelper.getTunerTvInputSize() == 0) {
            Toast.makeText(this, R.string.msg_no_input, Toast.LENGTH_SHORT).show();
            // TODO: Direct the user to a Play Store landing page for TvInputService apps.
            finish();
            return;
        }
        SetupUtils setupUtils = SetupUtils.getInstance(this);
        if (!mChannelTuner.isCurrentChannelPassthrough() && setupUtils.isFirstTune()) {
            if (!mChannelTuner.areAllChannelsLoaded()) {
                // tune() will be called, once all channels are loaded.
                stopTv("tune()", false);
                return;
            }
            if (mChannelDataManager.getChannelCount() > 0) {
                mOverlayManager.showIntroDialog();
            } else {
                mOverlayManager.showSetupDialog();
                return;
            }
        }
        if (!mChannelTuner.isCurrentChannelPassthrough()
                && mChannelTuner.getBrowsableChannelCount() == 0
                && mChannelDataManager.getChannelCount() > 0
                && !mOverlayManager.getSideFragmentManager().isActive()) {
            if (!mChannelTuner.areAllChannelsLoaded()) {
                return;
            }
            if (mTvInputManagerHelper.getTunerTvInputSize() == 1) {
                mOverlayManager.getSideFragmentManager().show(new CustomizeChannelListFragment());
            } else {
                showChannelSourcesFragment();
            }
            return;
        }
        // TODO: need to refactor the following code to put in startTv.
        final Channel channel = mChannelTuner.getCurrentChannel();
        if (channel == null) {
            // There is no channel to tune to.
            stopTv("tune()", false);
            if (!mChannelDataManager.isDbLoadFinished()) {
                // Wait until channel data is loaded in order to know the number of channels.
                // tune() will be retried, once the channel data is loaded.
                return;
            }
            if (mOverlayManager.getSideFragmentManager().isActive()) {
                return;
            }
            mOverlayManager.showSetupDialog();
            return;
        }

        if (!channel.isPassthrough()) {
            setupUtils.onTuned();
            if (mTuneParams != null) {
                Long initChannelId = mTuneParams.getLong(KEY_INIT_CHANNEL_ID);
                if (initChannelId == channel.getId()) {
                    mTuneParams.remove(KEY_INIT_CHANNEL_ID);
                } else {
                    mTuneParams = null;
                }
            }
        }

        mIsCurrentChannelUnblockedByUser = false;
        if (!isUnderShrunkenTvView()) {
            mLastAllowedRatingForCurrentChannel = null;
        }
        final boolean wasUnderShrunkenTvView = isUnderShrunkenTvView();
        final long streamInfoUpdateTimeThresholdMs =
                System.currentTimeMillis() + FIRST_STREAM_INFO_UPDATE_DELAY_MILLIS;
        mHandler.removeMessages(MSG_UPDATE_CHANNEL_BANNER_BY_INFO_UPDATE);
        if (mAccessibilityManager.isEnabled()) {
            // For every tune, we need to inform the tuned channel or input to a user,
            // if Talkback is turned on.
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setClassName(getClass().getName());
            event.setPackageName(getPackageName());
            event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            if (TvContract.isChannelUriForPassthroughInput(channel.getUri())) {
                TvInputInfo input = mTvInputManagerHelper.getTvInputInfo(channel.getInputId());
                event.getText().add(Utils.loadLabel(this, input));
            } else if (TextUtils.isEmpty(channel.getDisplayName())) {
                event.getText().add(channel.getDisplayNumber());
            } else {
                event.getText().add(channel.getDisplayNumber() + " " + channel.getDisplayName());
            }
            mAccessibilityManager.sendAccessibilityEvent(event);
        }

        boolean success = mTvView.tuneTo(channel, mTuneParams, new OnTuneListener() {
            boolean mUnlockAllowedRatingBeforeShrunken = true;

            @Override
            public void onUnexpectedStop(Channel channel) {
                stopTv();
                startTv(null);
            }

            @Override
            public void onTuneFailed(Channel channel) {
                Log.w(TAG, "Failed to tune to channel " + channel.getId()
                        + "@" + channel.getInputId());
                if (mTvView.isFadedOut()) {
                    mTvView.removeFadeEffect();
                }
                // TODO: show something to user about this error.
            }

            @Override
            public void onStreamInfoChanged(StreamInfo info) {
                if (info.isVideoAvailable() && mTuneDurationTimer.isRunning()) {
                    mTracker.sendChannelTuneTime(info.getCurrentChannel(),
                            mTuneDurationTimer.reset());
                }
                // If updateChannelBanner() is called without delay, the stream info seems flickering
                // when the channel is quickly changed.
                if (!mHandler.hasMessages(MSG_UPDATE_CHANNEL_BANNER_BY_INFO_UPDATE)
                        && info.isVideoAvailable()) {
                    if (System.currentTimeMillis() > streamInfoUpdateTimeThresholdMs) {
                        updateChannelBannerAndShowIfNeeded(
                                UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO);
                    } else {
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(
                                MSG_UPDATE_CHANNEL_BANNER_BY_INFO_UPDATE),
                                streamInfoUpdateTimeThresholdMs - System.currentTimeMillis());
                    }
                }

                applyDisplayRefreshRate(info.getVideoFrameRate());
                mTvViewUiManager.updateTvView();
                applyMultiAudio();
                applyClosedCaption();
                // TODO: Send command to TIS with checking the settings in TV and CaptionManager.
                mOverlayManager.getMenu().onStreamInfoChanged();
                if (mTvView.isVideoAvailable()) {
                    mTvViewUiManager.fadeInTvView();
                }
                mHandler.removeCallbacks(mRestoreMainViewRunnable);
                restoreMainTvView();
            }

            @Override
            public void onChannelRetuned(Uri channel) {
                if (channel == null) {
                    return;
                }
                Channel currentChannel =
                        mChannelDataManager.getChannel(ContentUris.parseId(channel));
                if (currentChannel == null) {
                    Log.e(TAG, "onChannelRetuned is called but can't find a channel with the URI "
                            + channel);
                    return;
                }
                if (isChannelChangeKeyDownReceived()) {
                    // Ignore this message if the user is changing the channel.
                    return;
                }
                mChannelTuner.setCurrentChannel(currentChannel);
                mTvView.setCurrentChannel(currentChannel);
                updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_TUNE);
            }

            @Override
            public void onContentBlocked() {
                mTuneDurationTimer.reset();
                TvContentRating rating = mTvView.getBlockedContentRating();
                // When tuneTo was called while TV view was shrunken, if the channel id is the same
                // with the channel watched before shrunken, we allow the rating which was allowed
                // before.
                if (wasUnderShrunkenTvView && mUnlockAllowedRatingBeforeShrunken
                        && mChannelBeforeShrunkenTvView.equals(channel)
                        && rating.equals(mAllowedRatingBeforeShrunken)) {
                    mUnlockAllowedRatingBeforeShrunken = isUnderShrunkenTvView();
                    mTvView.requestUnblockContent(rating);
                }

                updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK);
                mTvViewUiManager.fadeInTvView();
            }

            @Override
            public void onContentAllowed() {
                if (!isUnderShrunkenTvView()) {
                    mUnlockAllowedRatingBeforeShrunken = false;
                }
                updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK);
            }
        });
        mTuneParams = null;
        if (!success) {
            Toast.makeText(this, R.string.msg_tune_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // Explicitly make the TV view main to make the selected input an HDMI-CEC active source.
        mTvView.setMain();
        scheduleRestoreMainTvView();
        if (!isUnderShrunkenTvView()) {
            if (!channel.isPassthrough()) {
                addToRecentChannels(channel.getId());
            }
            Utils.setLastWatchedChannel(this, channel);
        }
        checkChannelLockNeeded(mTvView);
        updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_TUNE);
        if (mActivityResumed) {
            // requestVisibleBehind should be called after onResume() is called. But, when
            // launcher is over the TV app and the screen is turned off and on, tune() can
            // be called during the pause state by mBroadcastReceiver (Intent.ACTION_SCREEN_ON).
            requestVisibleBehind(true);
        }
        updateMediaSession();
    }

    private void updateMediaSession() {
        if (getCurrentChannel() == null) {
            mMediaSession.setActive(false);
            return;
        }

        // If the channel is blocked, display a lock and a short text on the Now Playing Card
        if (mTvView.isScreenBlocked() || mTvView.getBlockedContentRating() != null) {
            setMediaSessionPlaybackState(false);

            Bitmap art = BitmapFactory.decodeResource(
                    getResources(), R.drawable.ic_message_lock_preview);
            updateMediaMetadata(
                    getResources().getString(R.string.channel_banner_locked_channel_title), art);
            mMediaSession.setActive(true);
            return;
        }

        Program program = getCurrentProgram();
        String cardTitleText = program == null ? null : program.getTitle();
        if (TextUtils.isEmpty(cardTitleText)) {
            cardTitleText = getCurrentChannel().getDisplayName();
        }
        updateMediaMetadata(cardTitleText, null);
        setMediaSessionPlaybackState(true);

        if (program != null && program.getPosterArtUri() != null) {
            program.loadPosterArt(MainActivity.this, mNowPlayingCardWidth, mNowPlayingCardHeight,
                    new Program.LoadPosterArtCallback() {
                        @Override
                        public void onLoadPosterArtFinished(Program program, Bitmap posterArt) {
                            if (program != getCurrentProgram() || getCurrentChannel() == null) {
                                return;
                            }

                            if (posterArt != null) {
                                String cardTitleText = program == null ? null : program.getTitle();
                                if (TextUtils.isEmpty(cardTitleText)) {
                                    cardTitleText = getCurrentChannel().getDisplayName();
                                }
                                updateMediaMetadata(cardTitleText, posterArt);
                            } else {
                                updateMediaMetadataWithAlternativeArt(program);
                            }
                        }
                    });
        } else {
            updateMediaMetadataWithAlternativeArt(program);
        }

        mMediaSession.setActive(true);
    }

    private void updateMediaMetadata(String title, Bitmap posterArt) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        builder.putString(MediaMetadata.METADATA_KEY_TITLE, title);
        if (posterArt != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, posterArt);
        }
        mMediaSession.setMetadata(builder.build());
    }

    private void updateMediaMetadataWithAlternativeArt(final Program program) {
        Channel channel = getCurrentChannel();
        if (channel == null || program != getCurrentProgram()) {
            return;
        }

        String cardTitleText = program == null ? null : program.getTitle();
        if (TextUtils.isEmpty(cardTitleText)) {
            cardTitleText = channel.getDisplayName();
        }

        Bitmap posterArt = BitmapFactory.decodeResource(
                getResources(), R.drawable.default_now_card);
        updateMediaMetadata(cardTitleText, posterArt);
    }

    private void setMediaSessionPlaybackState(boolean isPlaying) {
        PlaybackState.Builder builder = new PlaybackState.Builder();
        builder.setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_STOPPED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                isPlaying ? MEDIA_SESSION_PLAYING_SPEED : MEDIA_SESSION_STOPPED_SPEED);
        mMediaSession.setPlaybackState(builder.build());
    }

    private void addToRecentChannels(long channelId) {
        if (!mRecentChannels.remove(channelId)) {
            if (mRecentChannels.size() >= MAX_RECENT_CHANNELS) {
                mRecentChannels.removeLast();
            }
        }
        mRecentChannels.addFirst(channelId);
        mOverlayManager.getMenu().onRecentChannelsChanged();
    }

    /**
     * Returns the recently tuned channels.
     */
    public ArrayDeque<Long> getRecentChannels() {
        return mRecentChannels;
    }

    private void checkChannelLockNeeded(TunableTvView tvView) {
        Channel channel = tvView.getCurrentChannel();
        if (tvView.isPlaying() && channel != null) {
            if (getParentalControlSettings().isParentalControlsEnabled()
                    && channel.isLocked()
                    && !mShowLockedChannelsTemporarily
                    && !(isUnderShrunkenTvView()
                            && channel.equals(mChannelBeforeShrunkenTvView)
                            && mWasChannelUnblockedBeforeShrunkenByUser)) {
                if (DEBUG) Log.d(TAG, "Channel " + channel.getId() + " is locked");
                blockScreen(tvView);
            } else {
                unblockScreen(tvView);
            }
        }
    }

    private void blockScreen(TunableTvView tvView) {
        tvView.blockScreen();
        if (tvView == mTvView) {
            updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK);
            updateMediaSession();
        }
    }

    private void unblockScreen(TunableTvView tvView) {
        tvView.unblockScreen();
        if (tvView == mTvView) {
            updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK);
            updateMediaSession();
        }
    }

    /**
     * Shows the channel banner if it was hidden from the side fragment.
     *
     * <p>When the side fragment is visible, showing the channel banner should be put off until the
     * side fragment is closed even though the channel changes.
     */
    public void showChannelBannerIfHiddenBySideFragment() {
        if (mChannelBannerHiddenBySideFragment) {
            updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW);
        }
    }

    private void updateChannelBannerAndShowIfNeeded(@ChannelBannerUpdateReason int reason) {
        if(DEBUG) Log.d(TAG, "updateChannelBannerAndShowIfNeeded(reason=" + reason + ")");
        if (!mChannelTuner.isCurrentChannelPassthrough()) {
            int lockType = ChannelBannerView.LOCK_NONE;
            if (mTvView.isScreenBlocked()) {
                lockType = ChannelBannerView.LOCK_CHANNEL_INFO;
            } else if (mTvView.getBlockedContentRating() != null
                    || (getParentalControlSettings().isParentalControlsEnabled()
                            && !mTvView.isVideoAvailable())) {
                // If the parental control is enabled, do not show the program detail until the
                // video becomes available.
                lockType = ChannelBannerView.LOCK_PROGRAM_DETAIL;
            }
            if (lockType == ChannelBannerView.LOCK_NONE) {
                if (reason == UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST) {
                    // Do not show detailed program information while fast-tuning.
                    lockType = ChannelBannerView.LOCK_PROGRAM_DETAIL;
                } else if (reason == UPDATE_CHANNEL_BANNER_REASON_TUNE
                        && getParentalControlSettings().isParentalControlsEnabled()) {
                    // If parental control is turned on,
                    // assumes that program is locked by default and waits for onContentAllowed.
                    lockType = ChannelBannerView.LOCK_PROGRAM_DETAIL;
                }
            }
            // If lock type is not changed, we don't need to update channel banner by parental
            // control.
            if (!mChannelBannerView.setLockType(lockType)
                    && reason == UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK) {
                return;
            }

            mChannelBannerView.updateViews(mTvView);
        }
        boolean needToShowBanner = (reason == UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW
                || reason == UPDATE_CHANNEL_BANNER_REASON_TUNE
                || reason == UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST);
        boolean noOverlayUiWhenResume =
                mInputToSetUp == null && !mShowProgramGuide && !mShowSelectInputView;
        if (needToShowBanner && noOverlayUiWhenResume
                && mOverlayManager.getCurrentDialog() == null) {
            if (mChannelTuner.getCurrentChannel() == null) {
                mChannelBannerHiddenBySideFragment = false;
            } else if (mOverlayManager.getSideFragmentManager().isActive()) {
                mChannelBannerHiddenBySideFragment = true;
            } else {
                mChannelBannerHiddenBySideFragment = false;
                mOverlayManager.showBanner();
            }
        }
        updateAvailabilityToast();
    }

    /**
     * Hide the overlays when tuning to a channel from the menu (e.g. Channels).
     */
    public void hideOverlaysForTune() {
        mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SCENE);
    }

    public boolean needToKeepDialogWhenHidingOverlay() {
        return mInputIdUnderSetup != null && mIsSetupActivityCalledByDialog;
    }

    // For now, this only takes care of 24fps.
    private void applyDisplayRefreshRate(float videoFrameRate) {
        boolean is24Fps = Math.abs(videoFrameRate - FRAME_RATE_FOR_FILM) < FRAME_RATE_EPSILON;
        if (mIsFilmModeSet && !is24Fps) {
            setPreferredRefreshRate(mDefaultRefreshRate);
            mIsFilmModeSet = false;
        } else if (!mIsFilmModeSet && is24Fps) {
            DisplayManager displayManager = (DisplayManager) getSystemService(
                    Context.DISPLAY_SERVICE);
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

            float[] refreshRates = display.getSupportedRefreshRates();
            for (float refreshRate : refreshRates) {
                // Be conservative and set only when the display refresh rate supports 24fps.
                if (Math.abs(videoFrameRate - refreshRate) < REFRESH_RATE_EPSILON) {
                    setPreferredRefreshRate(refreshRate);
                    mIsFilmModeSet = true;
                    return;
                }
            }
        }
    }

    private void setPreferredRefreshRate(float refreshRate) {
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.preferredRefreshRate = refreshRate;
        window.setAttributes(layoutParams);
    }

    private void applyMultiAudio() {
        List<TvTrackInfo> tracks = getTracks(TvTrackInfo.TYPE_AUDIO);
        if (tracks == null) {
            mTvOptionsManager.onMultiAudioChanged(null);
            return;
        }

        String id = TvSettings.getMultiAudioId(this);
        String language = TvSettings.getMultiAudioLanguage(this);
        int channelCount = TvSettings.getMultiAudioChannelCount(this);
        TvTrackInfo bestTrack = TvTrackInfoUtils
                .getBestTrackInfo(tracks, id, language, channelCount);
        if (bestTrack != null) {
            String selectedTrack = getSelectedTrack(TvTrackInfo.TYPE_AUDIO);
            if (!bestTrack.getId().equals(selectedTrack)) {
                selectTrack(TvTrackInfo.TYPE_AUDIO, bestTrack);
            } else {
                mTvOptionsManager.onMultiAudioChanged(
                        Utils.getMultiAudioString(this, bestTrack, false));
            }
            return;
        }
        mTvOptionsManager.onMultiAudioChanged(null);
    }

    private void applyClosedCaption() {
        List<TvTrackInfo> tracks = getTracks(TvTrackInfo.TYPE_SUBTITLE);
        if (tracks == null) {
            mTvOptionsManager.onClosedCaptionsChanged(null);
            return;
        }

        boolean enabled = mCaptionSettings.isEnabled();
        mTvView.setClosedCaptionEnabled(enabled);

        String selectedTrackId = getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE);
        TvTrackInfo alternativeTrack = null;
        if (enabled) {
            String language = mCaptionSettings.getLanguage();
            String trackId = mCaptionSettings.getTrackId();
            for (TvTrackInfo track : tracks) {
                if (Utils.isEqualLanguage(track.getLanguage(), language)) {
                    if (track.getId().equals(trackId)) {
                        if (!track.getId().equals(selectedTrackId)) {
                            selectTrack(TvTrackInfo.TYPE_SUBTITLE, track);
                        } else {
                            // Already selected. Update the option string only.
                            mTvOptionsManager.onClosedCaptionsChanged(track);
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Subtitle Track Selected {id=" + track.getId()
                                    + ", language=" + track.getLanguage() + "}");
                        }
                        return;
                    } else if (alternativeTrack == null) {
                        alternativeTrack = track;
                    }
                }
            }
            if (alternativeTrack != null) {
                if (!alternativeTrack.getId().equals(selectedTrackId)) {
                    selectTrack(TvTrackInfo.TYPE_SUBTITLE, alternativeTrack);
                } else {
                    mTvOptionsManager.onClosedCaptionsChanged(alternativeTrack);
                }
                if (DEBUG) {
                    Log.d(TAG, "Subtitle Track Selected {id=" + alternativeTrack.getId()
                            + ", language=" + alternativeTrack.getLanguage() + "}");
                }
                return;
            }
        }
        if (selectedTrackId != null) {
            selectTrack(TvTrackInfo.TYPE_SUBTITLE, null);
            if (DEBUG) Log.d(TAG, "Subtitle Track Unselected");
            return;
        }
        mTvOptionsManager.onClosedCaptionsChanged(null);
    }

    /**
     * Pops up the KeypadChannelSwitchView with the given key input event.
     *
     * @param keyCode A key code of the key event.
     */
    public void showKeypadChannelSwitchView(int keyCode) {
        if (mChannelTuner.areAllChannelsLoaded()) {
            mOverlayManager.showKeypadChannelSwitch();
            mKeypadChannelSwitchView.onNumberKeyUp(keyCode - KeyEvent.KEYCODE_0);
        }
    }

    public void showSearchActivity() {
        // HACK: Once we moved the window layer to TYPE_APPLICATION_SUB_PANEL,
        // the voice button doesn't work. So we directly call the voice action.
        SearchManagerHelper.getInstance(this).launchAssistAction();
    }

    public void showProgramGuideSearchFragment() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.search, mSearchFragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Do not save instance state because restoring instance state when TV app died
        // unexpectedly can cause some problems like initializing fragments duplicately and
        // accessing resource before it is initialized.
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        if (mChannelTuner != null) {
            mChannelTuner.removeListener(mChannelTunerListener);
            mChannelTuner.stop();
        }
        TvApplication application = ((TvApplication) getApplication());
        if (mProgramDataManager != null) {
            mProgramDataManager.removeOnCurrentProgramUpdatedListener(
                    Channel.INVALID_ID, mOnCurrentProgramUpdatedListener);
            if (application.isCurrentMainActivity(this)) {
                mProgramDataManager.setPrefetchEnabled(false);
            }
        }
        if (mPipInputManager != null) {
            mPipInputManager.stop();
        }
        if (mOverlayManager != null) {
            mOverlayManager.release();
        }
        if (mKeypadChannelSwitchView != null) {
            mKeypadChannelSwitchView.setChannels(null);
        }
        mMemoryManageables.clear();
        if (mMediaSession != null) {
            mMediaSession.release();
        }
        if (mAudioCapabilitiesReceiver != null) {
            mAudioCapabilitiesReceiver.unregister();
        }
        mHandler.removeCallbacksAndMessages(null);
        if (application.isCurrentMainActivity(this)) {
            application.setMainActivity(null);
        }
        if (mSendConfigInfoRecurringRunner != null) {
            mSendConfigInfoRecurringRunner.stop();
            mSendConfigInfoRecurringRunner = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (SystemProperties.LOG_KEYEVENT.getValue()) {
            Log.d(TAG, "onKeyDown(" + keyCode + ", " + event + ")");
        }
        switch (mOverlayManager.onKeyDown(keyCode, event)) {
            case KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY:
                return super.onKeyDown(keyCode, event);
            case KEY_EVENT_HANDLER_RESULT_HANDLED:
                return true;
            case KEY_EVENT_HANDLER_RESULT_NOT_HANDLED:
                return false;
            case KEY_EVENT_HANDLER_RESULT_PASSTHROUGH:
            default:
                // pass through
        }
        if (mSearchFragment.isVisible()) {
            return super.onKeyDown(keyCode, event);
        }
        if (!mChannelTuner.areAllChannelsLoaded()) {
            return false;
        }
        if (!mChannelTuner.isCurrentChannelPassthrough()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (event.getRepeatCount() == 0
                            && mChannelTuner.getBrowsableChannelCount() > 0) {
                        moveToAdjacentChannel(true, false);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CHANNEL_UP_PRESSED,
                                System.currentTimeMillis()), CHANNEL_CHANGE_INITIAL_DELAY_MILLIS);
                        mTracker.sendChannelUp();
                    }
                    return true;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (event.getRepeatCount() == 0
                            && mChannelTuner.getBrowsableChannelCount() > 0) {
                        moveToAdjacentChannel(false, false);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CHANNEL_DOWN_PRESSED,
                                System.currentTimeMillis()), CHANNEL_CHANGE_INITIAL_DELAY_MILLIS);
                        mTracker.sendChannelDown();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        /*
         * The following keyboard keys map to these remote keys or "debug actions"
         *  - --------
         *  A KEYCODE_MEDIA_AUDIO_TRACK
         *  D debug: show debug options
         *  E updateChannelBannerAndShowIfNeeded
         *  I KEYCODE_TV_INPUT
         *  O debug: show display mode option
         *  P debug: togglePipView
         *  R KEYCODE_MEDIA_STOP debug: dvr stop recording
         *  S KEYCODE_CAPTIONS: select subtitle
         *  V KEYCODE_MEDIA_RECORD debug: dvr start recording
         *  W debug: toggle screen size
         *  Z KEYCODE_PROG_RED debug: create program data for current channel
         */
        if (SystemProperties.LOG_KEYEVENT.getValue()) {
            Log.d(TAG, "onKeyUp(" + keyCode + ", " + event + ")");
        }
        // If we are in the middle of channel change, finish it before showing overlays.
        finishChannelChangeIfNeeded();

        if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
            showSearchActivity();
            return true;
        }
        switch (mOverlayManager.onKeyUp(keyCode, event)) {
            case KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY:
                return super.onKeyUp(keyCode, event);
            case KEY_EVENT_HANDLER_RESULT_HANDLED:
                return true;
            case KEY_EVENT_HANDLER_RESULT_NOT_HANDLED:
                return false;
            case KEY_EVENT_HANDLER_RESULT_PASSTHROUGH:
            default:
                // pass through
        }
        if (mSearchFragment.isVisible()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // When the event is from onUnhandledInputEvent, onBackPressed is not automatically
            // called. Therefore, we need to explicitly call onBackPressed().
            onBackPressed();
            return true;
        }

        if (!mChannelTuner.areAllChannelsLoaded()) {
            // Now channel map is under loading.
        } else if (mChannelTuner.getBrowsableChannelCount() == 0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_E:
                case KeyEvent.KEYCODE_MENU:
                    showChannelSourcesFragment();
                    return true;
            }
        } else {
            if (KeypadChannelSwitchView.isChannelNumberKey(keyCode)) {
                showKeypadChannelSwitchView(keyCode);
                return true;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!PermissionUtils.hasModifyParentalControls(this)) {
                        // TODO: support this feature for non-system LC app. b/23939816
                        return true;
                    }
                    PinDialogFragment dialog = null;
                    if (mTvView.isScreenBlocked()) {
                        dialog = new PinDialogFragment(
                                PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_CHANNEL,
                                new PinDialogFragment.ResultListener() {
                                    @Override
                                    public void done(boolean success) {
                                        if (success) {
                                            unblockScreen(mTvView);
                                            mIsCurrentChannelUnblockedByUser = true;
                                        }
                                    }
                                });
                    } else if (mTvView.getBlockedContentRating() != null) {
                        final TvContentRating rating = mTvView.getBlockedContentRating();
                        dialog = new PinDialogFragment(
                                PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_PROGRAM,
                                new PinDialogFragment.ResultListener() {
                                    @Override
                                    public void done(boolean success) {
                                        if (success) {
                                            mLastAllowedRatingForCurrentChannel = rating;
                                            mTvView.requestUnblockContent(rating);
                                        }
                                    }
                                });
                    }
                    if (dialog != null) {
                        mOverlayManager.showDialogFragment(PinDialogFragment.DIALOG_TAG, dialog,
                                false);
                    }
                    return true;

                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_E:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_MENU:
                    if (event.isCanceled()) {
                        // Ignore canceled key.
                        // Note that if there's a TIS granted RECEIVE_INPUT_EVENT,
                        // fallback keys not blacklisted will have FLAG_CANCELED.
                        // See dispatchKeyEvent() for detail.
                        return true;
                    }
                    if (keyCode != KeyEvent.KEYCODE_MENU) {
                        updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW);
                    }
                    if (keyCode != KeyEvent.KEYCODE_E) {
                        mOverlayManager.showMenu(Menu.REASON_NONE);
                    }
                    return true;
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // Channel change is already done in the head of this method.
                    return true;
                case KeyEvent.KEYCODE_S:
                    if (!SystemProperties.USE_DEBUG_KEYS.getValue()) {
                        break;
                    }
                case KeyEvent.KEYCODE_CAPTIONS: {
                    mOverlayManager.getSideFragmentManager().show(new ClosedCaptionFragment());
                    return true;
                }
                case KeyEvent.KEYCODE_A:
                    if (!SystemProperties.USE_DEBUG_KEYS.getValue()) {
                        break;
                    }
                case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK: {
                    mOverlayManager.getSideFragmentManager().show(new MultiAudioFragment());
                    return true;
                }
                case KeyEvent.KEYCODE_GUIDE: {
                    mOverlayManager.showProgramGuide();
                    return true;
                }
                case KeyEvent.KEYCODE_INFO: {
                    mOverlayManager.showBanner();
                    return true;
                }
            }
        }
        if (SystemProperties.USE_DEBUG_KEYS.getValue()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_W: {
                    mDebugNonFullSizeScreen = !mDebugNonFullSizeScreen;
                    if (mDebugNonFullSizeScreen) {
                        FrameLayout.LayoutParams params =
                                (FrameLayout.LayoutParams) mTvView.getLayoutParams();
                        params.width = 960;
                        params.height = 540;
                        params.gravity = Gravity.START;
                        mTvView.setLayoutParams(params);
                    } else {
                        FrameLayout.LayoutParams params =
                                (FrameLayout.LayoutParams) mTvView.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.gravity = Gravity.CENTER;
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
                    mOverlayManager.getSideFragmentManager().show(new DisplayModeFragment());
                    return true;
                }

                case KeyEvent.KEYCODE_D:
                    mOverlayManager.getSideFragmentManager().show(new DebugOptionFragment());
                    return true;

                case KeyEvent.KEYCODE_MEDIA_RECORD: // TODO(DVR) handle with debug_keys set
                case KeyEvent.KEYCODE_V: {
                    if (mDvrSessionClientForDebug != null) {
                        mDvrSessionClientForDebug.release();
                        mDvrSessionClientForDebug = null;
                    }
                    mDvrSessionClientForDebug = new DvrSessionClient(MainActivity.this);
                    Channel dvrChannel = null;
                    for (Channel channel : mChannelDataManager.getBrowsableChannelList()) {
                        if (channel.getInputId().equals(DVR_TEST_INPUT_ID)) {
                            dvrChannel = channel;
                            break;
                        }
                    }
                    if (dvrChannel == null) {
                        return true;
                    }
                    final Channel channel = dvrChannel;
                    mDvrSessionClientForDebug.connect(DVR_TEST_INPUT_ID, new DvrSessionClient.Callback() {
                        @Override
                        public void onConnected() {
                            mDvrSessionClientForDebug.startRecord(channel.getUri(), channel.getUri());
                        }
                    });
                    return true;
                }
                case KeyEvent.KEYCODE_MEDIA_STOP: // TODO(DVR) handle with debug_keys set
                case KeyEvent.KEYCODE_R:
                    if (mDvrSessionClientForDebug == null) {
                        return true;
                    }
                    mDvrSessionClientForDebug.stopRecord();
                    mDvrSessionClientForDebug.release();
                    mDvrSessionClientForDebug = null;
                    return true;
                case KeyEvent.KEYCODE_PROG_RED:
                case KeyEvent.KEYCODE_Z: {
                    Channel channel = mTvView.getCurrentChannel();
                    long channelId = channel.getId();
                    Program p = mProgramDataManager.getCurrentProgram(channelId);
                    if (p == null) {
                        long now = System.currentTimeMillis();
                        mDvrManager.addSchedule(channel, now, now + TimeUnit.MINUTES.toMillis(5));
                    } else {
                        mDvrManager.addSchedule(p);
                    }
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (SystemProperties.LOG_KEYEVENT.getValue()) Log.d(TAG, "onKeyLongPress(" + event);
        if (USE_BACK_KEY_LONG_PRESS) {
            // Treat the BACK key long press as the normal press since we changed the behavior in
            // onBackPressed().
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // It takes long time for TV app to finish, so stop TV first.
                stopAll(false);
                super.onBackPressed();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        // The activity should be returned to the caller of this activity
        // when the mSource is not null.
        if (!mOverlayManager.getSideFragmentManager().isActive() && isPlaying()
                && mSource == null) {
            // If back key would exit TV app,
            // show McLauncher instead so we can get benefit of McLauncher's shyMode.
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startActivity(startMain);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (mOverlayManager != null) {
            mOverlayManager.onUserInteraction();
        }
    }

    public void togglePipView() {
        enablePipView(!mPipEnabled, true);
        mOverlayManager.getMenu().update();
    }

    public boolean isPipEnabled() {
        return mPipEnabled;
    }

    public void tuneToChannelForPip(Channel channel) {
        if (!mPipEnabled) {
            throw new IllegalStateException("tuneToChannelForPip is called when PIP is off");
        }
        if (mPipChannel.equals(channel)) {
            return;
        }
        mPipChannel = channel;
        startPip(true);
    }

    public void enablePipView(boolean enable, boolean fromUserInteraction) {
        if (enable == mPipEnabled) {
            return;
        }
        if (enable) {
            List<PipInput> pipAvailableInputs = mPipInputManager.getPipInputList(true);
            if (pipAvailableInputs.isEmpty()) {
                Toast.makeText(this, R.string.msg_no_available_input_by_pip, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            // TODO: choose the last pip input.
            Channel pipChannel = pipAvailableInputs.get(0).getChannel();
            if (pipChannel != null) {
                mPipEnabled = true;
                mPipChannel = pipChannel;
                startPip(fromUserInteraction);
                mTvViewUiManager.restorePipSize();
                mTvViewUiManager.restorePipLayout();
                mTvOptionsManager.onPipChanged(mPipEnabled);
            } else {
                Toast.makeText(this, R.string.msg_no_available_input_by_pip, Toast.LENGTH_SHORT)
                        .show();
            }
        } else {
            mPipEnabled = false;
            mPipChannel = null;
            // Recover the stream volume of the main TV view, if needed.
            if (mPipSound == TvSettings.PIP_SOUND_PIP_WINDOW) {
                setVolumeByAudioFocusStatus(mTvView);
                mPipView.setStreamVolume(AUDIO_MIN_VOLUME);
                mPipSound = TvSettings.PIP_SOUND_MAIN;
                mTvOptionsManager.onPipSoundChanged(mPipSound);
            }
            stopPip();
            mTvViewUiManager.restoreDisplayMode(false);
            mTvOptionsManager.onPipChanged(mPipEnabled);
        }
    }

    private boolean isChannelChangeKeyDownReceived() {
        return mHandler.hasMessages(MSG_CHANNEL_UP_PRESSED)
                || mHandler.hasMessages(MSG_CHANNEL_DOWN_PRESSED);
    }

    private void finishChannelChangeIfNeeded() {
        if (!isChannelChangeKeyDownReceived()) {
            return;
        }
        mHandler.removeMessages(MSG_CHANNEL_UP_PRESSED);
        mHandler.removeMessages(MSG_CHANNEL_DOWN_PRESSED);
        if (mChannelTuner.getBrowsableChannelCount() > 0) {
            if (!mTvView.isPlaying()) {
                // We expect that mTvView is already played. But, it is sometimes not.
                // TODO: we figure out the reason when mTvView is not played.
                Log.w(TAG, "TV view isn't played in finishChannelChangeIfNeeded");
            }
            tuneToChannel(mChannelTuner.getCurrentChannel());
        } else {
            showChannelSourcesFragment();
        }
    }

    private boolean dispatchKeyEventToSession(final KeyEvent event) {
        if (SystemProperties.LOG_KEYEVENT.getValue()) {
            Log.d(TAG, "dispatchKeyEventToSession(" + event + ")");
        }
        if (mPipEnabled && mChannelTuner.isCurrentChannelPassthrough()) {
            // If PIP is enabled, key events will be used by UI.
            return false;
        }
        boolean handled = false;
        if (mTvView != null) {
            handled = mTvView.dispatchKeyEvent(event);
        }
        if (isKeyEventBlocked()) {
            if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK
                    || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) && mNeedShowBackKeyGuide) {
                // KeyEvent.KEYCODE_BUTTON_B is also used like the back button.
                Toast.makeText(this, R.string.msg_back_key_guide, Toast.LENGTH_SHORT).show();
                mNeedShowBackKeyGuide = false;
            }
            return true;
        }
        return handled;
    }

    private boolean isKeyEventBlocked() {
        // If the current channel is passthrough channel without a PIP view,
        // we always don't handle the key events in TV activity. Instead, the key event will
        // be handled by the passthrough TV input.
        return mChannelTuner.isCurrentChannelPassthrough() && !mPipEnabled;
    }

    public void tuneToLastWatchedChannelForTunerInput() {
        if (!mChannelTuner.isCurrentChannelPassthrough()) {
            return;
        }
        if (mPipEnabled) {
            if (!mPipChannel.isPassthrough()) {
                enablePipView(false, true);
            }
        }
        stopTv();
        startTv(null);
    }

    public void tuneToChannel(Channel channel) {
        if (channel == null) {
            if (mTvView.isPlaying()) {
                mTvView.reset();
            }
        } else {
            if (mPipEnabled && mPipInputManager.areInSamePipInput(channel, mPipChannel)) {
                enablePipView(false, true);
            }
            if (!mTvView.isPlaying()) {
                startTv(channel.getUri());
            } else if (channel.equals(mTvView.getCurrentChannel())) {
                updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_TUNE);
            } else if (mChannelTuner.moveToChannel(channel)) {
                // Channel banner would be updated inside of tune.
                tune();
            } else {
                showChannelSourcesFragment();
            }
        }
    }

    /**
     * This method just moves the channel in the channel map and updates the channel banner,
     * but doesn't actually tune to the channel.
     * The caller of this method should call tune() in the end.
     *
     * @param channelUp {@code true} for channel up, and {@code false} for channel down.
     * @param fastTuning {@code true} if fast tuning is requested.
     */
    private void moveToAdjacentChannel(boolean channelUp, boolean fastTuning) {
        if (mChannelTuner.moveToAdjacentBrowsableChannel(channelUp)) {
            updateChannelBannerAndShowIfNeeded(fastTuning ? UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST
                    : UPDATE_CHANNEL_BANNER_REASON_TUNE);
        }
    }

    public Channel getPipChannel() {
        return mPipChannel;
    }

    /**
     * Swap the main and the sub screens while in the PIP mode.
     */
    public void swapPip() {
        if (!mPipEnabled || mTvView == null || mPipView == null) {
            Log.e(TAG, "swapPip() - not in PIP");
            mPipSwap = false;
            return;
        }

        Channel channel = mTvView.getCurrentChannel();
        boolean tvViewBlocked = mTvView.isScreenBlocked();
        boolean pipViewBlocked = mPipView.isScreenBlocked();
        if (channel == null || !mTvView.isPlaying()) {
            // If the TV view is not currently playing or its current channel is null, swapping here
            // basically means disabling the PIP mode and getting back to the full screen since
            // there's no point of keeping a blank PIP screen at the bottom which is not tune-able.
            enablePipView(false, true);
            mOverlayManager.hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_DEFAULT);
            mPipSwap = false;
            return;
        }

        // Reset the TV view and tune the PIP view to the previous channel of the TV view.
        mTvView.reset();
        mPipView.reset();
        Channel oldPipChannel = mPipChannel;
        tuneToChannelForPip(channel);
        if (tvViewBlocked) {
            mPipView.blockScreen();
        } else {
            mPipView.unblockScreen();
        }

        if (oldPipChannel != null) {
            // Tune the TV view to the previous PIP channel.
            tuneToChannel(oldPipChannel);
        }
        if (pipViewBlocked) {
            mTvView.blockScreen();
        } else {
            mTvView.unblockScreen();
        }
        if (mPipSound == TvSettings.PIP_SOUND_MAIN) {
            setVolumeByAudioFocusStatus(mTvView);
            mPipView.setStreamVolume(AUDIO_MIN_VOLUME);
        } else { // mPipSound == TvSettings.PIP_SOUND_PIP_WINDOW
            setVolumeByAudioFocusStatus(mPipView);
            mTvView.setStreamVolume(AUDIO_MIN_VOLUME);
        }
        mPipSwap = !mPipSwap;
        mTvOptionsManager.onPipSwapChanged(mPipSwap);
    }

    /**
     * Toggle where the sound is coming from when the user is watching the PIP.
     */
    public void togglePipSoundMode() {
        if (!mPipEnabled || mTvView == null || mPipView == null) {
            Log.e(TAG, "togglePipSoundMode() - not in PIP");
            return;
        }
        if (mPipSound == TvSettings.PIP_SOUND_MAIN) {
            setVolumeByAudioFocusStatus(mPipView);
            mTvView.setStreamVolume(AUDIO_MIN_VOLUME);
            mPipSound = TvSettings.PIP_SOUND_PIP_WINDOW;
        } else { // mPipSound == TvSettings.PIP_SOUND_PIP_WINDOW
            setVolumeByAudioFocusStatus(mTvView);
            mPipView.setStreamVolume(AUDIO_MIN_VOLUME);
            mPipSound = TvSettings.PIP_SOUND_MAIN;
        }
        restoreMainTvView();
        mTvOptionsManager.onPipSoundChanged(mPipSound);
    }

    /**
     * Set the main TV view which holds HDMI-CEC active source based on the sound mode
     */
    private void restoreMainTvView() {
        if (mPipSound == TvSettings.PIP_SOUND_MAIN) {
            mTvView.setMain();
        } else { // mPipSound == TvSettings.PIP_SOUND_PIP_WINDOW
            mPipView.setMain();
        }
    }

    @Override
    public void onVisibleBehindCanceled() {
        stopTv("onVisibleBehindCanceled()", false);
        mTracker.sendScreenView("");
        mAudioFocusStatus = AudioManager.AUDIOFOCUS_LOSS;
        mAudioManager.abandonAudioFocus(this);
        if (mMediaSession.isActive()) {
            mMediaSession.setActive(false);
        }
        stopPip();
        mVisibleBehind = false;
        super.onVisibleBehindCanceled();
    }

    public List<TvTrackInfo> getTracks(int type) {
        return mTvView.getTracks(type);
    }

    public String getSelectedTrack(int type) {
        return mTvView.getSelectedTrack(type);
    }

    public void selectTrack(int type, TvTrackInfo track) {
        mTvView.selectTrack(type, track == null ? null : track.getId());
        if (type == TvTrackInfo.TYPE_AUDIO) {
            mTvOptionsManager.onMultiAudioChanged(track == null ? null :
                    Utils.getMultiAudioString(this, track, false));
        } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
            mTvOptionsManager.onClosedCaptionsChanged(track);
        }
    }

    public void selectAudioTrack(String trackId) {
        saveMultiAudioSetting(trackId);
        applyMultiAudio();
    }

    private void saveMultiAudioSetting(String trackId) {
        List<TvTrackInfo> tracks = getTracks(TvTrackInfo.TYPE_AUDIO);
        if (tracks != null) {
            for (TvTrackInfo track : tracks) {
                if (track.getId().equals(trackId)) {
                    TvSettings.setMultiAudioId(this, track.getId());
                    TvSettings.setMultiAudioLanguage(this, track.getLanguage());
                    TvSettings.setMultiAudioChannelCount(this, track.getAudioChannelCount());
                    return;
                }
            }
        }
        TvSettings.setMultiAudioId(this, null);
        TvSettings.setMultiAudioLanguage(this, null);
        TvSettings.setMultiAudioChannelCount(this, 0);
    }

    public void selectSubtitleTrack(int option, String trackId) {
        saveClosedCaptionSetting(option, trackId);
        applyClosedCaption();
    }

    public void selectSubtitleLanguage(int option, String language, String trackId) {
        mCaptionSettings.setEnableOption(option);
        mCaptionSettings.setLanguage(language);
        mCaptionSettings.setTrackId(trackId);
        applyClosedCaption();
    }

    private void saveClosedCaptionSetting(int option, String trackId) {
        mCaptionSettings.setEnableOption(option);
        if (option == CaptionSettings.OPTION_ON) {
            List<TvTrackInfo> tracks = getTracks(TvTrackInfo.TYPE_SUBTITLE);
            if (tracks != null) {
                for (TvTrackInfo track : tracks) {
                    if (track.getId().equals(trackId)) {
                        mCaptionSettings.setLanguage(track.getLanguage());
                        mCaptionSettings.setTrackId(trackId);
                        return;
                    }
                }
            }
        }
    }

    private void updateAvailabilityToast() {
        updateAvailabilityToast(mTvView);
    }

    private void updateAvailabilityToast(StreamInfo info) {
        if (info.isVideoAvailable()) {
            return;
        }

        int stringId;
        switch (info.getVideoUnavailableReason()) {
            case TunableTvView.VIDEO_UNAVAILABLE_REASON_NOT_TUNED:
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING:
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING:
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY:
                return;
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL:
                stringId = R.string.msg_channel_unavailable_weak_signal;
                break;
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN:
            default:
                stringId = R.string.msg_channel_unavailable_unknown;
                break;
        }

        Toast.makeText(this, stringId, Toast.LENGTH_SHORT).show();
    }

    public ParentalControlSettings getParentalControlSettings() {
        return mTvInputManagerHelper.getParentalControlSettings();
    }

    /**
     * Returns a ContentRatingsManager instance.
     */
    public ContentRatingsManager getContentRatingsManager() {
        return mTvInputManagerHelper.getContentRatingsManager();
    }

    public CaptionSettings getCaptionSettings() {
        return mCaptionSettings;
    }

    // Initialize TV app for test. The setup process should be finished before the Live TV app is
    // started. We only enable all the channels here.
    private void initForTest() {
        if (!TvCommonUtils.isRunningInTest()) {
            return;
        }

        Utils.enableAllChannels(this);
    }

    // Lazy initialization
    private void lazyInitializeIfNeeded(long delay) {
        // Already initialized.
        if (mLazyInitialized) {
            return;
        }
        mLazyInitialized = true;
        // Running initialization.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initAnimations();
                initSideFragments();
            }
        }, delay);
    }

    private void initAnimations() {
        mTvViewUiManager.initAnimatorIfNeeded();
        mOverlayManager.initAnimatorIfNeeded();
    }

    private void initSideFragments() {
        SideFragment.preloadRecycledViews(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        for (MemoryManageable memoryManageable : mMemoryManageables) {
            memoryManageable.performTrimMemory(level);
        }
    }

    private static class MainActivityHandler extends WeakHandler<MainActivity> {
        MainActivityHandler(MainActivity mainActivity) {
            super(mainActivity);
        }

        @Override
        protected void handleMessage(Message msg, @NonNull MainActivity mainActivity) {
            switch (msg.what) {
                case MSG_CHANNEL_DOWN_PRESSED:
                    long startTime = (Long) msg.obj;
                    mainActivity.moveToAdjacentChannel(false, true);
                    sendMessageDelayed(Message.obtain(msg), getDelay(startTime));
                    break;
                case MSG_CHANNEL_UP_PRESSED:
                    startTime = (Long) msg.obj;
                    mainActivity.moveToAdjacentChannel(true, true);
                    sendMessageDelayed(Message.obtain(msg), getDelay(startTime));
                    break;
                case MSG_UPDATE_CHANNEL_BANNER_BY_INFO_UPDATE:
                    mainActivity.updateChannelBannerAndShowIfNeeded(
                            UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO);
                    break;
            }
        }

        private long getDelay(long startTime) {
            if (System.currentTimeMillis() - startTime > CHANNEL_CHANGE_NORMAL_SPEED_DURATION_MS) {
                return CHANNEL_CHANGE_DELAY_MS_IN_MAX_SPEED;
            }
            return CHANNEL_CHANGE_DELAY_MS_IN_NORMAL_SPEED;
        }
    }
}

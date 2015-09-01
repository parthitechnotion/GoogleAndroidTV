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

package com.android.tv.ui;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;

import com.android.tv.ChannelTuner;
import com.android.tv.MainActivity;
import com.android.tv.MainActivity.KeyHandlerResultType;
import com.android.tv.R;
import com.android.tv.TimeShiftManager;
import com.android.tv.TvApplication;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.WeakHandler;
import com.android.tv.dialog.FullscreenDialogFragment;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dialog.RecentlyWatchedDialogFragment;
import com.android.tv.dialog.SafeDismissDialogFragment;
import com.android.tv.guide.ProgramGuide;
import com.android.tv.menu.Menu;
import com.android.tv.menu.Menu.MenuShowReason;
import com.android.tv.menu.MenuRowFactory;
import com.android.tv.menu.MenuView;
import com.android.tv.search.ProgramGuideSearchFragment;
import com.android.tv.ui.TvTransitionManager.SceneType;
import com.android.tv.ui.sidepanel.AboutFragment;
import com.android.tv.ui.sidepanel.SideFragmentManager;
import com.android.tv.ui.sidepanel.parentalcontrols.RatingsFragment;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

/**
 * A class responsible for the life cycle and event handling of the pop-ups over TV view.
 */
// TODO: Put TvTransitionManager into this class.
public class TvOverlayManager {
    private static final String TAG = "TvOverlayManager";
    private static final boolean DEBUG = false;
    public static final String SETUP_TRACKER_LABEL = "Setup dialog";
    public static final String INTRO_TRACKER_LABEL = "Intro dialog";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {FLAG_HIDE_OVERLAYS_DEFAULT, FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION,
                    FLAG_HIDE_OVERLAYS_KEEP_SCENE, FLAG_HIDE_OVERLAYS_KEEP_DIALOG,
                    FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS, FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY,
                    FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE, FLAG_HIDE_OVERLAYS_KEEP_MENU})
    public @interface HideOverlayFlag {}
    // FLAG_HIDE_OVERLAYs must be bitwise exclusive.
    public static final int FLAG_HIDE_OVERLAYS_DEFAULT =                 0b00000000;
    public static final int FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION =       0b00000010;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_SCENE =              0b00000100;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_DIALOG =             0b00001000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS =        0b00010000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY = 0b00100000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE =      0b01000000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_MENU =               0b10000000;

    public static final int MSG_SHOW_DIALOG = 1000;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {OVERLAY_TYPE_NONE, OVERLAY_TYPE_MENU, OVERLAY_TYPE_SIDE_FRAGMENT,
                    OVERLAY_TYPE_DIALOG, OVERLAY_TYPE_GUIDE, OVERLAY_TYPE_SCENE_CHANNEL_BANNER,
                    OVERLAY_TYPE_SCENE_INPUT_BANNER, OVERLAY_TYPE_SCENE_KEYPAD_CHANNEL_SWITCH,
                    OVERLAY_TYPE_SCENE_SELECT_INPUT})
    private @interface TvOverlayType {}
    // OVERLAY_TYPEs must be bitwise exclusive.
    private static final int OVERLAY_TYPE_NONE =                        0b00000000;
    private static final int OVERLAY_TYPE_MENU =                        0b00000001;
    private static final int OVERLAY_TYPE_SIDE_FRAGMENT =               0b00000010;
    private static final int OVERLAY_TYPE_DIALOG =                      0b00000100;
    private static final int OVERLAY_TYPE_GUIDE =                       0b00001000;
    private static final int OVERLAY_TYPE_SCENE_CHANNEL_BANNER =        0b00010000;
    private static final int OVERLAY_TYPE_SCENE_INPUT_BANNER =          0b00100000;
    private static final int OVERLAY_TYPE_SCENE_KEYPAD_CHANNEL_SWITCH = 0b01000000;
    private static final int OVERLAY_TYPE_SCENE_SELECT_INPUT =          0b10000000;

    private static final Set<String> AVAILABLE_DIALOG_TAGS = new HashSet<>();
    static {
        AVAILABLE_DIALOG_TAGS.add(RecentlyWatchedDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(PinDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(FullscreenDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(AboutFragment.LicenseActionItem.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(RatingsFragment.AttributionItem.DIALOG_TAG);
    }

    private final MainActivity mMainActivity;
    private final ChannelTuner mChannelTuner;
    private final TvTransitionManager mTransitionManager;
    private final Menu mMenu;
    private final SideFragmentManager mSideFragmentManager;
    private final ProgramGuide mProgramGuide;
    private final KeypadChannelSwitchView mKeypadChannelSwitchView;
    private final SelectInputView mSelectInputView;
    private final ProgramGuideSearchFragment mSearchFragment;
    private final Tracker mTracker;
    private SafeDismissDialogFragment mCurrentDialog;
    private final Handler mHandler = new TvOverlayHandler(this);

    private @TvOverlayType int mOpenedOverlays;

    public TvOverlayManager(MainActivity mainActivity, ChannelTuner channelTuner,
            KeypadChannelSwitchView keypadChannelSwitchView,
            ChannelBannerView channelBannerView, InputBannerView inputBannerView,
            SelectInputView selectInputView, ViewGroup sceneContainer,
            ProgramGuideSearchFragment searchFragment) {
        mMainActivity = mainActivity;
        mChannelTuner = channelTuner;
        mKeypadChannelSwitchView = keypadChannelSwitchView;
        mSelectInputView = selectInputView;
        mSearchFragment = searchFragment;
        mTracker = ((TvApplication) mainActivity.getApplication()).getTracker();
        mTransitionManager = new TvTransitionManager(mainActivity, sceneContainer,
                channelBannerView, inputBannerView, mKeypadChannelSwitchView, selectInputView);
        mTransitionManager.setListener(new TvTransitionManager.Listener() {
            @Override
            public void onSceneChanged(int fromScene, int toScene) {
                // Call notifyOverlayOpened first so that the listener can know that a new scene
                // will be opened when the notifyOverlayClosed is called.
                if (toScene != TvTransitionManager.SCENE_TYPE_EMPTY) {
                    onOverlayOpened(convertSceneToOverlayType(toScene));
                }
                if (fromScene != TvTransitionManager.SCENE_TYPE_EMPTY) {
                    onOverlayClosed(convertSceneToOverlayType(fromScene));
                }
            }
        });
        // Menu
        MenuView menuView = (MenuView) mainActivity.findViewById(R.id.menu);
        mMenu = new Menu(mainActivity, menuView, new MenuRowFactory(mainActivity),
                new Menu.OnMenuVisibilityChangeListener() {
                    @Override
                    public void onMenuVisibilityChange(boolean visible) {
                        if (visible) {
                            onOverlayOpened(OVERLAY_TYPE_MENU);
                        } else {
                            onOverlayClosed(OVERLAY_TYPE_MENU);
                        }
                    }
                });
        // Side Fragment
        mSideFragmentManager = new SideFragmentManager(mainActivity,
                new Runnable() {
                    @Override
                    public void run() {
                        onOverlayOpened(OVERLAY_TYPE_SIDE_FRAGMENT);
                        hideOverlays(FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        mMainActivity.showChannelBannerIfHiddenBySideFragment();
                        onOverlayClosed(OVERLAY_TYPE_SIDE_FRAGMENT);
                    }
                });
        // Program Guide
        mProgramGuide = new ProgramGuide(mainActivity, channelTuner,
                mainActivity.getTvInputManagerHelper(), mainActivity.getChannelDataManager(),
                mainActivity.getProgramDataManager(),
                ((TvApplication) mainActivity.getApplication()).getTracker(),
                new Runnable() {
                    @Override
                    public void run() {
                        onOverlayOpened(OVERLAY_TYPE_GUIDE);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        onOverlayClosed(OVERLAY_TYPE_GUIDE);
                    }
                });
    }

    /**
     * A method to release all the allocated resources or unregister listeners.
     * This is called from {@link MainActivity#onDestroy}.
     */
    public void release() {
        mMenu.release();
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Returns the instance of {@link Menu}.
     */
    public Menu getMenu() {
        return mMenu;
    }

    /**
     * Returns the instance of {@link SideFragmentManager}.
     */
    public SideFragmentManager getSideFragmentManager() {
        return mSideFragmentManager;
    }

    /**
     * Returns the currently opened dialog.
     */
    public SafeDismissDialogFragment getCurrentDialog() {
        return mCurrentDialog;
    }

    /**
     * Returns the instance of {@link ProgramGuide}.
     */
    public ProgramGuide getProgramGuide() {
        return mProgramGuide;
    }

    /**
     * Shows the main menu.
     */
    public void showMenu(@MenuShowReason int reason) {
        if (mChannelTuner != null && mChannelTuner.areAllChannelsLoaded()) {
            mMenu.show(reason);
        }
    }

    /**
     * Shows the play controller of the menu if the playback is paused.
     */
    public boolean showMenuWithTimeShiftPauseIfNeeded() {
        if (mMainActivity.getTimeShiftManager().isPaused()) {
            showMenu(Menu.REASON_PLAY_CONTROLS_PAUSE);
            return true;
        }
        return false;
    }

    /**
     * Shows the given dialog.
     */
    public void showDialogFragment(String tag, SafeDismissDialogFragment dialog,
            boolean keepSidePanelHistory) {
        showDialogFragment(tag, dialog, keepSidePanelHistory, 0);
    }

    /**
     * Shows the given dialog with a delay {@code delayMillis}.
     */
    public void showDialogFragment(String tag, SafeDismissDialogFragment dialog,
            boolean keepSidePanelHistory, long delayMillis) {
        int flags = FLAG_HIDE_OVERLAYS_KEEP_DIALOG;
        if (keepSidePanelHistory) {
            flags |= FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY;
        }
        hideOverlays(flags);
        // A tag for dialog must be added to AVAILABLE_DIALOG_TAGS to make it launchable from TV.
        if (!AVAILABLE_DIALOG_TAGS.contains(tag)) {
            return;
        }

        // TODO: Consider showing multiple dialog at once.
        if (mCurrentDialog != null && mCurrentDialog.isAdded()) {
            return;
        }

        mCurrentDialog = dialog;
        if (delayMillis == 0) {
            dialog.show(mMainActivity.getFragmentManager(), tag);
        } else {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SHOW_DIALOG, tag), delayMillis);
        }

        // Calling this from SafeDismissDialogFragment.onCreated() might be late
        // because it takes time for onCreated to be called
        // and next key events can be handled by MainActivity, not Dialog.
        onOverlayOpened(OVERLAY_TYPE_DIALOG);
    }

    /**
     * Shows setup dialog.
     */
    public void showSetupDialog() {
        showSetupDialog(0);
    }

    /**
     * Shows setup dialog with a delay {@code delayMillis}.
     */
    public void showSetupDialog(long delayMillis) {
        if (DEBUG) Log.d(TAG,"showSetupDialog");
        showDialogFragment(FullscreenDialogFragment.DIALOG_TAG,
                new FullscreenDialogFragment(R.layout.setup_dialog, SETUP_TRACKER_LABEL), false,
                delayMillis);
    }

    /**
     * Shows intro dialog.
     */
    public void showIntroDialog() {
        if (DEBUG) Log.d(TAG,"showIntroDialog");
        showDialogFragment(FullscreenDialogFragment.DIALOG_TAG,
                new FullscreenDialogFragment(R.layout.intro_dialog, INTRO_TRACKER_LABEL), false);
    }

    /**
     * Shows recently watched dialog.
     */
    public void showRecentlyWatchedDialog() {
        showDialogFragment(RecentlyWatchedDialogFragment.DIALOG_TAG,
                new RecentlyWatchedDialogFragment(), false);
    }

    /**
     * Shows banner view.
     */
    public void showBanner() {
        mTransitionManager.goToChannelBannerScene();
    }

    public void showKeypadChannelSwitch() {
        hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SCENE
                | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG);
        mTransitionManager.goToKeypadChannelSwitchScene();
    }

    /**
     * Shows select input view.
     */
    public void showSelectInputView() {
        hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SCENE);
        mTransitionManager.goToSelectInputScene();
    }

    /**
     * Initializes animators if animators are not initialized yet.
     */
    public void initAnimatorIfNeeded() {
        mTransitionManager.initIfNeeded();
    }

    /**
     * It is called when a SafeDismissDialogFragment is destroyed.
     */
    public void onDialogDestroyed() {
        mCurrentDialog = null;
        onOverlayClosed(OVERLAY_TYPE_DIALOG);
    }

    /**
     * Shows the program guide.
     */
    public void showProgramGuide() {
        mProgramGuide.show(new Runnable() {
            @Override
            public void run() {
                hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE);
            }
        });
    }

    /**
     * Hides all the opened overlays according to the flags.
     */
    // TODO: Add test for this method.
    public void hideOverlays(@HideOverlayFlag int flags) {
        if (mMainActivity.needToKeepDialogWhenHidingOverlay()) {
            flags |= FLAG_HIDE_OVERLAYS_KEEP_DIALOG;
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_DIALOG) != 0) {
            // Keeps the dialog.
        } else {
            if (mCurrentDialog != null) {
                if (mCurrentDialog instanceof PinDialogFragment) {
                    // The result listener of PinDialogFragment could call MenuView when
                    // the dialog is dismissed. In order not to call it, set the result listener
                    // to null.
                    ((PinDialogFragment) mCurrentDialog).setResultListener(null);
                }
                if (mHandler.hasMessages(MSG_SHOW_DIALOG)) {
                    mHandler.removeMessages(MSG_SHOW_DIALOG);
                    onDialogDestroyed();
                } else {
                    mCurrentDialog.dismiss();
                }
            }
            mCurrentDialog = null;
        }

        boolean withAnimation = (flags & FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION) == 0;

        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_MENU) != 0) {
            // Keeps the menu.
        } else {
            mMenu.hide(withAnimation);
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_SCENE) != 0) {
            // Keeps the current scene.
        } else {
            mTransitionManager.goToEmptyScene(withAnimation);
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS) != 0) {
            // Keeps side panels.
        } else if (mSideFragmentManager.isSidePanelVisible()) {
            if ((flags & FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY) != 0) {
                mSideFragmentManager.hideSidePanel(withAnimation);
            } else {
                mSideFragmentManager.hideAll(withAnimation);
            }
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE) != 0) {
            // Keep the program guide.
        } else {
            mProgramGuide.hide();
        }
    }

    /**
     * Returns true, if a main view needs to hide informational text. Specifically, when overlay
     * UIs except banner is shown, the informational text needs to be hidden for clean UI.
     */
    public boolean needHideTextOnMainView() {
        return getSideFragmentManager().isActive()
                || getMenu().isActive()
                || mTransitionManager.isKeypadChannelSwitchActive()
                || mTransitionManager.isSelectInputActive();
    }

    @TvOverlayType private int convertSceneToOverlayType(@SceneType int sceneType) {
        switch (sceneType) {
            case TvTransitionManager.SCENE_TYPE_CHANNEL_BANNER:
                return OVERLAY_TYPE_SCENE_CHANNEL_BANNER;
            case TvTransitionManager.SCENE_TYPE_INPUT_BANNER:
                return OVERLAY_TYPE_SCENE_INPUT_BANNER;
            case TvTransitionManager.SCENE_TYPE_KEYPAD_CHANNEL_SWITCH:
                return OVERLAY_TYPE_SCENE_KEYPAD_CHANNEL_SWITCH;
            case TvTransitionManager.SCENE_TYPE_SELECT_INPUT:
                return OVERLAY_TYPE_SCENE_SELECT_INPUT;
            case TvTransitionManager.SCENE_TYPE_EMPTY:
            default:
                return OVERLAY_TYPE_NONE;
        }
    }

    private void onOverlayOpened(@TvOverlayType int overlayType) {
        if (DEBUG) Log.d(TAG, "Overlay opened:  0b" + Integer.toBinaryString(overlayType));
        mOpenedOverlays |= overlayType;
        if (DEBUG) Log.d(TAG, "Opened overlays: 0b" + Integer.toBinaryString(mOpenedOverlays));
        mMainActivity.updateKeyInputFocus();
    }

    private void onOverlayClosed(@TvOverlayType int overlayType) {
        if (DEBUG) Log.d(TAG, "Overlay closed:  0b" + Integer.toBinaryString(overlayType));
        mOpenedOverlays &= ~overlayType;
        if (DEBUG) Log.d(TAG, "Opened overlays: 0b" + Integer.toBinaryString(mOpenedOverlays));
        mMainActivity.updateKeyInputFocus();
        boolean onlyBannerOrNoneOpened = (mOpenedOverlays & ~OVERLAY_TYPE_SCENE_CHANNEL_BANNER
                & ~OVERLAY_TYPE_SCENE_INPUT_BANNER) == 0;
        // Show the main menu again if there are no pop-ups or banners only.
        // The main menu should not be shown when the activity is in paused state.
        boolean wasMenuShown = false;
        if (mMainActivity.isActivityResumed() && onlyBannerOrNoneOpened) {
            wasMenuShown = showMenuWithTimeShiftPauseIfNeeded();
        }
        // Don't set screen name to main if the overlay closing is a banner
        // or if a non banner overlay is still open
        // or if we just opened the menu
        if (overlayType != OVERLAY_TYPE_SCENE_CHANNEL_BANNER
                && overlayType != OVERLAY_TYPE_SCENE_INPUT_BANNER
                && onlyBannerOrNoneOpened
                && !wasMenuShown) {
            mTracker.sendScreenView(MainActivity.SCREEN_NAME);
        }
    }

    /**
     * Handles the onUserInteraction event of the {@link MainActivity}.
     */
    public void onUserInteraction() {
        if (mSideFragmentManager.isActive()) {
            mSideFragmentManager.scheduleHideAll();
        } else if (mMenu.isActive()) {
            mMenu.scheduleHide();
        } else if (mProgramGuide.isActive()) {
            mProgramGuide.scheduleHide();
        }
    }

    /**
     * Handles the onKeyDown event of the {@link MainActivity}.
     */
    @KeyHandlerResultType public int onKeyDown(int keyCode, KeyEvent event) {
        if (mCurrentDialog != null) {
            // Consumes the keys while a Dialog is creating.
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        // Handle media key here because it is related to the menu.
        if (isMediaStartKey(keyCode)) {
            // Consumes the keys which may trigger system's default music player.
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (mMenu.isActive() || mSideFragmentManager.isActive() || mProgramGuide.isActive()) {
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mTransitionManager.isKeypadChannelSwitchActive()) {
            return mKeypadChannelSwitchView.onKeyDown(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        if (mTransitionManager.isSelectInputActive()) {
            return mSelectInputView.onKeyDown(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        return MainActivity.KEY_EVENT_HANDLER_RESULT_PASSTHROUGH;
    }

    /**
     * Handles the onKeyUp event of the {@link MainActivity}.
     */
    @KeyHandlerResultType public int onKeyUp(int keyCode, KeyEvent event) {
        // Handle media key here because it is related to the menu.
        if (isMediaStartKey(keyCode)) {
            // The media key should not be passed up to the system in any cases.
            if (mCurrentDialog != null || mProgramGuide.isActive()
                    || mSideFragmentManager.isActive()
                    || mSearchFragment.isVisible()
                    || mTransitionManager.isKeypadChannelSwitchActive()
                    || mTransitionManager.isSelectInputActive()) {
                // Do not handle media key when any pop-ups which can handle keys are active.
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            TimeShiftManager timeShiftManager = mMainActivity.getTimeShiftManager();
            if (!timeShiftManager.isAvailable()) {
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    timeShiftManager.play();
                    showMenu(Menu.REASON_PLAY_CONTROLS_PLAY);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    timeShiftManager.pause();
                    showMenu(Menu.REASON_PLAY_CONTROLS_PAUSE);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    timeShiftManager.togglePlayPause();
                    showMenu(Menu.REASON_PLAY_CONTROLS_PLAY_PAUSE);
                    break;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    timeShiftManager.rewind();
                    showMenu(Menu.REASON_PLAY_CONTROLS_REWIND);
                    break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    timeShiftManager.fastForward();
                    showMenu(Menu.REASON_PLAY_CONTROLS_FAST_FORWARD);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    timeShiftManager.jumpToPrevious();
                    showMenu(Menu.REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    timeShiftManager.jumpToNext();
                    showMenu(Menu.REASON_PLAY_CONTROLS_JUMP_TO_NEXT);
                    break;
                default:
                    // Does nothing.
                    break;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (keyCode == KeyEvent.KEYCODE_I || keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            if (mTransitionManager.isSelectInputActive()) {
                mSelectInputView.onKeyUp(keyCode, event);
            } else {
                showSelectInputView();
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (mCurrentDialog != null) {
            // Consumes the keys while a Dialog is showing.
            // This can be happen while a Dialog isn't created yet.
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (mProgramGuide.isActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mProgramGuide.onBackPressed();
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mSideFragmentManager.isActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || mSideFragmentManager.isHideKeyForCurrentPanel(keyCode)) {
                mSideFragmentManager.popSideFragment();
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mMenu.isActive() || mTransitionManager.isSceneActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                TimeShiftManager timeShiftManager = mMainActivity.getTimeShiftManager();
                if (timeShiftManager.isPaused()) {
                    timeShiftManager.play();
                }
                hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            if (mMenu.isActive()) {
                if (KeypadChannelSwitchView.isChannelNumberKey(keyCode)) {
                    mMainActivity.showKeypadChannelSwitchView(keyCode);
                    return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
                }
                return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
            }
        }
        if (mTransitionManager.isKeypadChannelSwitchActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mTransitionManager.goToEmptyScene(true);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return mKeypadChannelSwitchView.onKeyUp(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        if (mTransitionManager.isSelectInputActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mTransitionManager.goToEmptyScene(true);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return mSelectInputView.onKeyUp(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        return MainActivity.KEY_EVENT_HANDLER_RESULT_PASSTHROUGH;
    }

    /**
     * Checks whether the given {@code keyCode} can start the system's music app or not.
     */
    private static boolean isMediaStartKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return true;
        }
        return false;
    }

    private static class TvOverlayHandler extends WeakHandler<TvOverlayManager> {
        public TvOverlayHandler(TvOverlayManager ref) {
            super(ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull TvOverlayManager tvOverlayManager) {
            if (msg.what == MSG_SHOW_DIALOG) {
                String tag = (String) msg.obj;
                tvOverlayManager.mCurrentDialog
                        .show(tvOverlayManager.mMainActivity.getFragmentManager(), tag);
            }
        }
    }
}

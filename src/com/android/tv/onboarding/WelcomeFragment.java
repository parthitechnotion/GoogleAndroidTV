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

package com.android.tv.onboarding;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.os.Bundle;
import android.transition.TransitionValues;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.tv.R;
import com.android.tv.common.ui.setup.SetupFragment;
import com.android.tv.common.ui.setup.animation.CustomTransition;
import com.android.tv.common.ui.setup.animation.CustomTransitionProvider;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;

/**
 * A fragment for the onboarding screen.
 */
public class WelcomeFragment extends SetupFragment {
    public static final int ACTION_NEXT = 1;

    private static final long LOGO_SPLASH_PAUSE_DURATION_MS = 333;
    private static final long LOGO_SPLASH_DURATION_MS = 1000;
    private static final long START_DELAY_PAGE_INDICATOR_MS = LOGO_SPLASH_DURATION_MS;
    private static final long START_DELAY_TITLE_MS = LOGO_SPLASH_DURATION_MS + 33;
    private static final long START_DELAY_DESCRIPTION_MS = LOGO_SPLASH_DURATION_MS + 33;
    private static final long START_DELAY_CLOUD_MS = LOGO_SPLASH_DURATION_MS + 33;
    private static final long START_DELAY_TV_MS = LOGO_SPLASH_DURATION_MS + 567;
    private static final long START_DELAY_TV_CONTENTS_MS = 266;
    private static final long START_DELAY_SHADOW_MS = LOGO_SPLASH_DURATION_MS + 567;

    private static final long WELCOME_PAGE_TRANSITION_DURATION_MS = 417;

    private static final long BLUE_SCREEN_HOLD_DURATION_MS = 1500;

    private static final int[] TV_FRAMES_1_START = {
            R.drawable.tv_1a_01,
            R.drawable.tv_1a_02,
            R.drawable.tv_1a_03,
            R.drawable.tv_1a_04,
            R.drawable.tv_1a_05,
            R.drawable.tv_1a_06,
            R.drawable.tv_1a_07,
            R.drawable.tv_1a_08,
            R.drawable.tv_1a_09,
            R.drawable.tv_1a_10,
            R.drawable.tv_1a_11,
            R.drawable.tv_1a_12,
            R.drawable.tv_1a_13,
            R.drawable.tv_1a_14,
            R.drawable.tv_1a_15,
            R.drawable.tv_1a_16,
            R.drawable.tv_1a_17,
            R.drawable.tv_1a_18,
            R.drawable.tv_1a_19,
            R.drawable.tv_1a_20,
            0
    };

    private static final int[] TV_FRAMES_1_END = {
            R.drawable.tv_1b_01,
            R.drawable.tv_1b_02,
            R.drawable.tv_1b_03,
            R.drawable.tv_1b_04,
            R.drawable.tv_1b_05,
            R.drawable.tv_1b_06,
            R.drawable.tv_1b_07,
            R.drawable.tv_1b_08,
            R.drawable.tv_1b_09,
            R.drawable.tv_1b_10,
            R.drawable.tv_1b_11,
            0
    };

    private static final int[] TV_FRAMES_2_BLUE_ARROW = {
            R.drawable.arrow_blue_00,
            R.drawable.arrow_blue_01,
            R.drawable.arrow_blue_02,
            R.drawable.arrow_blue_03,
            R.drawable.arrow_blue_04,
            R.drawable.arrow_blue_05,
            R.drawable.arrow_blue_06,
            R.drawable.arrow_blue_07,
            R.drawable.arrow_blue_08,
            R.drawable.arrow_blue_09,
            R.drawable.arrow_blue_10,
            R.drawable.arrow_blue_11,
            R.drawable.arrow_blue_12,
            R.drawable.arrow_blue_13,
            R.drawable.arrow_blue_14,
            R.drawable.arrow_blue_15,
            R.drawable.arrow_blue_16,
            R.drawable.arrow_blue_17,
            R.drawable.arrow_blue_18,
            R.drawable.arrow_blue_19,
            R.drawable.arrow_blue_20,
            R.drawable.arrow_blue_21,
            R.drawable.arrow_blue_22,
            R.drawable.arrow_blue_23,
            R.drawable.arrow_blue_24,
            R.drawable.arrow_blue_25,
            R.drawable.arrow_blue_26,
            R.drawable.arrow_blue_27,
            R.drawable.arrow_blue_28,
            R.drawable.arrow_blue_29,
            R.drawable.arrow_blue_30,
            R.drawable.arrow_blue_31,
            R.drawable.arrow_blue_32,
            R.drawable.arrow_blue_33,
            R.drawable.arrow_blue_34,
            R.drawable.arrow_blue_35,
            R.drawable.arrow_blue_36,
            R.drawable.arrow_blue_37,
            R.drawable.arrow_blue_38,
            R.drawable.arrow_blue_39,
            R.drawable.arrow_blue_40,
            R.drawable.arrow_blue_41,
            R.drawable.arrow_blue_42,
            R.drawable.arrow_blue_43,
            R.drawable.arrow_blue_44,
            R.drawable.arrow_blue_45,
            R.drawable.arrow_blue_46,
            R.drawable.arrow_blue_47,
            R.drawable.arrow_blue_48,
            R.drawable.arrow_blue_49,
            R.drawable.arrow_blue_50,
            R.drawable.arrow_blue_51,
            R.drawable.arrow_blue_52,
            R.drawable.arrow_blue_53,
            R.drawable.arrow_blue_54,
            R.drawable.arrow_blue_55,
            R.drawable.arrow_blue_56,
            R.drawable.arrow_blue_57,
            R.drawable.arrow_blue_58,
            R.drawable.arrow_blue_59,
            R.drawable.arrow_blue_60,
            0
    };

    private static final int[] TV_FRAMES_2_BLUE_START = {
            R.drawable.tv_2a_01,
            R.drawable.tv_2a_02,
            R.drawable.tv_2a_03,
            R.drawable.tv_2a_04,
            R.drawable.tv_2a_05,
            R.drawable.tv_2a_06,
            R.drawable.tv_2a_07,
            R.drawable.tv_2a_08,
            R.drawable.tv_2a_09,
            R.drawable.tv_2a_10,
            R.drawable.tv_2a_11,
            R.drawable.tv_2a_12,
            R.drawable.tv_2a_13,
            R.drawable.tv_2a_14,
            R.drawable.tv_2a_15,
            R.drawable.tv_2a_16,
            R.drawable.tv_2a_17,
            R.drawable.tv_2a_18,
            R.drawable.tv_2a_19,
            0
    };

    private static final int[] TV_FRAMES_2_BLUE_END = {
            R.drawable.tv_2b_01,
            R.drawable.tv_2b_02,
            R.drawable.tv_2b_03,
            R.drawable.tv_2b_04,
            R.drawable.tv_2b_05,
            R.drawable.tv_2b_06,
            R.drawable.tv_2b_07,
            R.drawable.tv_2b_08,
            R.drawable.tv_2b_09,
            R.drawable.tv_2b_10,
            R.drawable.tv_2b_11,
            R.drawable.tv_2b_12,
            R.drawable.tv_2b_13,
            R.drawable.tv_2b_14,
            R.drawable.tv_2b_15,
            R.drawable.tv_2b_16,
            R.drawable.tv_2b_17,
            R.drawable.tv_2b_18,
            R.drawable.tv_2b_19,
            0
    };

    private static final int[] TV_FRAMES_2_ORANGE_ARROW = {
            R.drawable.arrow_orange_180,
            R.drawable.arrow_orange_181,
            R.drawable.arrow_orange_182,
            R.drawable.arrow_orange_183,
            R.drawable.arrow_orange_184,
            R.drawable.arrow_orange_185,
            R.drawable.arrow_orange_186,
            R.drawable.arrow_orange_187,
            R.drawable.arrow_orange_188,
            R.drawable.arrow_orange_189,
            R.drawable.arrow_orange_190,
            R.drawable.arrow_orange_191,
            R.drawable.arrow_orange_192,
            R.drawable.arrow_orange_193,
            R.drawable.arrow_orange_194,
            R.drawable.arrow_orange_195,
            R.drawable.arrow_orange_196,
            R.drawable.arrow_orange_197,
            R.drawable.arrow_orange_198,
            R.drawable.arrow_orange_199,
            R.drawable.arrow_orange_200,
            R.drawable.arrow_orange_201,
            R.drawable.arrow_orange_202,
            R.drawable.arrow_orange_203,
            R.drawable.arrow_orange_204,
            R.drawable.arrow_orange_205,
            R.drawable.arrow_orange_206,
            R.drawable.arrow_orange_207,
            R.drawable.arrow_orange_208,
            R.drawable.arrow_orange_209,
            R.drawable.arrow_orange_210,
            R.drawable.arrow_orange_211,
            R.drawable.arrow_orange_212,
            R.drawable.arrow_orange_213,
            R.drawable.arrow_orange_214,
            R.drawable.arrow_orange_215,
            R.drawable.arrow_orange_216,
            R.drawable.arrow_orange_217,
            R.drawable.arrow_orange_218,
            R.drawable.arrow_orange_219,
            R.drawable.arrow_orange_220,
            R.drawable.arrow_orange_221,
            R.drawable.arrow_orange_222,
            R.drawable.arrow_orange_223,
            R.drawable.arrow_orange_224,
            R.drawable.arrow_orange_225,
            R.drawable.arrow_orange_226,
            R.drawable.arrow_orange_227,
            R.drawable.arrow_orange_228,
            R.drawable.arrow_orange_229,
            R.drawable.arrow_orange_230,
            R.drawable.arrow_orange_231,
            R.drawable.arrow_orange_232,
            R.drawable.arrow_orange_233,
            R.drawable.arrow_orange_234,
            R.drawable.arrow_orange_235,
            R.drawable.arrow_orange_236,
            R.drawable.arrow_orange_237,
            R.drawable.arrow_orange_238,
            R.drawable.arrow_orange_239,
            R.drawable.arrow_orange_240,
            0
    };

    private static final int[] TV_FRAMES_2_ORANGE_START = {
            R.drawable.tv_2c_01,
            R.drawable.tv_2c_02,
            R.drawable.tv_2c_03,
            R.drawable.tv_2c_04,
            R.drawable.tv_2c_05,
            R.drawable.tv_2c_06,
            R.drawable.tv_2c_07,
            R.drawable.tv_2c_08,
            R.drawable.tv_2c_09,
            R.drawable.tv_2c_10,
            R.drawable.tv_2c_11,
            R.drawable.tv_2c_12,
            R.drawable.tv_2c_13,
            R.drawable.tv_2c_14,
            R.drawable.tv_2c_15,
            R.drawable.tv_2c_16,
            0
    };

    private static final int[] TV_FRAMES_3_START = {
            R.drawable.tv_3a_01,
            R.drawable.tv_3a_02,
            R.drawable.tv_3a_03,
            R.drawable.tv_3a_04,
            R.drawable.tv_3a_05,
            R.drawable.tv_3a_06,
            R.drawable.tv_3a_07,
            R.drawable.tv_3a_08,
            R.drawable.tv_3a_09,
            R.drawable.tv_3a_10,
            R.drawable.tv_3a_11,
            R.drawable.tv_3a_12,
            R.drawable.tv_3a_13,
            R.drawable.tv_3a_14,
            R.drawable.tv_3a_15,
            R.drawable.tv_3a_16,
            R.drawable.tv_3a_17,
            R.drawable.tv_3b_75,
            R.drawable.tv_3b_76,
            R.drawable.tv_3b_77,
            R.drawable.tv_3b_78,
            R.drawable.tv_3b_79,
            R.drawable.tv_3b_80,
            R.drawable.tv_3b_81,
            R.drawable.tv_3b_82,
            R.drawable.tv_3b_83,
            R.drawable.tv_3b_84,
            R.drawable.tv_3b_85,
            R.drawable.tv_3b_86,
            R.drawable.tv_3b_87,
            R.drawable.tv_3b_88,
            R.drawable.tv_3b_89,
            R.drawable.tv_3b_90,
            R.drawable.tv_3b_91,
            R.drawable.tv_3b_92,
            R.drawable.tv_3b_93,
            R.drawable.tv_3b_94,
            R.drawable.tv_3b_95,
            R.drawable.tv_3b_96,
            R.drawable.tv_3b_97,
            R.drawable.tv_3b_98,
            R.drawable.tv_3b_99,
            R.drawable.tv_3b_100,
            R.drawable.tv_3b_101,
            R.drawable.tv_3b_102,
            R.drawable.tv_3b_103,
            R.drawable.tv_3b_104,
            R.drawable.tv_3b_105,
            R.drawable.tv_3b_106,
            R.drawable.tv_3b_107,
            R.drawable.tv_3b_108,
            R.drawable.tv_3b_109,
            R.drawable.tv_3b_110,
            R.drawable.tv_3b_111,
            R.drawable.tv_3b_112,
            R.drawable.tv_3b_113,
            R.drawable.tv_3b_114,
            R.drawable.tv_3b_115,
            R.drawable.tv_3b_116,
            R.drawable.tv_3b_117,
            R.drawable.tv_3b_118,
            0
    };

    private static final int[] TV_FRAMES_4_START = {
            R.drawable.tv_4a_15,
            R.drawable.tv_4a_16,
            R.drawable.tv_4a_17,
            R.drawable.tv_4a_18,
            R.drawable.tv_4a_19,
            R.drawable.tv_4a_20,
            R.drawable.tv_4a_21,
            R.drawable.tv_4a_22,
            R.drawable.tv_4a_23,
            R.drawable.tv_4a_24,
            R.drawable.tv_4a_25,
            R.drawable.tv_4a_26,
            R.drawable.tv_4a_27,
            R.drawable.tv_4a_28,
            R.drawable.tv_4a_29,
            R.drawable.tv_4a_30,
            R.drawable.tv_4a_31,
            R.drawable.tv_4a_32,
            R.drawable.tv_4a_33,
            R.drawable.tv_4a_34,
            R.drawable.tv_4a_35,
            R.drawable.tv_4a_36,
            R.drawable.tv_4a_37,
            R.drawable.tv_4a_38,
            R.drawable.tv_4a_39,
            R.drawable.tv_4a_40,
            R.drawable.tv_4a_41,
            R.drawable.tv_4a_42,
            R.drawable.tv_4a_43,
            R.drawable.tv_4a_44,
            R.drawable.tv_4a_45,
            R.drawable.tv_4a_46,
            R.drawable.tv_4a_47,
            R.drawable.tv_4a_48,
            R.drawable.tv_4a_49,
            R.drawable.tv_4a_50,
            R.drawable.tv_4a_51,
            R.drawable.tv_4a_52,
            R.drawable.tv_4a_53,
            R.drawable.tv_4a_54,
            R.drawable.tv_4a_55,
            R.drawable.tv_4a_56,
            R.drawable.tv_4a_57,
            R.drawable.tv_4a_58,
            R.drawable.tv_4a_59,
            R.drawable.tv_4a_60,
            R.drawable.tv_4a_61,
            R.drawable.tv_4a_62,
            R.drawable.tv_4a_63,
            R.drawable.tv_4a_64,
            R.drawable.tv_4a_65,
            R.drawable.tv_4a_66,
            R.drawable.tv_4a_67,
            R.drawable.tv_4a_68,
            R.drawable.tv_4a_69,
            R.drawable.tv_4a_70,
            R.drawable.tv_4a_71,
            R.drawable.tv_4a_72,
            R.drawable.tv_4a_73,
            R.drawable.tv_4a_74,
            R.drawable.tv_4a_75,
            R.drawable.tv_4a_76,
            R.drawable.tv_4a_77,
            R.drawable.tv_4a_78,
            R.drawable.tv_4a_79,
            R.drawable.tv_4a_80,
            R.drawable.tv_4a_81,
            R.drawable.tv_4a_82,
            R.drawable.tv_4a_83,
            R.drawable.tv_4a_84,
            R.drawable.tv_4a_85,
            R.drawable.tv_4a_86,
            R.drawable.tv_4a_87,
            R.drawable.tv_4a_88,
            R.drawable.tv_4a_89,
            R.drawable.tv_4a_90,
            R.drawable.tv_4a_91,
            R.drawable.tv_4a_92,
            R.drawable.tv_4a_93,
            R.drawable.tv_4a_94,
            R.drawable.tv_4a_95,
            R.drawable.tv_4a_96,
            R.drawable.tv_4a_97,
            R.drawable.tv_4a_98,
            R.drawable.tv_4a_99,
            R.drawable.tv_4a_100,
            R.drawable.tv_4a_101,
            R.drawable.tv_4a_102,
            R.drawable.tv_4a_103,
            R.drawable.tv_4a_104,
            R.drawable.tv_4a_105,
            R.drawable.tv_4a_106,
            R.drawable.tv_4a_107,
            R.drawable.tv_4a_108,
            R.drawable.tv_4a_109,
            R.drawable.tv_4a_110,
            R.drawable.tv_4a_111,
            R.drawable.tv_4a_112,
            R.drawable.tv_4a_113,
            R.drawable.tv_4a_114,
            R.drawable.tv_4a_115,
            R.drawable.tv_4a_116,
            R.drawable.tv_4a_117,
            R.drawable.tv_4a_118,
            R.drawable.tv_4a_119,
            R.drawable.tv_4a_120,
            R.drawable.tv_4a_121,
            R.drawable.tv_4a_122,
            R.drawable.tv_4a_123,
            R.drawable.tv_4a_124,
            R.drawable.tv_4a_125,
            R.drawable.tv_4a_126,
            R.drawable.tv_4a_127,
            R.drawable.tv_4a_128,
            R.drawable.tv_4a_129,
            R.drawable.tv_4a_130,
            R.drawable.tv_4a_131,
            R.drawable.tv_4a_132,
            R.drawable.tv_4a_133,
            R.drawable.tv_4a_134,
            R.drawable.tv_4a_135,
            R.drawable.tv_4a_136,
            R.drawable.tv_4a_137,
            R.drawable.tv_4a_138,
            R.drawable.tv_4a_139,
            R.drawable.tv_4a_140,
            R.drawable.tv_4a_141,
            R.drawable.tv_4a_142,
            R.drawable.tv_4a_143,
            R.drawable.tv_4a_144,
            R.drawable.tv_4a_145,
            R.drawable.tv_4a_146,
            R.drawable.tv_4a_147,
            R.drawable.tv_4a_148,
            R.drawable.tv_4a_149,
            R.drawable.tv_4a_150,
            R.drawable.tv_4a_151,
            R.drawable.tv_4a_152,
            R.drawable.tv_4a_153,
            R.drawable.tv_4a_154,
            R.drawable.tv_4a_155,
            R.drawable.tv_4a_156,
            R.drawable.tv_4a_157,
            R.drawable.tv_4a_158,
            R.drawable.tv_4a_159,
            R.drawable.tv_4a_160,
            R.drawable.tv_4a_161,
            R.drawable.tv_4a_162,
            R.drawable.tv_4a_163,
            R.drawable.tv_4a_164,
            R.drawable.tv_4a_165,
            R.drawable.tv_4a_166,
            R.drawable.tv_4a_167,
            R.drawable.tv_4a_168,
            R.drawable.tv_4a_169,
            R.drawable.tv_4a_170,
            R.drawable.tv_4a_171,
            R.drawable.tv_4a_172,
            R.drawable.tv_4a_173,
            R.drawable.tv_4a_174,
            R.drawable.tv_4a_175,
            R.drawable.tv_4a_176,
            R.drawable.tv_4a_177,
            R.drawable.tv_4a_178,
            R.drawable.tv_4a_179,
            R.drawable.tv_4a_180,
            R.drawable.tv_4a_181,
            R.drawable.tv_4a_182,
            R.drawable.tv_4a_183,
            R.drawable.tv_4a_184,
            R.drawable.tv_4a_185,
            R.drawable.tv_4a_186,
            R.drawable.tv_4a_187,
            R.drawable.tv_4a_188,
            R.drawable.tv_4a_189,
            R.drawable.tv_4a_190,
            R.drawable.tv_4a_191,
            R.drawable.tv_4a_192,
            R.drawable.tv_4a_193,
            R.drawable.tv_4a_194,
            R.drawable.tv_4a_195,
            R.drawable.tv_4a_196,
            R.drawable.tv_4a_197,
            R.drawable.tv_4a_198,
            R.drawable.tv_4a_199,
            R.drawable.tv_4a_200,
            R.drawable.tv_4a_201,
            R.drawable.tv_4a_202,
            R.drawable.tv_4a_203,
            R.drawable.tv_4a_204,
            R.drawable.tv_4a_205,
            R.drawable.tv_4a_206,
            R.drawable.tv_4a_207,
            R.drawable.tv_4a_208,
            R.drawable.tv_4a_209,
            R.drawable.tv_4a_210,
            R.drawable.tv_4a_211,
            R.drawable.tv_4a_212,
            R.drawable.tv_4a_213,
            R.drawable.tv_4a_214,
            R.drawable.tv_4a_215,
            R.drawable.tv_4a_216,
            R.drawable.tv_4a_217,
            R.drawable.tv_4a_218,
            R.drawable.tv_4a_219,
            R.drawable.tv_4a_220,
            R.drawable.tv_4a_221,
            R.drawable.tv_4a_222,
            R.drawable.tv_4a_223,
            R.drawable.tv_4a_224,
            R.drawable.tv_4a_225,
            R.drawable.tv_4a_226,
            R.drawable.tv_4a_227,
            R.drawable.tv_4a_228,
            R.drawable.tv_4a_229,
            R.drawable.tv_4a_230,
            R.drawable.tv_4a_231,
            R.drawable.tv_4a_232,
            R.drawable.tv_4a_233,
            R.drawable.tv_4a_234,
            R.drawable.tv_4a_235,
            R.drawable.tv_4a_236,
            R.drawable.tv_4a_237,
            R.drawable.tv_4a_238,
            R.drawable.tv_4a_239,
            0
    };

    private int mNumPages;
    private String[] mPageTitles;
    private String[] mPageDescriptions;
    private int mCurrentPageIndex;
    private int mPageTransitionDistance;

    private ImageView mTvContentView;
    private PagingIndicator mPageIndicator;
    private ImageView mArrowView;
    private View mLogoView;

    private Animator mAnimator;

    public WelcomeFragment() {
        enableFragmentTransition(FRAGMENT_EXIT_TRANSITION);
        setEnterTransition(new CustomTransition(new CustomTransitionProvider() {
            @Override
            public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                    TransitionValues endValues) {
                Animator animator = null;
                switch (endValues.view.getId()) {
                    case R.id.logo: {
                        Animator inAnimator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_logo_enter);
                        Animator outAnimator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_logo_exit);
                        outAnimator.setStartDelay(LOGO_SPLASH_PAUSE_DURATION_MS);
                        animator = new AnimatorSet();
                        ((AnimatorSet) animator).playSequentially(inAnimator, outAnimator);
                        animator.setTarget(view);
                        break;
                    }
                    case R.id.page_indicator:
                        view.setAlpha(0);
                        animator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_page_indicator_enter);
                        animator.setStartDelay(START_DELAY_PAGE_INDICATOR_MS);
                        animator.setTarget(view);
                        break;
                    case R.id.title:
                        view.setAlpha(0);
                        animator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_title_enter);
                        animator.setStartDelay(START_DELAY_TITLE_MS);
                        animator.setTarget(view);
                        break;
                    case R.id.description:
                        view.setAlpha(0);
                        animator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_description_enter);
                        animator.setStartDelay(START_DELAY_DESCRIPTION_MS);
                        animator.setTarget(view);
                        break;
                    case R.id.cloud1:
                    case R.id.cloud2:
                        view.setAlpha(0);
                        animator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_cloud_enter);
                        animator.setStartDelay(START_DELAY_CLOUD_MS);
                        animator.setTarget(view);
                        break;
                    case R.id.tv_container: {
                        view.setAlpha(0);
                        Animator tvAnimator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_tv_enter);
                        tvAnimator.setTarget(view);
                        Animator frameAnimator = SetupAnimationHelper.createFrameAnimator(
                                mTvContentView, TV_FRAMES_1_START);
                        frameAnimator.setStartDelay(START_DELAY_TV_CONTENTS_MS);
                        frameAnimator.setTarget(mTvContentView);
                        animator = new AnimatorSet();
                        ((AnimatorSet) animator).playTogether(tvAnimator, frameAnimator);
                        animator.setStartDelay(START_DELAY_TV_MS);
                        break;
                    }
                    case R.id.shadow:
                        view.setAlpha(0);
                        animator = AnimatorInflater.loadAnimator(getActivity(),
                                R.animator.onboarding_welcome_shadow_enter);
                        animator.setStartDelay(START_DELAY_SHADOW_MS);
                        animator.setTarget(view);
                        break;
                }
                return animator;
            }

            @Override
            public Animator onDisappear(ViewGroup sceneRoot, View view,
                    TransitionValues startValues, TransitionValues endValues) {
                return null;
            }
        }));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mAnimator = null;
        mPageTransitionDistance = getResources().getDimensionPixelOffset(
                R.dimen.onboarding_welcome_page_transition_distance);
        mPageTitles = getResources().getStringArray(R.array.welcome_page_titles);
        mPageDescriptions = getResources().getStringArray(R.array.welcome_page_descriptions);
        mNumPages = mPageTitles.length;
        mCurrentPageIndex = 0;
        mTvContentView = (ImageView) view.findViewById(R.id.tv_content);
        mPageIndicator = (PagingIndicator) view.findViewById(R.id.page_indicator);
        mPageIndicator.setPageCount(mNumPages);
        mPageIndicator.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCurrentPageIndex == mNumPages - 1) {
                    onActionClick(ACTION_NEXT);
                } else {
                    showPage(++mCurrentPageIndex);
                    startTvFrameAnimation(mCurrentPageIndex);
                }
            }
        });
        mArrowView = (ImageView) view.findViewById(R.id.arrow);
        mLogoView = view.findViewById(R.id.logo);
        showPage(mCurrentPageIndex);
        return view;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_welcome;
    }

    /*
     * Should return {@link SetupFragment} for the custom animations.
     */
    private SetupFragment getPage(int index) {
        Bundle args = new Bundle();
        args.putString(WelcomePageFragment.KEY_TITLE, mPageTitles[index]);
        args.putString(WelcomePageFragment.KEY_DESCRIPTION, mPageDescriptions[index]);
        SetupFragment fragment = new WelcomePageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private void showPage(final int pageIndex) {
        SetupFragment fragment = getPage(pageIndex);
        if (pageIndex == 0) {
            fragment.enableFragmentTransition(FRAGMENT_EXIT_TRANSITION);
        }
        if (pageIndex == mNumPages - 1) {
            fragment.enableFragmentTransition(FRAGMENT_ENTER_TRANSITION);
        }
        fragment.setTransitionDistance(mPageTransitionDistance);
        fragment.setTransitionDuration(WELCOME_PAGE_TRANSITION_DURATION_MS);
        getChildFragmentManager().beginTransaction().replace(R.id.page_container, fragment)
                .commit();
        mPageIndicator.onPageSelected(pageIndex, pageIndex != 0);
    }

    @Override
    protected int[] getParentIdsForDelay() {
        return new int[] {R.id.welcome_fragment_root};
    }

    private void startTvFrameAnimation(int newPageIndex) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        // TODO: Change the magic numbers to constants once the animation specification is given.
        AnimatorSet animatorSet = new AnimatorSet();
        switch (newPageIndex) {
            case 1:
                mLogoView.setVisibility(View.GONE);
                animatorSet.playSequentially(
                        SetupAnimationHelper.createFrameAnimator(mTvContentView, TV_FRAMES_1_END),
                        SetupAnimationHelper.createFrameAnimator(mArrowView,
                                TV_FRAMES_2_BLUE_ARROW),
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_2_BLUE_START),
                        SetupAnimationHelper.createFrameAnimatorWithDelay(mTvContentView,
                                TV_FRAMES_2_BLUE_END, BLUE_SCREEN_HOLD_DURATION_MS),
                        SetupAnimationHelper.createFrameAnimator(mArrowView,
                                TV_FRAMES_2_ORANGE_ARROW),
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_2_ORANGE_START));
                mArrowView.setVisibility(View.VISIBLE);
                break;
            case 2:
                mArrowView.setVisibility(View.GONE);
                animatorSet.playSequentially(
                        SetupAnimationHelper.createFadeOutAnimator(mTvContentView, 333, true),
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_3_START));
                break;
            case 3:
                animatorSet.playSequentially(
                        SetupAnimationHelper.createFadeOutAnimator(mTvContentView, 333, true),
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_4_START));
                break;
        }
        mAnimator = SetupAnimationHelper.applyAnimationTimeScale(animatorSet);
        mAnimator.start();
    }
}

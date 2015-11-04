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

package com.android.tv;

import static com.android.tv.common.feature.FeatureUtils.AND;
import static com.android.tv.common.feature.FeatureUtils.ON;
import static com.android.tv.common.feature.FeatureUtils.OR;

import android.support.annotation.VisibleForTesting;

import com.android.tv.common.feature.Feature;
import com.android.tv.common.feature.GServiceFeature;
import com.android.tv.common.feature.PropertyFeature;
import com.android.tv.util.EngOnlyFeature;

/**
 * List of {@link Feature} for the Live TV App.
 *
 * <p>Remove the {@code Feature} once it is launched.
 */
public final class Features {
    /**
     * UI for opting out of analytics.
     *
     * <p>See <a href="http://b/20228119">b/20228119</a>
     */
    public static Feature ANALYTICS_OPT_OUT = new EngOnlyFeature();

    /**
     * Analytics that include sensitive information such as channel or program identifiers.
     *
     * <p>See <a href="http://b/22062676">b/22062676</a>
     */
    public static Feature ANALYTICS_V2 = AND(ON, ANALYTICS_OPT_OUT);

    public static Feature EPG_SEARCH = new PropertyFeature("feature_tv_use_epg_search", false);


    /**
     * A flag which indicates that the on-boarding experience is used or not.
     *
     * <p>See <a href="http://b/24070322">b/24070322</a>
     */
    public static Feature ONBOARDING_EXPERIENCE = new PropertyFeature(
            "feature_tv_use_onboarding_exp", false);

    @VisibleForTesting
    public static Feature TEST_FEATURE = new PropertyFeature("test_feature", false);

    private Features() {
    }
}

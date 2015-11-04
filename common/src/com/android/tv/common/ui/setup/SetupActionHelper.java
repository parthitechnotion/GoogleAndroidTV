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

package com.android.tv.common.ui.setup;

import android.app.Fragment;

/**
 * Helper class for the execution in the fragment.
 */
public class SetupActionHelper {
    /**
     * Executes the action of the given {@code actionId}.
     */
    public static void onActionClick(Fragment fragment, int actionId) {
        if (fragment.getActivity() instanceof OnActionClickListener) {
            ((OnActionClickListener) fragment.getActivity()).onActionClick(actionId);
        }
    }
}

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

package com.android.tv.util;

import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.media.tv.TvInputInfo;

import com.android.tv.common.TvCommonConstants;

import java.lang.reflect.Constructor;

/**
 * A class that includes convenience methods for testing.
 */
public class TestUtils {
    public static TvInputInfo createTvInputInfo(ResolveInfo service, String id, String parentId,
            int type, boolean isHardwareInput) throws Exception {
        // Create a mock TvInputInfo by using private constructor
        // TODO: Find better way to mock TvInputInfo.
        // Note that mockito doesn't support mock/spy on final object.
        if (!TvCommonConstants.IS_MNC_PREVIEW && !TvCommonConstants.IS_MNC_OR_HIGHER) {
            return createTvInputInfoForLmp(service, id, parentId, type);
        }
        return createTvInputInfoForMnc(service, id, parentId, type, isHardwareInput);
    }

    private static TvInputInfo createTvInputInfoForLmp(ResolveInfo service, String id,
            String parentId, int type) throws Exception {
        Constructor<TvInputInfo> constructor = TvInputInfo.class.getDeclaredConstructor(new Class[]{
                ResolveInfo.class, String.class, String.class, int.class});
        constructor.setAccessible(true);
        return constructor.newInstance(service, id, parentId, type);
    }

    private static TvInputInfo createTvInputInfoForMnc(ResolveInfo service, String id,
            String parentId, int type, boolean isHardwareInput) throws Exception {
        Constructor<TvInputInfo> constructor = TvInputInfo.class.getDeclaredConstructor(new Class[]{
                ResolveInfo.class, String.class, String.class, int.class, boolean.class});
        constructor.setAccessible(true);
        return constructor.newInstance(service, id, parentId, type, isHardwareInput);
    }

    public static ResolveInfo createResolveInfo(String packageName, String name) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = packageName;
        resolveInfo.serviceInfo.name = name;
        return resolveInfo;
    }
}

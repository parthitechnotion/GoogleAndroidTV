#
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    livetv-android-support-annotations:annotations/android-support-annotations.jar \
    livetv-android-support-v4:v4/android-support-v4.jar\
    livetv-android-support-v7-palette:v7/palette/libs/android-support-v7-palette.jar \
    livetv-android-support-v7-recyclerview:v7/recyclerview/libs/android-support-v7-recyclerview.jar \
    livetv-android-support-v17-leanback:v17/leanback/libs/android-support-v17-leanback.jar \

include $(BUILD_MULTI_PREBUILT)


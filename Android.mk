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

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PACKAGE_NAME := TV

# It is required for com.android.providers.tv.permission.ALL_EPG_DATA
LOCAL_PRIVILEGED_MODULE := true

LOCAL_SDK_VERSION := system_current
LOCAL_RESOURCE_DIR := \
    $(TOP)/prebuilts/sdk/current/support/v7/recyclerview/res \
    $(TOP)/prebuilts/sdk/current/support/v17/leanback/res \
    $(LOCAL_PATH)/res

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-v4 \
    android-support-v7-palette \
    android-support-v7-recyclerview \
    android-support-v17-leanback \
    tv-common

LOCAL_JAVACFLAGS := -Xlint:deprecation -Xlint:unchecked

include $(LOCAL_PATH)/version.mk
LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages android.support.v17.leanback \
    --version-name "$(version_name_package)" \
    --version-code $(version_code_package) \

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))

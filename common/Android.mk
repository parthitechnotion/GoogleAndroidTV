LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_BUILDCONFIG_CLASS := src/com/android/tv/common/BuildConfig.java
BC_OUT_DIR := $(LOCAL_PATH)
BC_APPLICATION_ID := "com.android.tv.common"
include $(LOCAL_PATH)/buildconfig.mk

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    $(LOCAL_BUILDCONFIG_CLASS)

LOCAL_MODULE := tv-common
LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := system_current

LOCAL_RESOURCE_DIR := \
    $(TOP)/prebuilts/sdk/current/support/v7/recyclerview/res \
    $(TOP)/prebuilts/sdk/current/support/v17/leanback/res \
    $(LOCAL_PATH)/res \
    $(LOCAL_PATH)/res_leanback \

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-v4 \
    android-support-v7-recyclerview \
    android-support-v17-leanback \

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages android.support.v17.leanback \


include $(BUILD_STATIC_JAVA_LIBRARY)

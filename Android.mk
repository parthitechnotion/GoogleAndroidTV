LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PACKAGE_NAME := TV

# It is required for android.permission.ALL_EPG_DATA
LOCAL_PRIVILEGED_MODULE := true

LOCAL_RESOURCE_DIR := \
    $(TOP)/frameworks/support/v17/leanback/res \
    $(LOCAL_PATH)/res

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4 \
    android-support-v7-recyclerview \
    android-support-v17-leanback

LOCAL_AAPT_FLAGS += --auto-add-overlay --extra-packages android.support.v17.leanback

include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))

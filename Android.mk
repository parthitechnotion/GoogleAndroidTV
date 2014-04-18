LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PACKAGE_NAME := TV

# It is required for android.permission.ALL_EPG_DATA
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))

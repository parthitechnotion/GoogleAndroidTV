LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE := tv-common
LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \

include $(BUILD_STATIC_JAVA_LIBRARY)

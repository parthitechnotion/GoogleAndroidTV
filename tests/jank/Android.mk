LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests


# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := TVJankTests

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    tv-test-common \
    ub-janktesthelper \
    ub-uiautomator \

LOCAL_INSTRUMENTATION_FOR := TV

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

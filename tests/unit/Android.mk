LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests


# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    mockito-target \
    tv-test-common \

LOCAL_PACKAGE_NAME := TVUnitTests

LOCAL_INSTRUMENTATION_FOR := TV

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

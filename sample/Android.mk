LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += \
    ../src/com/android/tv/Channel.java \
    ../src/com/android/tv/Program.java

LOCAL_PACKAGE_NAME := SampleTvInput
LOCAL_MODULE_TAGS := optional
LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

LOCAL_PATH := $(call my-dir)

# --------------------------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE    := libusbtuner_jni
LOCAL_SRC_FILES += usbtuner_jni.cpp DvbManager.cpp
LOCAL_SDK_VERSION := 21
LOCAL_NDK_STL_VARIANT := stlport_static
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)

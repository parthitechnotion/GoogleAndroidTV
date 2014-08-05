LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := SampleTvInput
LOCAL_MODULE_TAGS := optional
LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

ifeq ($(PRODUCT_IS_ATV),true)
  ifneq ($(PRODUCT_IS_ATV_SDK),true)
    $(call dist-for-goals,dist_files,$(LOCAL_BUILT_MODULE):SampleTvInput.apk)
  endif
endif

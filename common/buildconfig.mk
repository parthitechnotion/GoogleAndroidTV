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

# Emulate gradles BuildConfig.java

ifeq "$(TARGET_BUILD_VARIANT)" "eng"
   BC_DEBUG_STATUS := "true"
else ifeq "$(TARGET_BUILD_VARIANT)" "userdebug"
   BC_DEBUG_STATUS := "true"
else
   BC_DEBUG_STATUS := "false"
endif

ifeq "$(TARGET_BUILD_VARIANT)" "eng"
   BC_ENG_STATUS := "true"
else
   BC_ENG_STATUS := "false"
endif

$(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS): FORCE
	echo "/**" > $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "* Automatically generated file. DO NOT MODIFY" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "*/" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "package "$(BC_APPLICATION_ID)";" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "public final class BuildConfig {" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "    public static final boolean DEBUG = "$(BC_DEBUG_STATUS)";" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "    public static final boolean ENG = "$(BC_ENG_STATUS)";" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "    private BuildConfig() {}" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
	echo "}" >> $(BC_OUT_DIR)/$(LOCAL_BUILDCONFIG_CLASS)
FORCE:

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

# The version code scheme for the package apk is:
#      Cmmbbbtad
# where
#    M - major version (one or more digits)
#    C - code major version  (for legacy reasons this is M+3)
#    m - minor version (exactly 2)
#  bbb - automatically specified build number (exactly 3 digits)
#    t - build type (exactly 1 digit).  Current valid values are:
#           0 : eng build
#           1 : build server build
#    a - device architecture (exactly 1 digit).  Current valid values are:
#           0 : non-native
#           1 : armv5te
#           3 : armv7-a
#           5 : mips
#           7 : x86
#    d - asset density (exactly 1 digit).  Current valid values are:
#           0 : all densities
# Mmmbbb is specified manually.  tad is automatically set during the build.
#
# For the client jar, the version code is agnostic to the target architecture and density: Mmbbbt00
#
# NOTE: arch needs to be more significant than density because x86 devices support running ARM
# code in emulation mode, so all x86 versions must be higher than all ARM versions to ensure
# we deliver true x86 code to those devices.
#

# Specify the following manually.  Note that base_version_minor must be exactly 2 digit and
# base_version_build must be exactly 3 digits.
# Always submit version number changes as DO NOT MERGE


base_version_major := 1
# Change this for each branch
base_version_minor := 06
# The date of the first commit checked in to the current branch
base_version_since := 2015-07-22


# code_version_major will overflow at 22
code_version_major := $(shell echo $$(($(base_version_major)+3)))

git_commit_count := $(shell git --git-dir $(LOCAL_PATH)/.git rev-list --since=$(base_version_since) --no-merges --count HEAD)
git_commit_count_usb := $(shell git --git-dir $(LOCAL_PATH)/../UsbTunerTvInput/.git rev-list --since=$(base_version_since) --no-merges --count HEAD)
#code_version_build := $(shell printf "%03d" $$(($(git_commit_count)+$(git_commit_count_usb))))
# TODO http://b/22930520 version.mk sometimes goes backwards
code_version_build=202

#####################################################
#####################################################
# Collect automatic version code parameters
ifneq "" "$(filter eng.%,$(BUILD_NUMBER))"
    # This is an eng build
    base_version_buildtype := 0
else
    # This is a build server build
    base_version_buildtype := 1
endif

ifeq "$(TARGET_ARCH)" "x86"
    base_version_arch := 7
else ifeq "$(TARGET_ARCH)" "mips"
    base_version_arch := 5
else ifeq "$(TARGET_ARCH)" "arm"
    ifeq ($(TARGET_ARCH_VARIANT),armv5te)
        base_version_arch := 1
    else
        base_version_arch := 3
    endif
else
    base_version_arch := 0
endif

# Currently supported densities.
base_version_density := 0

# Build the version code
version_code_package := $(code_version_major)$(base_version_minor)$(code_version_build)$(base_version_buildtype)$(base_version_arch)$(base_version_density)

# The version name scheme for the package apk is:
# - For eng build (t=0):     M.mm.bbb eng.$(USER)-hh-date-ad
# - For build server (t=1):  M.mm.bbb (nnnnnn-ad)
#       where nnnnnn is the build number from the build server (no zero-padding)
#       and hh is the git hash
# On eng builds, the BUILD_NUMBER has the user and timestamp inline
ifneq "" "$(filter eng.%,$(BUILD_NUMBER))"
    git_hash := $(shell git --git-dir $(LOCAL_PATH)/.git log -n 1 --pretty=format:%h)
    date_string := $(shell date +%m%d%y_%H%M%S)
    version_name_package := $(base_version_major).$(base_version_minor).$(code_version_build) (aosp.lmp.eng.$(USER).$(git_hash).$(date_string)-$(base_version_arch)$(base_version_density))
else
    version_name_package := $(base_version_major).$(base_version_minor).$(code_version_build) (aosp-lmp-$(BUILD_NUMBER)-$(base_version_arch)$(base_version_density))
endif

# Cleanup the locals
code_version_major :=
code_version_build :=
base_version_major :=
base_version_minor :=
base_version_since :=
base_version_buildtype :=
base_version_arch :=
base_version_density :=
git_commit_count :=
git_commit_count_usb :=
git_hash :=
date_string :=
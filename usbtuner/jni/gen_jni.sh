#!/bin/bash

javah -jni -classpath ../bin:../../../../../prebuilts/sdk/current/android.jar -o usbtuner_jni.h com.android.usbtuner.UsbTunerInterface

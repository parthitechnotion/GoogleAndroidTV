/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "usbtuner_jni.h"
#include <map>

#include "DvbManager.h"
#define LOG_TAG "usbtuner_jni"
#include "logging.h"

//-------------------------------------------------------------------------------
// JNI native method implementation
//-------------------------------------------------------------------------------

#define TS_PACKET_SIZE 188
#define TS_PAYLOAD_SIZE (TS_PACKET_SIZE * 7) // Fit Ethernet MTU (1500)
#define READ_TIMEOUT_MS 100

static int sTotalBytesFetched = 0;
static std::map<jlong, DvbManager *> sDvbManagers;

/*
 * Class:     com_android_usbtuner_UsbTunerInterface
 * Method:    nativeFinalize
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_android_usbtuner_UsbTunerInterface_nativeFinalize
(JNIEnv *, jobject, jlong deviceId) {
    std::map<jlong, DvbManager *>::iterator it = sDvbManagers.find(deviceId);
    if (it != sDvbManagers.end()) {
        delete it->second;
        sDvbManagers.erase(it);
    }
}

/*
 * Class:     com_android_usbtuner_UsbTunerInterface
 * Method:    nativeTuneAtsc
 * Signature: (JILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_android_usbtuner_UsbTunerInterface_nativeTuneAtsc
(JNIEnv *env, jobject thiz, jlong deviceId, jint frequency, jstring modulation, jint timeout_ms) {
    std::map<jlong, DvbManager *>::iterator it = sDvbManagers.find(deviceId);
    DvbManager *dvbManager;
    if (it == sDvbManagers.end()) {
        dvbManager = new DvbManager(env, thiz);
        sDvbManagers.insert(std::pair<jlong, DvbManager *>(deviceId, dvbManager));
    } else {
        dvbManager = it->second;
    }
    int res = dvbManager->tuneAtsc(env, thiz,
            frequency, env->GetStringUTFChars(modulation, 0), timeout_ms);
    return (res == 0);
}

/*
 * Class:     com_android_usbtuner_UsbTunerInterface
 * Method:    nativeCloseAllPidFilters
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_android_usbtuner_UsbTunerInterface_nativeCloseAllPidFilters
  (JNIEnv *, jobject, jlong deviceId) {
    std::map<jlong, DvbManager *>::iterator it = sDvbManagers.find(deviceId);
    if (it != sDvbManagers.end()) {
        it->second->closeAllDvbPidFilter();
    }
}

/*
 * Class:     com_android_usbtuner_UsbTunerInterface
 * Method:    nativeStopTune
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_android_usbtuner_UsbTunerInterface_nativeStopTune
(JNIEnv *, jobject, jlong deviceId) {
    std::map<jlong, DvbManager *>::iterator it = sDvbManagers.find(deviceId);
    if (it != sDvbManagers.end()) {
        it->second->stopTune();
    }
}

/*
 * Class:     com_android_usbtuner_UsbTunerInterface
 * Method:    nativeAddPidFilter
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL
Java_com_android_usbtuner_UsbTunerInterface_nativeAddPidFilter
(JNIEnv *env, jobject thiz, jlong deviceId, jint pid, jint filterType) {
    std::map<jlong, DvbManager *>::iterator it = sDvbManagers.find(deviceId);
    if (it != sDvbManagers.end()) {
        it->second->startTsPidFilter(env, thiz, pid, filterType);
    }
}

/*
 * Class:     com_android_usbtuner_UsbTunerInterface
 * Method:    nativeWriteInBuffer
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL
Java_com_android_usbtuner_UsbTunerInterface_nativeWriteInBuffer
(JNIEnv *env, jobject thiz, jlong deviceId, jbyteArray javaBuffer, jint javaBufferSize) {
    uint8_t tsBuffer[TS_PAYLOAD_SIZE];
    std::map<jlong, DvbManager *>::iterator it = sDvbManagers.find(deviceId);
    if (it == sDvbManagers.end()) {
        return -1;
    }
    DvbManager *dvbManager = it->second;

    // Always read multiple of TS_PACKET_SIZE
    javaBufferSize = (javaBufferSize / TS_PACKET_SIZE) * TS_PACKET_SIZE;
    int readBufferSize = (javaBufferSize < TS_PAYLOAD_SIZE) ? javaBufferSize : TS_PAYLOAD_SIZE;

    int dataSize = dvbManager->readTsStream(env, thiz, tsBuffer, readBufferSize, READ_TIMEOUT_MS);
    if (dataSize == 0) {
        ALOGD("No data to read DVR");
        return 0;
    } else if (dataSize < 0) {
        return -1;
    }

    sTotalBytesFetched += dataSize;

    env->SetByteArrayRegion(javaBuffer, 0, dataSize, (jbyte *) tsBuffer);
    return dataSize;
}

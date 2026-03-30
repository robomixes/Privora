#include <jni.h>
#include <string>
#include <android/log.h>
#include "ai_engine.h"
#include "detector.h"

#define LOG_TAG "PrivateAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static privateai::Detector* g_detector = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_privateai_camera_bridge_NativeBridge_initDetector(
    JNIEnv* env, jobject /* this */, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing detector with model: %s", path);

    try {
        if (g_detector) {
            delete g_detector;
        }
        g_detector = new privateai::Detector(path);
        env->ReleaseStringUTFChars(modelPath, path);
        LOGI("Detector initialized successfully");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Failed to initialize detector: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_privateai_camera_bridge_NativeBridge_detectObjects(
    JNIEnv* env, jobject /* this */,
    jbyteArray imageData, jint width, jint height,
    jfloat confidenceThreshold) {

    if (!g_detector) {
        LOGE("Detector not initialized");
        return env->NewFloatArray(0);
    }

    jbyte* data = env->GetByteArrayElements(imageData, nullptr);
    int dataLen = env->GetArrayLength(imageData);

    auto detections = g_detector->detect(
        reinterpret_cast<const uint8_t*>(data),
        width, height, confidenceThreshold);

    env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);

    // Pack detections: [x1, y1, x2, y2, confidence, classId] per detection
    int numFloats = static_cast<int>(detections.size()) * 6;
    jfloatArray result = env->NewFloatArray(numFloats);

    if (numFloats > 0) {
        std::vector<float> packed(numFloats);
        for (size_t i = 0; i < detections.size(); i++) {
            const auto& d = detections[i];
            packed[i * 6 + 0] = d.x1;
            packed[i * 6 + 1] = d.y1;
            packed[i * 6 + 2] = d.x2;
            packed[i * 6 + 3] = d.y2;
            packed[i * 6 + 4] = d.confidence;
            packed[i * 6 + 5] = static_cast<float>(d.class_id);
        }
        env->SetFloatArrayRegion(result, 0, numFloats, packed.data());
    }

    return result;
}

JNIEXPORT void JNICALL
Java_com_privateai_camera_bridge_NativeBridge_releaseDetector(
    JNIEnv* env, jobject /* this */) {
    if (g_detector) {
        delete g_detector;
        g_detector = nullptr;
        LOGI("Detector released");
    }
}

JNIEXPORT jlong JNICALL
Java_com_privateai_camera_bridge_NativeBridge_benchmarkDetector(
    JNIEnv* env, jobject /* this */) {
    if (!g_detector) {
        return -1;
    }
    return g_detector->benchmark();
}

} // extern "C"

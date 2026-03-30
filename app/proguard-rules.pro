# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep bridge classes
-keep class com.privateai.camera.bridge.** { *; }

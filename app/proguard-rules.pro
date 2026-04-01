# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }

# Keep data classes used in JSON serialization (notes)
-keep class com.privateai.camera.security.SecureNote { *; }

# Keep bridge classes (ONNX detector)
-keep class com.privateai.camera.bridge.** { *; }

# Crypto
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

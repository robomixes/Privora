package com.privateai.camera.bridge

/**
 * JNI bridge to the shared C++ core (libprivateai.so).
 * All AI inference, image processing, and encryption go through here.
 */
object NativeBridge {

    init {
        System.loadLibrary("privateai")
    }

    /**
     * Initialize the ONNX Runtime and load the YOLOv8n model.
     * @param modelPath absolute path to yolov8n.onnx
     * @return true if model loaded successfully
     */
    external fun initDetector(modelPath: String): Boolean

    /**
     * Run object detection on an image.
     * @param imageData RGB byte array (H*W*3)
     * @param width image width
     * @param height image height
     * @param confidenceThreshold minimum confidence (0.0-1.0)
     * @return float array of detections: [x1,y1,x2,y2,confidence,classId, ...]
     *         every 6 floats is one detection
     */
    external fun detectObjects(
        imageData: ByteArray,
        width: Int,
        height: Int,
        confidenceThreshold: Float
    ): FloatArray

    /**
     * Release the detector model and free resources.
     */
    external fun releaseDetector()

    /**
     * Run a micro-benchmark of the detector.
     * @return average inference time in milliseconds
     */
    external fun benchmarkDetector(): Long
}

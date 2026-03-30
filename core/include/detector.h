#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace privateai {

struct Detection {
    float x1, y1, x2, y2;  // bounding box (normalized 0-1)
    float confidence;
    int class_id;
};

/**
 * YOLOv8 object detector using ONNX Runtime.
 * This class is shared across Android, iOS, and Desktop.
 */
class Detector {
public:
    explicit Detector(const std::string& model_path);
    ~Detector();

    /**
     * Run detection on an RGB image.
     * @param rgb_data pointer to RGB byte array (H * W * 3)
     * @param width image width in pixels
     * @param height image height in pixels
     * @param confidence_threshold minimum confidence to keep (0.0-1.0)
     * @return vector of detections
     */
    std::vector<Detection> detect(
        const uint8_t* rgb_data,
        int width, int height,
        float confidence_threshold = 0.25f);

    /**
     * Run a micro-benchmark: 5 inference runs, return avg ms.
     */
    long benchmark();

    bool is_loaded() const { return loaded_; }

private:
    bool loaded_ = false;
    // ONNX Runtime session will be added when we integrate the runtime
    // For now, this is the scaffold
};

// COCO class names for YOLOv8
const std::vector<std::string>& get_coco_class_names();

} // namespace privateai

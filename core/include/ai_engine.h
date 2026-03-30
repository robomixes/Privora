#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace privateai {

/**
 * Base class for ONNX Runtime inference sessions.
 * Manages model loading and session lifecycle.
 */
class AIEngine {
public:
    AIEngine() = default;
    virtual ~AIEngine() = default;

    // Non-copyable
    AIEngine(const AIEngine&) = delete;
    AIEngine& operator=(const AIEngine&) = delete;
};

} // namespace privateai

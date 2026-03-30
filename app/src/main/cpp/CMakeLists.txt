cmake_minimum_required(VERSION 3.18)
project(privateai VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Core source files (shared cross-platform code)
set(CORE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../core/src/ai_engine.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../core/src/detector.cpp
)

# JNI bridge (Android-specific)
set(JNI_SOURCES
    native_bridge.cpp
)

add_library(privateai SHARED
    ${CORE_SOURCES}
    ${JNI_SOURCES}
)

target_include_directories(privateai PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../core/include
)

# Link Android log library
find_library(log-lib log)
target_link_libraries(privateai ${log-lib})

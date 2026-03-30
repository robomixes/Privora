cmake_minimum_required(VERSION 3.18)
project(privateai-core VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(privateai-core STATIC
    src/ai_engine.cpp
    src/detector.cpp
)

target_include_directories(privateai-core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Tests (desktop only, not Android)
option(BUILD_TESTS "Build tests" OFF)
if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(test)
endif()

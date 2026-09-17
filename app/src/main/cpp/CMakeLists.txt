cmake_minimum_required(VERSION 3.22.1)
project("llm_jni")

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(STATUS "Skipping llm_jni for unsupported ABI: ${ANDROID_ABI}")
    return()
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# CPU-only build, matching the spike exactly.
set(GGML_CURL OFF CACHE BOOL "" FORCE)
# common/ (llama-common target) is only added by llama.cpp's own CMakeLists.txt
# when LLAMA_BUILD_COMMON is ON, which defaults to LLAMA_STANDALONE (false when
# vendored via add_subdirectory like this). Force it on so the llama-common
# target we link against below actually exists.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/llama.cpp third_party/llama.cpp-build)

add_library(llm_jni SHARED
        llm_jni.cpp)

target_include_directories(llm_jni PRIVATE
        third_party/llama.cpp
        third_party/llama.cpp/common
        third_party/llama.cpp/include
        third_party/llama.cpp/ggml/include)

target_link_libraries(llm_jni
        llama
        llama-common
        android
        log)

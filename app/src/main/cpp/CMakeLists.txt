cmake_minimum_required(VERSION 3.22.1)
project("llm_jni")

add_library(llm_jni SHARED
        sanity_check.cpp)

target_link_libraries(llm_jni
        android
        log)

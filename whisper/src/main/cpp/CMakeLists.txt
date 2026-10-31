cmake_minimum_required(VERSION 3.22)
project(whisper_jni CXX C)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

# Static, CPU-only ggml. Everything is linked into one shared library and its symbols are hidden,
# so this ggml copy never clashes with the separate copy inside libllama_jni.so.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # Snapdragon 8 Gen 3 (Cortex-X4/A720/A520) supports dotprod, fp16 and i8mm.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)
endif()

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory(${WHISPER_DIR} whisper_build EXCLUDE_FROM_ALL)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni PRIVATE whisper)
if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(whisper_jni PRIVATE ${LOG_LIB})
    target_link_options(whisper_jni PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
else()
    find_package(JNI REQUIRED)
    target_include_directories(whisper_jni PRIVATE ${JNI_INCLUDE_DIRS})
endif()

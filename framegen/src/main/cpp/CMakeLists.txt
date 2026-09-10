cmake_minimum_required(VERSION 3.22.1)
project(zalith_framegen)

# Pulls in lsfg-vk-android's `framegen/` target. Path is relative to this file
# (framegen/src/main/cpp/CMakeLists.txt) — 4 levels up (cpp -> main -> src -> framegen)
# reaches the repo root, then into native/lsfg-vk-android.
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/lsfg-vk-android
    ${CMAKE_CURRENT_BINARY_DIR}/lsfg-vk-android
)

add_library(zalith_framegen_jni SHARED
    frame_gen_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(zalith_framegen_jni
    lsfg-vk-framegen   # confirmed via native/lsfg-vk-android/framegen/CMakeLists.txt —
                       # the "framegen" name I guessed earlier was just the directory,
                       # not a CMake target.
    ${log-lib}
    ${android-lib}
)

target_include_directories(zalith_framegen_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/lsfg-vk-android/framegen/public
)

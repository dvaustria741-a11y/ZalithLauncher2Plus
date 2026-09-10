cmake_minimum_required(VERSION 3.22.1)
project(zalith_framegen)

# Pulls in lsfg-vk-android's `framegen/` target — same relative-path pattern its own
# README documents for LSFG-Android-Application's JNI CMakeLists.txt, just from our
# module's cpp dir instead of theirs.
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native/lsfg-vk-android
    ${CMAKE_CURRENT_BINARY_DIR}/lsfg-vk-android
)

add_library(zalith_framegen_jni SHARED
    frame_gen_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(zalith_framegen_jni
    framegen   # TODO(dex): confirm this matches the actual target name lsfg-vk-android's
               # CMakeLists.txt exports — verify with `cmake --build . --target help` once
               # this builds locally; it wasn't obvious from the README alone.
    ${log-lib}
    ${android-lib}
)

target_include_directories(zalith_framegen_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native/lsfg-vk-android/framegen/public
)

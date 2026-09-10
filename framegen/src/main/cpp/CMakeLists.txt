cmake_minimum_required(VERSION 3.22.1)
project(zalith_framegen)

set(LSFG_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/lsfg-vk-android")
if(NOT EXISTS "${LSFG_ROOT}/framegen/CMakeLists.txt")
    message(FATAL_ERROR "Can't find lsfg-vk-android sources at ${LSFG_ROOT} — submodule not checked out?")
endif()

# -----------------------------------------------------------------------------
# volk — built by hand rather than via the submodule's own add_subdirectory(thirdparty/volk),
# copying LSFG-Android-Application/app/src/main/cpp/CMakeLists.txt's own approach exactly.
# This turned out to matter: the submodule's top-level CMakeLists.txt pulls in volk without
# VK_USE_PLATFORM_ANDROID_KHR defined, which is what caused the previous build failure
# (framegen/src/core/image.cpp couldn't see VkAndroidHardwareBufferFormatPropertiesANDROID
# and friends — those Vulkan structs are compiled out entirely without this define).
# -----------------------------------------------------------------------------
add_library(volk STATIC "${LSFG_ROOT}/thirdparty/volk/volk.c")
target_include_directories(volk PUBLIC "${LSFG_ROOT}/thirdparty/volk")
target_compile_definitions(volk PUBLIC VK_USE_PLATFORM_ANDROID_KHR=1)

# -----------------------------------------------------------------------------
# framegen only — NOT the submodule root. The root CMakeLists.txt also declares the
# full Linux-oriented `lsfg-vk` target (toml11 config file, dxbc, pe-parse, GTK-adjacent
# extraction code) which framegen/CMakeLists.txt's own target_link_libraries only needs
# `volk` for anyway. Matches how LSFG-Android-Application itself scopes this — its own
# CMakeLists.txt does the same add_subdirectory(.../framegen), not the submodule root,
# and reimplements Android-specific shader loading separately rather than reusing the
# Linux Extract/Config code path. We don't have that reimplementation yet — see
# framegen/README.md and the TODOs in frame_gen_jni.cpp.
# -----------------------------------------------------------------------------
add_subdirectory("${LSFG_ROOT}/framegen" "${CMAKE_CURRENT_BINARY_DIR}/framegen")

add_library(zalith_framegen_jni SHARED
    frame_gen_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(zalith_framegen_jni
    lsfg-vk-framegen
    ${log-lib}
    ${android-lib}
)

target_include_directories(zalith_framegen_jni PRIVATE
    "${LSFG_ROOT}/framegen/public"
)

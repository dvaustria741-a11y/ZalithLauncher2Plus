// Zalith Launcher 2 Plus
//
// JNI glue between FrameGenBridge.kt and lsfg-vk-android's public C++ API
// (native/lsfg-vk-android/framegen/public/lsfg_3_1.hpp, lsfg_3_1p.hpp).
//
// STATUS: scaffolding only. The function signatures below match the real public API
// (createContextFromAHB / Generate / waitIdle) as documented in that submodule's README,
// but the actual frame hand-off is NOT implemented — see the TODOs. Do not ship this
// without finishing and on-device testing each TODO.
//
// THE OPEN QUESTION THIS FILE DOESN'T ANSWER:
// lsfg-vk on Linux runs as an implicit Vulkan layer that intercepts vkQueuePresentKHR
// on someone else's swapchain. Zalith doesn't need the layer-injection trick LSFG-Android
// uses (MediaProjection + overlay) since it renders Minecraft itself, in-process — but it
// still needs *some* interception point that hands this code the two most recent rendered
// frames as AHardwareBuffers before they reach the display.
//
// For GL-based renderers (GL4ES, VirGL, MobileGlues) there's no Vulkan swapchain to hook at
// all — frame generation as built here only applies to the Kopper / VulkanZinkRenderer path.
// For that path, ZalithLauncher/src/main/jni/ctxbridges/ already has a precedent for this
// exact kind of interception: bridge_tbl.h's br_swap_buffers function-pointer table
// intercepts eglSwapBuffers for the GL renderers. Vulkan has no equivalent bridge in this
// repo yet. The likely shape of one, based on how driver_helper.c already redirects Vulkan
// driver loading: a small shim that LWJGL's dlopen("libvulkan.so") resolves to instead of
// the real loader, forwarding every function except vkQueuePresentKHR, which it intercepts
// to capture the outgoing swapchain image before presenting it. That shim does not exist
// yet and is NOT part of this scaffold — confirming this approach even works (Android may
// impose restrictions here that aren't obvious from source alone) needs to happen on a real
// Adreno 7xx+ device before more code gets written against it.

#include <jni.h>
#include <android/hardware_buffer_jni.h>
#include <android/log.h>

#include "lsfg_3_1.hpp"

#define LOG_TAG "ZalithFrameGen"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Mirrors LSFG_3_1::createContextFromAHB. dllPath is a filesystem path to the user's own
// Lossless.dll (see FrameGenerationSetting's DLL picker) — lsfg-vk-android's pe-parse step
// extracts the shader chain from it on first call.
//
// TODO(dex): lsfg_vk's createContextFromAHB signature in the real header also wants
// per-buffer format/extent info, not just raw AHardwareBuffer* — check
// native/lsfg-vk-android/framegen/public/lsfg_3_1.hpp directly once this is building,
// this stub guesses at a plausible shape rather than committing to one blind.
JNIEXPORT jlong JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeCreateContext(
    JNIEnv *env,
    jclass clazz,
    jstring dllPath
) {
    (void) clazz;
    const char *path = env->GetStringUTFChars(dllPath, nullptr);
    LOGI("nativeCreateContext: dll=%s (STUB — not calling into lsfg-vk-android yet)", path);
    env->ReleaseStringUTFChars(dllPath, path);

    // TODO(dex): actually call LSFG_3_1::createContextFromAHB(...) here once we know
    // where the input/output AHardwareBuffers are coming from (see file header). Returning
    // 0 makes every other call in this file a safe no-op until this is filled in.
    return 0;
}

// Mirrors LSFG_3_1::Generate. Takes the context handle from nativeCreateContext plus the
// two most recently rendered frames, and is meant to return an interpolated frame.
//
// TODO(dex): this is the actual frame hand-off and it is completely unimplemented. It
// needs a real AHardwareBuffer for `previousFrame`/`currentFrame`, which nothing in this
// scaffold produces yet — see the Vulkan present-hook question at the top of this file.
JNIEXPORT jobject JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeGenerate(
    JNIEnv *env,
    jclass clazz,
    jlong contextHandle,
    jobject previousFrame,
    jobject currentFrame
) {
    (void) clazz;
    (void) previousFrame;
    (void) currentFrame;

    if (contextHandle == 0) {
        LOGE("nativeGenerate: context not initialized (createContext returned 0)");
        return nullptr;
    }

    // TODO(dex): call LSFG_3_1::Generate(...) with real AHardwareBuffer-backed images.
    LOGI("nativeGenerate: STUB — returning null, no interpolated frame produced");
    return nullptr;
}

// Mirrors LSFG_3_1::waitIdle — exposed so a host Vulkan session sharing the same
// AHardwareBuffers can synchronize without a cross-device semaphore (per the submodule's
// README; Vulkan doesn't define one). Safe to wire up once nativeCreateContext is real.
JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeWaitIdle(
    JNIEnv *env,
    jclass clazz,
    jlong contextHandle
) {
    (void) env;
    (void) clazz;
    if (contextHandle == 0) return;
    // TODO(dex): call LSFG_3_1::waitIdle() on the real context.
}

JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeDestroyContext(
    JNIEnv *env,
    jclass clazz,
    jlong contextHandle
) {
    (void) env;
    (void) clazz;
    if (contextHandle == 0) return;
    // TODO(dex): free the real context once nativeCreateContext produces one.
    LOGI("nativeDestroyContext: STUB");
}

} // extern "C"

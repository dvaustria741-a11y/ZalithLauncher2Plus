// Zalith Launcher 2 Plus
//
// JNI glue between FrameGenBridge.kt and lsfg-vk-android's public C++ API
// (native/lsfg-vk-android/framegen/public/lsfg_3_1.hpp).
//
// STATUS: scaffolding only, rewritten once against the *actual* header contents
// instead of guessing — the earlier version of this file had the wrong shape entirely
// (a Context-handle-per-call model; the real API is a global initialize() + a
// per-swapchain int32_t context id + semaphore-based presentContext()). Still not
// functional — see the TODOs, and framegen/README.md for what's still missing.
//
// REAL API SHAPE (from lsfg_3_1.hpp, verified against the submodule directly):
//   initialize(deviceUUID, isHdr, flowScale, generationCount, loader) — once, globally.
//     `loader` is std::function<vector<uint8_t>(string)> — given a shader name, return
//     its bytecode. NOT implemented here yet (see nativeInitialize TODO below): it needs
//     Extract::getShader() from native/lsfg-vk-android's src/extract/*.cpp, which this
//     module does NOT currently link against (only lsfg-vk-framegen, not the full
//     lsfg-vk target that owns extraction). That's the next real blocker, not faked here.
//   createContextFromAHB(in0, in1, outN, extent, format) -> int32_t context id.
//   presentContext(id, inSem, outSem) — the actual per-frame generation call. Takes
//     raw fd-based semaphores, not a synchronous "hand me a buffer back" call.
//   deleteContext(id), finalize(), waitIdle().
//
// STILL OPEN, CONFIRMED WHILE READING THE SOURCE (not guesses):
//   - Extract::extractShaders()/getShader() live in native/lsfg-vk-android's
//     src/extract/*.cpp (part of the `lsfg-vk` target, not `lsfg-vk-framegen`), and
//     depend on pe-parse + dxbc + toml11 — none of which this CMakeLists.txt links yet.
//   - Config::dll (native/lsfg-vk-android/include/config/config.hpp) is how that code
//     finds Lossless.dll: either a TOML config file, or the LSFG_DLL_PATH env var
//     (src/config/config.cpp). Android has no equivalent of the Linux config file path
//     it defaults to, so this glue would need to setenv("LSFG_DLL_PATH", ...) itself
//     before calling extractShaders() — see nativeInitialize TODO.
//   - That env var needs a real filesystem path, not a content:// URI. FrameGenDllPicker
//     currently only stores the SAF URI — it will need to copy the file into app-internal
//     storage before this can work at all. Not done yet.
//   - The swapchain-interception problem from the original scaffold notes is still
//     completely unsolved — createContextFromAHB/presentContext need real AHardwareBuffers
//     from Minecraft's actual rendered frames, and nothing produces those yet.

#include <jni.h>
#include <android/hardware_buffer_jni.h>
#include <android/log.h>

#include "lsfg_3_1.hpp"

#define LOG_TAG "ZalithFrameGen"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Mirrors LSFG_3_1::initialize. dllPath is a REAL FILESYSTEM PATH (not a content:// URI —
// see FrameGenDllPicker TODO) to the user's own, legitimately-owned Lossless.dll.
//
// TODO(dex): completely unimplemented. Needs to:
//   1. setenv("LSFG_DLL_PATH", dllPath, 1) so Extract::extractShaders() can find it
//      (matches src/config/config.cpp's env-var override path).
//   2. Link this module against native/lsfg-vk-android's src/extract/*.cpp + its deps
//      (pe-parse, dxbc, toml11) — not currently in this CMakeLists.txt at all.
//   3. Call Extract::extractShaders(), then pass Extract::getShader as the `loader`
//      std::function to LSFG_3_1::initialize(), along with a real Vulkan deviceUUID
//      (from the app's own VkPhysicalDevice — not sourced anywhere in this scaffold yet).
// Currently a no-op that always reports failure so nothing downstream can silently
// proceed as if it succeeded.
JNIEXPORT jboolean JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeInitialize(
    JNIEnv *env,
    jclass clazz,
    jstring dllPath
) {
    (void) clazz;
    const char *path = env->GetStringUTFChars(dllPath, nullptr);
    LOGI("nativeInitialize: dll=%s (STUB — extraction pipeline not linked in yet)", path);
    env->ReleaseStringUTFChars(dllPath, path);
    return JNI_FALSE;
}

// Mirrors LSFG_3_1::createContextFromAHB. Needs real AHardwareBuffers backing Minecraft's
// actual rendered frames — nothing in this scaffold produces those (see the Vulkan
// present-hook question in framegen/README.md). Always returns -1 (invalid context id).
JNIEXPORT jint JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeCreateContext(
    JNIEnv *env,
    jclass clazz,
    jobject in0,
    jobject in1,
    jint width,
    jint height
) {
    (void) env;
    (void) clazz;
    (void) in0;
    (void) in1;
    (void) width;
    (void) height;
    LOGE("nativeCreateContext: STUB — nativeInitialize was never really run, refusing");
    return -1;
}

// Mirrors LSFG_3_1::presentContext. TODO(dex): needs real fd-based semaphores from
// whatever ends up doing the actual swapchain interception — unimplemented.
JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativePresent(
    JNIEnv *env,
    jclass clazz,
    jint contextId
) {
    (void) env;
    (void) clazz;
    if (contextId < 0) return;
    LOGI("nativePresent: STUB — no-op");
}

JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeDeleteContext(
    JNIEnv *env,
    jclass clazz,
    jint contextId
) {
    (void) env;
    (void) clazz;
    if (contextId < 0) return;
    LOGI("nativeDeleteContext: STUB");
}

JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeWaitIdle(
    JNIEnv *env,
    jclass clazz
) {
    (void) env;
    (void) clazz;
}

JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_framegen_FrameGenBridge_nativeFinalize(
    JNIEnv *env,
    jclass clazz
) {
    (void) env;
    (void) clazz;
    LOGI("nativeFinalize: STUB");
}

} // extern "C"

/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.framegen

import android.hardware.HardwareBuffer

/**
 * Kotlin side of the lsfg-vk-android JNI bridge.
 *
 * STATUS: scaffolding — see frame_gen_jni.cpp for exactly what's stubbed. Every method here
 * is safe to call (won't crash), but [createContext] currently always fails and every other
 * method becomes a no-op as a result. Nothing here has been tested against a real Adreno
 * device or a real Lossless.dll.
 */
object FrameGenBridge {

    init {
        System.loadLibrary("zalith_framegen_jni")
    }

    /**
     * @param dllPath filesystem path to the user's own, legitimately-owned Lossless.dll.
     *   This project never bundles or redistributes that file — see FrameGenerationSetting's
     *   DLL picker, which just stores whatever path the user selects via SAF.
     * @return an opaque context handle, or 0 if creation failed (always the case right now).
     */
    fun createContext(dllPath: String): Long =
        nativeCreateContext(dllPath)

    /**
     * @return an interpolated frame, or null if generation isn't available (always the case
     *   right now — see the TODOs in frame_gen_jni.cpp for what's missing).
     */
    fun generate(contextHandle: Long, previousFrame: HardwareBuffer, currentFrame: HardwareBuffer): HardwareBuffer? =
        nativeGenerate(contextHandle, previousFrame, currentFrame)

    fun waitIdle(contextHandle: Long) =
        nativeWaitIdle(contextHandle)

    fun destroyContext(contextHandle: Long) =
        nativeDestroyContext(contextHandle)

    @JvmStatic private external fun nativeCreateContext(dllPath: String): Long
    @JvmStatic private external fun nativeGenerate(contextHandle: Long, previousFrame: HardwareBuffer, currentFrame: HardwareBuffer): HardwareBuffer?
    @JvmStatic private external fun nativeWaitIdle(contextHandle: Long)
    @JvmStatic private external fun nativeDestroyContext(contextHandle: Long)
}

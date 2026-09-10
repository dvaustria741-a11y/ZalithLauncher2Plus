/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.framegen

import android.hardware.HardwareBuffer

/**
 * Kotlin side of the lsfg-vk-android JNI bridge.
 *
 * STATUS: scaffolding — see frame_gen_jni.cpp for exactly what's stubbed. [initialize]
 * always returns false right now, and everything else is a no-op as a result. Nothing
 * here has been tested against a real Adreno device or a real Lossless.dll.
 */
object FrameGenBridge {

    init {
        System.loadLibrary("zalith_framegen_jni")
    }

    /**
     * @param dllPath a REAL FILESYSTEM PATH to the user's own, legitimately-owned
     *   Lossless.dll — NOT the content:// URI FrameGenDllPicker currently stores. Native
     *   code can't read SAF content URIs directly, so whatever calls this still needs to
     *   copy the picked file into app-internal storage first. Not done yet.
     * @return whether initialization succeeded. Always false right now.
     */
    fun initialize(dllPath: String): Boolean =
        nativeInitialize(dllPath)

    /** @return a context id, or -1 if creation failed (always the case right now). */
    fun createContext(in0: HardwareBuffer, in1: HardwareBuffer, width: Int, height: Int): Int =
        nativeCreateContext(in0, in1, width, height)

    fun present(contextId: Int) =
        nativePresent(contextId)

    fun deleteContext(contextId: Int) =
        nativeDeleteContext(contextId)

    fun waitIdle() =
        nativeWaitIdle()

    fun finalizeEngine() =
        nativeFinalize()

    @JvmStatic private external fun nativeInitialize(dllPath: String): Boolean
    @JvmStatic private external fun nativeCreateContext(in0: HardwareBuffer, in1: HardwareBuffer, width: Int, height: Int): Int
    @JvmStatic private external fun nativePresent(contextId: Int)
    @JvmStatic private external fun nativeDeleteContext(contextId: Int)
    @JvmStatic private external fun nativeWaitIdle()
    @JvmStatic private external fun nativeFinalize()
}

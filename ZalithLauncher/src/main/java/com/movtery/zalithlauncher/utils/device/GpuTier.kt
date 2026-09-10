/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.utils.device

import android.opengl.GLES20

/**
 * GPU capability tiers for compute-heavy features (e.g. frame interpolation / frame generation).
 *
 * Frame generation runs an extra compute pass every frame (optical-flow estimation between
 * two rendered frames) before it can present an interpolated one. On a GPU that's already
 * the bottleneck, that pass can consume more headroom than the interpolation buys back —
 * so this is a real capability gate, not just a cosmetic label.
 */
enum class GpuTier {
    /** GPU string could not be read or matched against a known family. */
    UNSUPPORTED,

    /** Identified, but below the tier where interpolation overhead reliably nets a gain. */
    LOW,

    /** Should see a real (if modest) gain; some headroom cost expected. */
    MID,

    /** Recommended tier — enough compute headroom for interpolation to be close to "free". */
    HIGH;

    val allowsFrameGeneration: Boolean
        get() = this == MID || this == HIGH
}

data class GpuInfo(
    val vendor: String,
    val renderer: String,
    val tier: GpuTier
)

/**
 * Caches the last [GpuInfo] detected from a live GL context (currently populated from
 * [com.movtery.zalithlauncher.ui.screens.content.settings.BenchmarkGLRenderer]'s surface,
 * since that's the one GL context the settings UI already has a reason to open).
 *
 * Until the app opens a GL/Vulkan context earlier in its lifecycle (e.g. at launch) and
 * detects here instead, this stays `null` for a user who hasn't run the benchmark yet —
 * [FrameGenerationSetting] treats that the same as [GpuTier.UNSUPPORTED].
 */
object DetectedGpuHolder {
    var info: GpuInfo? = null
        private set

    fun update(newInfo: GpuInfo) {
        info = newInfo
    }
}

object GpuTierDetector {

    // Adreno 6xx below ~660 (e.g. Adreno 619 on Snapdragon 695) lacks the compute
    // throughput to run optical-flow interpolation without eating most of its own gains.
    // 725+ is the tier the existing Android frame-gen mods (LSFG-style, root/zygisk based)
    // themselves recommend as the safe floor.
    private val ADRENO_REGEX = Regex("""Adreno\s*\(TM\)?\s*(\d{3})""", RegexOption.IGNORE_CASE)
    private val MALI_REGEX = Regex("""Mali-G(\d{2,3})""", RegexOption.IGNORE_CASE)

    /**
     * Must be called from inside an active GL context (e.g. GLSurfaceView.Renderer.onSurfaceCreated),
     * since GL_VENDOR / GL_RENDERER only return valid strings once a context is current.
     * [BenchmarkGLRenderer] already establishes one for the perf-benchmark screen — reuse that
     * context rather than spinning up a second throwaway surface.
     */
    fun detect(): GpuInfo {
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "unknown"
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
        return GpuInfo(vendor, renderer, classify(renderer))
    }

    private fun classify(renderer: String): GpuTier {
        ADRENO_REGEX.find(renderer)?.let { match ->
            val model = match.groupValues[1].toIntOrNull() ?: return GpuTier.UNSUPPORTED
            return when {
                model >= 725 -> GpuTier.HIGH
                model >= 660 -> GpuTier.MID
                else -> GpuTier.LOW // covers Adreno 619 and similar
            }
        }
        MALI_REGEX.find(renderer)?.let { match ->
            val model = match.groupValues[1].toIntOrNull() ?: return GpuTier.UNSUPPORTED
            return when {
                model >= 78 -> GpuTier.HIGH // Mali-G78 / Valhall and newer
                model >= 68 -> GpuTier.MID
                else -> GpuTier.LOW
            }
        }
        return GpuTier.UNSUPPORTED
    }
}

/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.KryptonWrapperTestConfig

/**
 * Krypton Wrapper (Test) — 与正式版 Krypton Wrapper 共用同一个 libng_gl4es.so，
 * 但拥有独立的渲染器 ID、独立的设置/配置文件，互不干扰。
 * 用于试验额外的 GL4ES 性能调优开关（VSync、批处理、VAO、FBO 等），
 * 出问题时可直接切回正式版 Krypton Wrapper。
 */
object KryptonWrapperTestRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_test"
    override fun getUniqueIdentifier(): String = "a93c2b04-9f3e-4eda-93ad-1e6f3225bb84"
    override fun getRendererName(): String = "Krypton Wrapper (Test)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = KryptonWrapperTestConfig.load() ?: KryptonWrapperTestConfig()
        buildMap {
            if (cfg.useMcColor) put("LIBGL_USE_MC_COLOR", "1")
            // Explicitly set — prevents GameLauncher's id-based auto-derivation of LIBGL_ES
            put("LIBGL_GL", cfg.glLevel.toString())
            put("LIBGL_ES", cfg.esVersion.toString())
            if (cfg.normalize) put("LIBGL_NORMALIZE", "1")
            if (cfg.noError)   put("LIBGL_NOERROR",   "1")
            put("LIBGL_NOINTOVLHACK", "1")
            put("LIBGL_MIPMAP", "3")

            // --- Experimental performance tuning (Test build only) ---
            if (cfg.vsyncOff) put("LIBGL_VSYNC", "0")
            if (cfg.batch) put("LIBGL_BATCH", "1")
            put("LIBGL_DEFERRED_FLUSH", cfg.deferredFlush.toString())
            if (cfg.useVAO) put("LIBGL_USEVAO", "1")
            put("LIBGL_FBO", cfg.fboMode.toString())
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }
    override fun getRendererLibrary(): String = "libng_gl4es.so"
}

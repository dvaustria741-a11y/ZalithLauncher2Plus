/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.KryptonWrapperConfig

object NGGL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3"
    override fun getUniqueIdentifier(): String = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31"
    override fun getRendererName(): String = "Krypton Wrapper"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = KryptonWrapperConfig.load() ?: KryptonWrapperConfig()
        buildMap {
            if (cfg.useMcColor) put("LIBGL_USE_MC_COLOR", "1")
            put("LIBGL_GL",   cfg.glLevel.toString())
            put("LIBGL_ES",   cfg.esVersion.toString())
            if (cfg.normalize) put("LIBGL_NORMALIZE", "1")
            if (cfg.noError)   put("LIBGL_NOERROR",   "1")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }
    override fun getRendererLibrary(): String = "libng_gl4es.so"
}

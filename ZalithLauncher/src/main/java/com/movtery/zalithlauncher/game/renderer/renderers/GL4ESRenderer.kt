/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.GL4ESConfig

object GL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles2"
    override fun getUniqueIdentifier(): String = "8b52d82d-8f6d-4d3a-a767-dc93f8b72fc7"
    override fun getRendererName(): String = "GL4ES"
    override fun getMaxMCVersion(): String = "1.21.4"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = GL4ESConfig.load() ?: GL4ESConfig()
        buildMap {
            put("LIBGL_MIPMAP", cfg.mipmapLevel.toString())
            if (cfg.normalize) put("LIBGL_NORMALIZE", "1")
            if (cfg.noBanner)  put("LIBGL_NOBANNER",  "1")
            if (cfg.noError)   put("LIBGL_NOERROR",   "1")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }
    override fun getRendererLibrary(): String = "libgl4es_114.so"
}

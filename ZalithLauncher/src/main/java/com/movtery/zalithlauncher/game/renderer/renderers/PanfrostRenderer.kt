/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import android.os.Environment
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.PanfrostConfig

object PanfrostRenderer : RendererInterface {
    override fun getRendererId(): String = "gallium_panfrost"
    override fun getUniqueIdentifier(): String = "9b2808c4-11af-4c72-a9c6-94c940396475"
    override fun getMaxMCVersion(): String = "1.21.4"
    override fun getRendererName(): String = "Panfrost (Mali)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = PanfrostConfig.load() ?: PanfrostConfig()
        val cacheDir = "${Environment.getExternalStorageDirectory().absolutePath}/.cache/mesa"
        buildMap {
            put("MESA_GL_VERSION_OVERRIDE",   cfg.glVersionOverride)
            put("MESA_GLSL_VERSION_OVERRIDE", cfg.glslVersionOverride)
            put("MESA_GLSL_CACHE_DIR",  cacheDir)
            put("MESA_SHADER_CACHE_DIR", cacheDir)
            if (cfg.noError)  put("MESA_NO_ERROR", "1")
            put("LIBGL_MIPMAP", cfg.mipmapLevel.toString())
            if (cfg.glThread) {
                put("MESA_GLTHREAD", "true")
                put("mesa_glthread",  "true")
            }
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }
    override fun getRendererLibrary(): String = "libOSMesa_2300d.so"
}

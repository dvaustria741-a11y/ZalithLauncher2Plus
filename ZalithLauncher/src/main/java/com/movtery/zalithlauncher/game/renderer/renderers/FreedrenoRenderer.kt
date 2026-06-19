/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import android.os.Environment
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.FreedrenoConfig

object FreedrenoRenderer : RendererInterface {
    override fun getRendererId(): String = "gallium_freedreno"
    override fun getUniqueIdentifier(): String = "1ad7249f-5784-4f00-bc72-174b3578ee46"
    override fun getRendererName(): String = "Freedreno (Adreno)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = FreedrenoConfig.load() ?: FreedrenoConfig()
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
    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}

/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import android.os.Environment
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.VulkanZinkConfig

object VulkanZinkRenderer : RendererInterface {
    override fun getRendererId(): String = "vulkan_zink"

    override fun getUniqueIdentifier(): String = "0fa435e2-46df-45c9-906c-b29606aaef00"

    override fun getRendererName(): String = "Vulkan Zink"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = VulkanZinkConfig.load() ?: VulkanZinkConfig()
        val cacheDir = "${Environment.getExternalStorageDirectory().absolutePath}/.cache/mesa"
        buildMap {
            put("MESA_GL_VERSION_OVERRIDE",   cfg.glVersionOverride)
            put("MESA_GLSL_VERSION_OVERRIDE", cfg.glslVersionOverride)
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
            // Shader disk cache – avoids recompiling on every launch
            put("MESA_GLSL_CACHE_DIR", cacheDir)
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

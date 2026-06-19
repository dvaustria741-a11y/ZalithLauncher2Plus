/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import android.os.Environment
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.VulkanZinkConfig

object KopperZinkRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_desktopgl_zink_kopper"

    override fun getUniqueIdentifier(): String = "7c3a91f2-bb4e-4e8d-9f21-c45d38a7b201"

    override fun getRendererName(): String = "Kopper Zink (Vulkan)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = VulkanZinkConfig.load() ?: VulkanZinkConfig()
        val cacheDir = "${Environment.getExternalStorageDirectory().absolutePath}/.cache/mesa"
        buildMap {
            // Use Mesa's own EGL instead of Android system EGL — key to Kopper WSI path
            put("POJAVEXEC_EGL", "libEGL_mesa.so")
            put("MESA_GL_VERSION_OVERRIDE",   cfg.glVersionOverride)
            put("MESA_GLSL_VERSION_OVERRIDE", cfg.glslVersionOverride)
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
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

    // libglxshim.so is the Kopper/Mesa-EGL GLX shim — bypasses Android EGL for Vulkan WSI
    override fun getRendererLibrary(): String = "libglxshim.so"
}

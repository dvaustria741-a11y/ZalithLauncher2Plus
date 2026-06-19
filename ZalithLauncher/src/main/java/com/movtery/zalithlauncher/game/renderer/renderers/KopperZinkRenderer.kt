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

    // Kopper Zink: uses Mesa EGL for eglBindAPI(EGL_OPENGL_API), but on some Adreno devices
    // that bind fails (EGL_BAD_PARAMETER). The launcher now routes Kopper through the
    // Vulkan/Zink OSMesa path (same as Vulkan Zink) to bypass the EGL binding issue entirely.
    override fun getRendererSummary(): String =
        "Vulkan-backed renderer using Mesa Zink. Routes through the OSMesa path on Adreno to " +
        "avoid EGL OpenGL API binding failures. Falls back gracefully like Vulkan Zink."

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val cfg = VulkanZinkConfig.load() ?: VulkanZinkConfig()
        val cacheDir = "${Environment.getExternalStorageDirectory().absolutePath}/.cache/mesa"
        buildMap {
            // Use Mesa\'s own EGL — enables the Kopper WSI path directly to Vulkan
            put("POJAVEXEC_EGL", "libEGL_mesa.so")

            // Explicitly set to 3 — prevents the launcher auto-deriving the garbage value
            // "3_desktopgl_zink_kopper" from the renderer ID string (GameLauncher line 409)
            put("LIBGL_ES", "3")

            put("MESA_GL_VERSION_OVERRIDE",   cfg.glVersionOverride)
            put("MESA_GLSL_VERSION_OVERRIDE", cfg.glslVersionOverride)
            // LIB_MESA_NAME must be set explicitly: GameLauncher.kt only injects it
            // for non-opengles renderer IDs, but our ID starts with "opengles", so
            // osmesa_loader.c would read getenv("LIB_MESA_NAME") as NULL and abort.
            put("LIB_MESA_NAME", "libOSMesa_8.so")
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
            put("MESA_GLSL_CACHE_DIR",  cacheDir)
            put("MESA_SHADER_CACHE_DIR", cacheDir)

            // GLSL extension vars — normally only set for non-opengles renderers by the launcher
            // (GameLauncher.setRendererEnv line 392) but skipped because our ID starts with opengles
            put("force_glsl_extensions_warn",             "true")
            put("allow_higher_compat_version",            "true")
            put("allow_glsl_extension_directive_midshader", "true")

            if (cfg.noError)  put("MESA_NO_ERROR", "1")
            put("LIBGL_MIPMAP", cfg.mipmapLevel.toString())
            if (cfg.glThread) {
                put("MESA_GLTHREAD", "true")
                put("mesa_glthread",  "true")
            }
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    // Use libOSMesa_8.so: Kopper now routes through the OSMesa/Zink path (see egl_bridge.c),
    // so the renderer library must match — libglxshim.so would conflict with OSMesa GL dispatch.
    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}

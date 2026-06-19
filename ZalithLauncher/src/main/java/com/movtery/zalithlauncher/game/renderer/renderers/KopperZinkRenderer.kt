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

    // Kopper needs Mesa's EGL to bind the desktop GL API (eglBindAPI(EGL_OPENGL_API)).
    // On some Adreno devices/driver builds that bind fails (EGL_BAD_PARAMETER) and silently
    // falls back to the native vendor GLES driver instead, which doesn't implement the
    // desktop-only GL calls Minecraft's renderer always makes — this crashes on first texture
    // creation ("OpenGL error 1281: non-compressed internal format is invalid") during init.
    // Seen on: Adreno 619. If startup crashes here, try "Vulkan Zink" instead.
    override fun getRendererSummary(): String =
        "Experimental — may fail to initialize on some Adreno devices and crash during startup. " +
        "If that happens, try \"Vulkan Zink\" instead."

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

    // libglxshim.so is the Kopper/Mesa-EGL GLX shim — bypasses Android EGL for Vulkan WSI
    override fun getRendererLibrary(): String = "libglxshim.so"
}

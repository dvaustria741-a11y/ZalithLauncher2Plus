/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.utils.settings

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class VulkanZinkConfig private constructor(private var isInitializing: Boolean) {

    constructor() : this(false)

    /** MESA_GL_VERSION_OVERRIDE – e.g. "4.6" */
    var glVersionOverride: String = "4.6"
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** Derived automatically from glVersionOverride */
    val glslVersionOverride: String
        get() = glVersionOverride.replace(".", "") + "0"   // "4.6" → "460"

    /** MESA_NO_ERROR – skip GL error checking for performance */
    var noError: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_MIPMAP – 0=off 1=nearest 2=linear 3=trilinear */
    var mipmapLevel: Int = 3
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** MESA_GLTHREAD – offload GL calls to a background thread */
    var glThread: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    private fun saveIfReady() { if (!isInitializing) save() }

    fun save() {
        runCatching {
            val f = File(CONFIG_FILE_PATH)
            f.parentFile?.mkdirs()
            f.writeText(Gson().toJson(mapOf(
                "glVersionOverride" to glVersionOverride,
                "noError"           to noError,
                "mipmapLevel"       to mipmapLevel,
                "glThread"          to glThread
            )))
        }
    }

    companion object {
        val CONFIG_FILE_PATH: String
            get() = "${Environment.getExternalStorageDirectory().absolutePath}/ZinkConfig/config.json"

        fun load(): VulkanZinkConfig? {
            val f = File(CONFIG_FILE_PATH)
            if (!f.exists()) return null
            val text = runCatching { f.readText() }.getOrNull() ?: return null
            return runCatching {
                val obj: JsonObject = JsonParser.parseString(text).asJsonObject
                val cfg = VulkanZinkConfig(isInitializing = true)
                fun JsonObject.str(k: String, d: String) = get(k)?.asString ?: d
                fun JsonObject.bool(k: String, d: Boolean) = get(k)?.asBoolean ?: d
                fun JsonObject.int(k: String, d: Int) = get(k)?.asInt ?: d
                cfg.glVersionOverride = obj.str("glVersionOverride", "4.6")
                cfg.noError           = obj.bool("noError", true)
                cfg.mipmapLevel       = obj.int("mipmapLevel", 3)
                cfg.glThread          = obj.bool("glThread", true)
                cfg.isInitializing    = false
                cfg
            }.getOrNull()
        }
    }
}

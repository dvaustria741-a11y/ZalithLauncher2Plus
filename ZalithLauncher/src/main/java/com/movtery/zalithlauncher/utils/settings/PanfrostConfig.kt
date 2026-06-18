/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.utils.settings

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class PanfrostConfig private constructor(private var isInitializing: Boolean) {
    constructor() : this(false)

    var glVersionOverride: String = "4.6"
        set(value) { if (field != value) { field = value; saveIfReady() } }

    val glslVersionOverride: String
        get() = glVersionOverride.replace(".", "") + "0"

    var noError: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    var mipmapLevel: Int = 3
        set(value) { if (field != value) { field = value; saveIfReady() } }

    var glThread: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    private fun saveIfReady() { if (!isInitializing) save() }

    fun save() {
        runCatching {
            val f = File(CONFIG_FILE_PATH); f.parentFile?.mkdirs()
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
            get() = "${Environment.getExternalStorageDirectory().absolutePath}/PanfrostConfig/config.json"

        fun load(): PanfrostConfig? {
            val f = File(CONFIG_FILE_PATH)
            if (!f.exists()) return null
            val text = runCatching { f.readText() }.getOrNull() ?: return null
            return runCatching {
                val obj: JsonObject = JsonParser.parseString(text).asJsonObject
                val cfg = PanfrostConfig(isInitializing = true)
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

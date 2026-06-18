/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.utils.settings

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class KryptonWrapperConfig private constructor(private var isInitializing: Boolean) {
    constructor() : this(false)

    /** LIBGL_GL – base GL level (21 = ES 2.1, 31 = ES 3.1) */
    var glLevel: Int = 31
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_ES – ES version (2 or 3) */
    var esVersion: Int = 3
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_USE_MC_COLOR – use Minecraft color mode */
    var useMcColor: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NORMALIZE */
    var normalize: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NOERROR */
    var noError: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    private fun saveIfReady() { if (!isInitializing) save() }

    fun save() {
        runCatching {
            val f = File(CONFIG_FILE_PATH); f.parentFile?.mkdirs()
            f.writeText(Gson().toJson(mapOf(
                "glLevel"    to glLevel,
                "esVersion"  to esVersion,
                "useMcColor" to useMcColor,
                "normalize"  to normalize,
                "noError"    to noError
            )))
        }
    }

    companion object {
        val CONFIG_FILE_PATH: String
            get() = "${Environment.getExternalStorageDirectory().absolutePath}/KryptonConfig/config.json"

        fun load(): KryptonWrapperConfig? {
            val f = File(CONFIG_FILE_PATH)
            if (!f.exists()) return null
            val text = runCatching { f.readText() }.getOrNull() ?: return null
            return runCatching {
                val obj: JsonObject = JsonParser.parseString(text).asJsonObject
                val cfg = KryptonWrapperConfig(isInitializing = true)
                fun JsonObject.bool(k: String, d: Boolean) = get(k)?.asBoolean ?: d
                fun JsonObject.int(k: String, d: Int) = get(k)?.asInt ?: d
                cfg.glLevel    = obj.int("glLevel", 31)
                cfg.esVersion  = obj.int("esVersion", 3)
                cfg.useMcColor = obj.bool("useMcColor", true)
                cfg.normalize  = obj.bool("normalize", true)
                cfg.noError    = obj.bool("noError", true)
                cfg.isInitializing = false
                cfg
            }.getOrNull()
        }
    }
}

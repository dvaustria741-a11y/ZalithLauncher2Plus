/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.utils.settings

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class GL4ESConfig private constructor(private var isInitializing: Boolean) {
    constructor() : this(false)

    /** LIBGL_MIPMAP 0=off 1=nearest 2=linear 3=trilinear */
    var mipmapLevel: Int = 3
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NORMALIZE – normalize GL color components */
    var normalize: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NOERROR – skip GL error checking for performance */
    var noError: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NOBANNER – suppress GL4ES startup banner */
    var noBanner: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    private fun saveIfReady() { if (!isInitializing) save() }

    fun save() {
        runCatching {
            val f = File(CONFIG_FILE_PATH); f.parentFile?.mkdirs()
            f.writeText(Gson().toJson(mapOf(
                "mipmapLevel" to mipmapLevel,
                "normalize"   to normalize,
                "noError"     to noError,
                "noBanner"    to noBanner
            )))
        }
    }

    companion object {
        val CONFIG_FILE_PATH: String
            get() = "${Environment.getExternalStorageDirectory().absolutePath}/GL4ESConfig/config.json"

        fun load(): GL4ESConfig? {
            val f = File(CONFIG_FILE_PATH)
            if (!f.exists()) return null
            val text = runCatching { f.readText() }.getOrNull() ?: return null
            return runCatching {
                val obj: JsonObject = JsonParser.parseString(text).asJsonObject
                val cfg = GL4ESConfig(isInitializing = true)
                fun JsonObject.bool(k: String, d: Boolean) = get(k)?.asBoolean ?: d
                fun JsonObject.int(k: String, d: Int) = get(k)?.asInt ?: d
                cfg.mipmapLevel = obj.int("mipmapLevel", 3)
                cfg.normalize   = obj.bool("normalize", true)
                cfg.noError     = obj.bool("noError", true)
                cfg.noBanner    = obj.bool("noBanner", true)
                cfg.isInitializing = false
                cfg
            }.getOrNull()
        }
    }
}

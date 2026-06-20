/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.utils.settings

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Krypton Wrapper (Test) 的独立配置
 * 与正式版 [KryptonWrapperConfig] 完全隔离，互不影响，
 * 用于安全试验额外的性能调优开关，出问题时切回正式版即可
 */
class KryptonWrapperTestConfig private constructor(private var isInitializing: Boolean) {
    constructor() : this(false)

    /** LIBGL_GL – base GL level (21 = ES 2.1, 31 = ES 3.1) */
    var glLevel: Int = 31
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_ES – ES version (2 or 3) */
    var esVersion: Int = 3
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_USE_MC_COLOR */
    var useMcColor: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NORMALIZE */
    var normalize: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_NOERROR */
    var noError: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_VSYNC=0 – removes the compositor's vsync wait, uncaps FPS above refresh rate */
    var vsyncOff: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_BATCH – batches immediate-mode draw call emulation into fewer GPU submits */
    var batch: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_DEFERRED_FLUSH – number of draws to buffer before forcing a glFlush */
    var deferredFlush: Int = 8
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_USEVAO – use Vertex Array Objects instead of client-side arrays */
    var useVAO: Boolean = true
        set(value) { if (field != value) { field = value; saveIfReady() } }

    /** LIBGL_FBO – force the FBO offscreen path (2 = always use FBO) */
    var fboMode: Int = 2
        set(value) { if (field != value) { field = value; saveIfReady() } }

    private fun saveIfReady() { if (!isInitializing) save() }

    fun save() {
        runCatching {
            val f = File(CONFIG_FILE_PATH); f.parentFile?.mkdirs()
            f.writeText(Gson().toJson(mapOf(
                "glLevel" to glLevel,
                "esVersion" to esVersion,
                "useMcColor" to useMcColor,
                "normalize" to normalize,
                "noError" to noError,
                "vsyncOff" to vsyncOff,
                "batch" to batch,
                "deferredFlush" to deferredFlush,
                "useVAO" to useVAO,
                "fboMode" to fboMode
            )))
        }
    }

    companion object {
        val CONFIG_FILE_PATH: String
            get() = "${Environment.getExternalStorageDirectory().absolutePath}/KryptonTestConfig/config.json"

        fun load(): KryptonWrapperTestConfig? {
            val f = File(CONFIG_FILE_PATH)
            if (!f.exists()) return null
            val text = runCatching { f.readText() }.getOrNull() ?: return null
            return runCatching {
                val obj: JsonObject = JsonParser.parseString(text).asJsonObject
                val cfg = KryptonWrapperTestConfig(isInitializing = true)
                fun JsonObject.bool(k: String, d: Boolean) = get(k)?.asBoolean ?: d
                fun JsonObject.int(k: String, d: Int) = get(k)?.asInt ?: d
                cfg.glLevel = obj.int("glLevel", 31)
                cfg.esVersion = obj.int("esVersion", 3)
                cfg.useMcColor = obj.bool("useMcColor", true)
                cfg.normalize = obj.bool("normalize", true)
                cfg.noError = obj.bool("noError", true)
                cfg.vsyncOff = obj.bool("vsyncOff", true)
                cfg.batch = obj.bool("batch", true)
                cfg.deferredFlush = obj.int("deferredFlush", 8)
                cfg.useVAO = obj.bool("useVAO", true)
                cfg.fboMode = obj.int("fboMode", 2)
                cfg.isInitializing = false
                cfg
            }.getOrNull()
        }
    }
}

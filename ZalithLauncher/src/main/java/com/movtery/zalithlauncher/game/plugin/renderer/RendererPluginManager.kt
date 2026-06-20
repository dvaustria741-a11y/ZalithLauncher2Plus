/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.plugin.renderer

import android.content.Context
import android.content.pm.ApplicationInfo
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.plugin.ApkPlugin
import com.movtery.zalithlauncher.game.plugin.ApkPluginManager
import com.movtery.zalithlauncher.game.plugin.cacheAppIcon
import com.movtery.zalithlauncher.game.renderer.Renderers

/**
 * FCL、ZalithLauncher 渲染器插件，同时支持使用本地渲染器插件
 * [FCL Renderer Plugin](https://github.com/FCL-Team/FCLRendererPlugin)
 */
object RendererPluginManager: ApkPluginManager() {
    private val rendererPluginList: MutableList<RendererPlugin> = mutableListOf()
    private val apkRendererPluginList: MutableList<ApkRendererPlugin> = mutableListOf()

    /**
     * 获取当前渲染器插件加载的所有渲染器
     */
    fun getRendererList(): List<RendererPlugin> = rendererPluginList

    /**
     * 移除某些已加载的渲染器
     */
    fun removeRenderer(rendererPlugins: Collection<RendererPlugin>) {
        rendererPluginList.removeAll(rendererPlugins)
    }

    /**
     * @return 是可用的
     */
    fun isAvailable(): Boolean {
        return rendererPluginList.isNotEmpty()
    }

    /**
     * 当前选择的渲染器插件所加载的渲染器
     * 根据总渲染器管理者选择的渲染器的渲染器唯一标识符进行判断
     */
    val selectedRendererPlugin: RendererPlugin?
        get() {
            val currentRenderer = runCatching {
                Renderers.getCurrentRenderer().getUniqueIdentifier()
            }.getOrNull()
            return rendererPluginList.find { it.uniqueIdentifier == currentRenderer }
        }

    /**
     * 清除渲染器插件
     */
    fun clearPlugin() {
        rendererPluginList.clear()
        apkRendererPluginList.clear()
    }

    /**
     * 当前渲染器插件是否带有配置项（软件式插件、白名单包名）
     */
    @JvmStatic
    fun isConfigurablePlugin(rendererUniqueIdentifier: String): Boolean {
        val renderer = apkRendererPluginList.find { it.uniqueIdentifier == rendererUniqueIdentifier }
        return renderer?.packageName in setOf(
            "com.bzlzhh.plugin.ngg",
            "com.bzlzhh.plugin.ngg.angleless",
            "com.fcl.plugin.mobileglues"
        )
    }

    /**
     * Plugin packages with a confirmed native-crash track record on this fork, surfaced as a
     * warning in the renderer summary instead of letting users hit them blind.
     * com.bzlzhh.plugin.ngg / .angleless (Krypton Wrapper): SIGSEGV in libng_gl4es.so at renderer
     * init on some Adreno devices, and separately a SIGSEGV in libc __memcpy ~1 min into gameplay
     * under OptiFine/Iris + heavy mod load.
     */
    private val knownUnstablePlugins = setOf(
        "com.bzlzhh.plugin.ngg",
        "com.bzlzhh.plugin.ngg.angleless"
    )

    /**
     * 解析 ZalithLauncher、FCL 渲染器插件
     */
    override fun parseApkPlugin(
        context: Context,
        info: ApplicationInfo,
        loaded: (ApkPlugin) -> Unit
    ) {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            val metaData = info.metaData ?: return
            if (
                metaData.getBoolean("fclPlugin", false) ||
                metaData.getBoolean("zalithRendererPlugin", false)
            ) {
                val rendererString = metaData.getString("renderer") ?: return
                val des = metaData.getString("des") ?: return
                val pojavEnvString = metaData.getString("pojavEnv") ?: return
                val nativeLibraryDir = info.nativeLibraryDir
                val renderer = rendererString.split(":")

                var rendererId: String = renderer[0]
                val envList = mutableMapOf<String, String>()
                val dlopenList = mutableListOf<String>()
                pojavEnvString.split(":").forEach { envString ->
                    if (envString.contains("=")) {
                        val stringList = envString.split("=")
                        val key = stringList[0]
                        val value = stringList[1]
                        when (key) {
                            "POJAV_RENDERER" -> rendererId = value
                            "DLOPEN" -> {
                                value.split(",").forEach { lib ->
                                    dlopenList.add(lib)
                                }
                            }
                            "LIB_MESA_NAME", "MESA_LIBRARY" -> envList[key] = "$nativeLibraryDir/$value"
                            else -> envList[key] = value
                        }
                    }
                }

                val packageManager = context.packageManager
                val packageName = info.packageName
                val appName = info.loadLabel(packageManager).toString()

                val baseSummary = context.getString(R.string.settings_renderer_from_plugins, appName)
                val summary = if (packageName in knownUnstablePlugins) {
                    "$baseSummary — known to crash (native SIGSEGV) on some devices, especially under load. If you hit a startup or in-game crash, try a different renderer."
                } else {
                    baseSummary
                }

                val plugin = ApkRendererPlugin(
                    id = rendererId,
                    displayName = des,
                    summary = summary,
                    minMCVer = metaData.getVersionString("minMCVer"),
                    maxMCVer = metaData.getVersionString("maxMCVer"),
                    uniqueIdentifier = packageName,
                    glName = renderer[1],
                    eglName = renderer[2].progressEglName(nativeLibraryDir),
                    path = nativeLibraryDir,
                    env = envList,
                    dlopen = dlopenList,
                    packageName = packageName
                )

                rendererPluginList.add(plugin)
                apkRendererPluginList.add(plugin)

                runCatching {
                    cacheAppIcon(context, info)
                    object : ApkPlugin(
                        packageName = packageName,
                        appName = appName,
                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                    ) {}
                }.getOrNull()?.let { loaded(it) }
            }
        }
    }

    private fun String.progressEglName(libPath: String): String =
        if (startsWith("/")) "$libPath$this"
        else this
}
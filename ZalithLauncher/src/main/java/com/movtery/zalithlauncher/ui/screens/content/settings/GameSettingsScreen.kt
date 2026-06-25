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

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.movtery.zalithlauncher.game.plugin.natives.NativePlugin
import com.movtery.zalithlauncher.game.plugin.natives.NativePluginManager
import com.movtery.zalithlauncher.path.URL_CLOUD_NATIVE_LIB_PLUGINS
import com.movtery.zalithlauncher.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.unit.floatRange
import com.movtery.zalithlauncher.setting.unit.min
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.AnimatedColumn
import com.movtery.zalithlauncher.ui.components.verticalScrollWithBar
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.MemoryPreview
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.IntSliderSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.ListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.StringListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SwitchSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.TextInputSettingsCard
import com.movtery.zalithlauncher.utils.platform.getMaxMemoryForSettings
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.sendDLPlugin

@Composable
fun GameSettingsScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?,
    eventViewModel: EventViewModel
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.Game, settingsScreenKey, false)
    ) { isVisible ->
        AnimatedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollWithBar(state = rememberScrollState())
                .padding(all = 12.dp),
            isVisible = isVisible
        ) { scope ->
            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Top,
                        unit = AllSettings.versionIsolation,
                        title = stringResource(R.string.settings_game_version_isolation_title),
                        summary = stringResource(R.string.settings_game_version_isolation_summary)
                    )

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.skipGameIntegrityCheck,
                        title = stringResource(R.string.settings_game_skip_game_integrity_check_title),
                        summary = stringResource(R.string.settings_game_skip_game_integrity_check_summary)
                    )

                    TextInputSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.versionCustomInfo,
                        title = stringResource(R.string.settings_game_version_custom_info_title),
                        summary = stringResource(R.string.settings_game_version_custom_info_summary)
                    )
                }
            }

            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    val runtimes = remember { RuntimesManager.getRuntimes() }

                    var importingRuntime by remember { mutableStateOf(false) }
                    val importScope = rememberCoroutineScope()
                    val context = LocalContext.current
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir

                    val importLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent()
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        importScope.launch {
                            importingRuntime = true
                            try {
                                val name = withContext(Dispatchers.IO) {
                                    context.contentResolver.query(
                                        uri, null, null, null, null
                                    )?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                            if (idx >= 0) cursor.getString(idx) else null
                                        } else null
                                    } ?: uri.lastPathSegment ?: "custom-runtime"
                                }.removeSuffix(".tar.xz").removeSuffix(".tar").removeSuffix(".zip")

                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        RuntimesManager.installRuntime(
                                            nativeLibDir = nativeLibDir,
                                            inputStream = stream,
                                            name = name
                                        )
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_game_import_runtime_success, name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_game_import_runtime_failed, e.message),
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                importingRuntime = false
                            }
                        }
                    }

                    if (runtimes.isNotEmpty()) {
                        ListSettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Top,
                            unit = AllSettings.javaRuntime,
                            items = RuntimesManager.getRuntimes().filter { it.isCompatible() },
                            title = stringResource(R.string.settings_game_java_runtime_title),
                            summary = stringResource(R.string.settings_game_java_runtime_summary),
                            getItemText = { it.name },
                            getItemId = { it.name }
                        )
                    }

                    // Import Runtime Environment button
                    SettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        title = stringResource(R.string.settings_game_import_runtime_title),
                        summary = if (importingRuntime)
                            stringResource(R.string.settings_game_import_runtime_running)
                        else
                            stringResource(R.string.settings_game_import_runtime_summary),
                        enabled = !importingRuntime,
                        onClick = { importLauncher.launch("*/*") },
                        trailingIcon = {
                            if (importingRuntime) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_folder_zip_outlined),
                                    contentDescription = stringResource(R.string.settings_game_import_runtime_title)
                                )
                            }
                        }
                    )

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = if (runtimes.isNotEmpty()) CardPosition.Middle else CardPosition.Top,
                        unit = AllSettings.autoPickJavaRuntime,
                        title = stringResource(R.string.settings_game_auto_pick_java_runtime_title),
                        summary = stringResource(R.string.settings_game_auto_pick_java_runtime_summary)
                    )

                    val nativePlugins = remember {
                        NativePluginManager.getPlugins()
                    }

                    @Composable
                    fun DLNativeLibsButton() {
                        IconButton(
                            onClick = {
                                eventViewModel.sendDLPlugin(
                                    githubLink = URL_GITHUB_NATIVE_LIB_PLUGINS,
                                    cloudDrives = listOf(
                                        EventViewModel.Event.DownloadPlugins.CloudDrive(
                                            language = "zh",
                                            link = URL_CLOUD_NATIVE_LIB_PLUGINS
                                        )
                                    )
                                )
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_2_filled),
                                contentDescription = stringResource(R.string.generic_download)
                            )
                        }
                    }
                    if (nativePlugins.isNotEmpty()) {
                        StringListSettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Middle,
                            unit = AllSettings.disableNativeLibPlugins,
                            items = nativePlugins,
                            onItemsChange = { value, item ->
                                if (value) {
                                    this - item.packageName
                                } else {
                                    this + item.packageName
                                }
                            },
                            title = stringResource(R.string.settings_game_native_lib_plugin_title),
                            summary = stringResource(R.string.settings_game_native_lib_plugin_summary),
                            getItemID = { it.packageName },
                            getItemText = { it.displayName },
                            getItemSummary = { plugin ->
                                NativePluginSummaryLayout(plugin)
                            },
                            getItemCheck = { contains -> !contains },
                            trailingIcon = {
                                DLNativeLibsButton()
                            },
                        )
                    } else {
                        SettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Middle,
                            title = stringResource(R.string.settings_game_native_lib_plugin_title),
                            summary = stringResource(R.string.settings_game_native_lib_plugin_summary),
                            onClick = {},
                            trailingIcon = {
                                DLNativeLibsButton()
                            }
                        )
                    }

                    IntSliderSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.ramAllocation,
                        title = stringResource(R.string.settings_game_java_memory_title),
                        summary = stringResource(R.string.settings_game_java_memory_summary),
                        valueRange = AllSettings.ramAllocation.floatRange.start..getMaxMemoryForSettings(LocalContext.current).toFloat(),
                        suffix = "MB",
                        fineTuningControl = true,
                        previewContent = {
                            MemoryPreview(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp),
                                preview = (AllSettings.ramAllocation.state ?: AllSettings.ramAllocation.min).toDouble(),
                                usedText = { usedMemory, totalMemory ->
                                    stringResource(R.string.settings_game_java_memory_used_text, usedMemory.toInt(), totalMemory.toInt())
                                },
                                previewText = { preview ->
                                    stringResource(R.string.settings_game_java_memory_allocation_text, preview.toInt())
                                }
                            )
                        }
                    )

                    TextInputSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.jvmArgs,
                        title = stringResource(R.string.settings_game_jvm_args_title),
                        summary = stringResource(R.string.settings_game_jvm_args_summary)
                    )
                }
            }

            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Top,
                        unit = AllSettings.showLogAutomatic,
                        title = stringResource(R.string.settings_game_show_log_automatic_title),
                        summary = stringResource(R.string.settings_game_show_log_automatic_summary)
                    )

                    IntSliderSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.logTextSize,
                        title = stringResource(R.string.settings_game_log_text_size_title),
                        summary = stringResource(R.string.settings_game_log_text_size_summary),
                        valueRange = AllSettings.logTextSize.floatRange,
                        suffix = "Sp",
                        fineTuningControl = true
                    )

                    IntSliderSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.logBufferFlushInterval,
                        title = stringResource(R.string.settings_game_log_buffer_flush_interval_title),
                        summary = stringResource(R.string.settings_game_log_buffer_flush_interval_summary),
                        valueRange = AllSettings.logBufferFlushInterval.floatRange,
                        suffix = "ms",
                        fineTuningControl = true
                    )
                }
            }
        }
    }
}


@Composable
private fun NativePluginSummaryLayout(plugin: NativePlugin) {
    FlowRow(
        modifier = Modifier.alpha(0.7f),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_renderer_from_plugins,plugin.appName),
            style = MaterialTheme.typography.labelSmall
        )

        val minVer = plugin.minMCVer
        val maxVer = plugin.maxMCVer

        if (minVer != null || maxVer != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = stringResource(R.string.renderer_version_support), style = MaterialTheme.typography.labelSmall)

                minVer?.let {
                    Text(text = ">= $it", style = MaterialTheme.typography.labelSmall)
                }

                maxVer?.let {
                    Text(text = "<= $it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import com.movtery.zalithlauncher.utils.settings.KryptonWrapperTestConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KryptonWrapperTestSettingsDialog(onDismissRequest: () -> Unit) {
    val config = remember { KryptonWrapperTestConfig.load() ?: KryptonWrapperTestConfig() }
    var glLevel by remember { mutableIntStateOf(config.glLevel) }
    var esVersion by remember { mutableIntStateOf(config.esVersion) }
    var useMcColor by remember { mutableStateOf(config.useMcColor) }
    var normalize by remember { mutableStateOf(config.normalize) }
    var noError by remember { mutableStateOf(config.noError) }
    var vsyncOff by remember { mutableStateOf(config.vsyncOff) }
    var batch by remember { mutableStateOf(config.batch) }
    var deferredFlush by remember { mutableFloatStateOf(config.deferredFlush.toFloat()) }
    var useVAO by remember { mutableStateOf(config.useVAO) }
    var fboMode by remember { mutableIntStateOf(config.fboMode) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, color = cardColor(false),
            contentColor = onCardColor(), shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Krypton Wrapper (Test) Settings", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Experimental build — shares the same engine as Krypton Wrapper but with extra performance tuning. If something breaks, switch back to the normal Krypton Wrapper.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )

                HorizontalDivider()
                KwtSectionHeader("OpenGL ES Version")

                Text("ES Version (LIBGL_ES)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("ES 2" to 2, "ES 3" to 3).forEachIndexed { i, (label, v) ->
                        SegmentedButton(selected = esVersion == v, onClick = { esVersion = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            label = { Text(label) })
                    }
                }

                Text("GL Level (LIBGL_GL)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("ES 2.1" to 21, "ES 3.1" to 31).forEachIndexed { i, (label, v) ->
                        SegmentedButton(selected = glLevel == v, onClick = { glLevel = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            label = { Text(label) })
                    }
                }

                HorizontalDivider()
                KwtSectionHeader("Behavior")
                KwtSwitchRow("MC Color Mode", "LIBGL_USE_MC_COLOR", useMcColor) { useMcColor = it }
                KwtSwitchRow("Normalize Colors", "LIBGL_NORMALIZE", normalize) { normalize = it }
                KwtSwitchRow("No Error Mode", "LIBGL_NOERROR", noError) { noError = it }

                HorizontalDivider()
                KwtSectionHeader("Performance Tuning (Experimental)")

                KwtSwitchRow(
                    "Disable VSync",
                    "Removes the FPS cap tied to the screen's refresh rate (LIBGL_VSYNC=0)",
                    vsyncOff
                ) { vsyncOff = it }

                KwtSwitchRow(
                    "Batch Draw Calls",
                    "Batches immediate-mode emulation into fewer GPU submits, cuts CPU overhead (LIBGL_BATCH)",
                    batch
                ) { batch = it }

                KwtSwitchRow(
                    "Use VAO",
                    "Use Vertex Array Objects instead of client-side arrays (LIBGL_USEVAO)",
                    useVAO
                ) { useVAO = it }

                Text("Deferred Flush Count (LIBGL_DEFERRED_FLUSH): ${deferredFlush.toInt()}",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = deferredFlush,
                    onValueChange = { deferredFlush = it },
                    valueRange = 0f..32f,
                    steps = 31
                )
                Text(
                    "Higher values buffer more draws before forcing a flush — fewer GPU stalls, but more latency. 8 is a balanced default.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text("FBO Mode (LIBGL_FBO)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Auto" to 0, "Prefer" to 1, "Always" to 2).forEachIndexed { i, (label, v) ->
                        SegmentedButton(selected = fboMode == v, onClick = { fboMode = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            label = { Text(label) })
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    config.glLevel = glLevel
                    config.esVersion = esVersion
                    config.useMcColor = useMcColor
                    config.normalize = normalize
                    config.noError = noError
                    config.vsyncOff = vsyncOff
                    config.batch = batch
                    config.deferredFlush = deferredFlush.toInt()
                    config.useVAO = useVAO
                    config.fboMode = fboMode
                    config.save()
                    onDismissRequest()
                }) { Text("Confirm") }
            }
        }
    }
}

@Composable
private fun KwtSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun KwtSwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

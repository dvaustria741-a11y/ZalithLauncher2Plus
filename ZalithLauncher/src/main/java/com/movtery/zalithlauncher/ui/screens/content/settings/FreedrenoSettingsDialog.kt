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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.movtery.zalithlauncher.utils.settings.FreedrenoConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreedrenoSettingsDialog(onDismissRequest: () -> Unit) {
    val config = remember { FreedrenoConfig.load() ?: FreedrenoConfig() }
    var glVersion   by remember { mutableStateOf(config.glVersionOverride) }
    var noError     by remember { mutableStateOf(config.noError) }
    var mipmapLevel by remember { mutableIntStateOf(config.mipmapLevel) }
    var glThread    by remember { mutableStateOf(config.glThread) }
    val glVersionOptions = listOf("4.1", "4.2", "4.3", "4.4", "4.5", "4.6")

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, color = cardColor(false),
            contentColor = onCardColor(), shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Freedreno Settings", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()

                RSecHeader("OpenGL")
                RSecStrDropdown("GL Version Override", glVersionOptions, glVersion) { glVersion = it }
                Text("GLSL version auto-derived (${glVersion.replace(".", "")}0)",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                HorizontalDivider()
                RSecHeader("Performance")
                RSecSwitch("No Error Mode", "Skip GL error checking (MESA_NO_ERROR)", noError) { noError = it }
                RSecSwitch("GL Threading", "Offload GL calls to background thread (MESA_GLTHREAD)", glThread) { glThread = it }

                HorizontalDivider()
                RSecHeader("Textures")
                MipmapSegmented(mipmapLevel) { mipmapLevel = it }

                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    config.glVersionOverride = glVersion
                    config.noError           = noError
                    config.mipmapLevel       = mipmapLevel
                    config.glThread          = glThread
                    config.save()
                    onDismissRequest()
                }) { Text("Confirm") }
            }
        }
    }
}

@Composable
private fun RSecHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RSecSwitch(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RSecStrDropdown(label: String, options: List<String>, selected: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
            value = selected, onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onValueChange(opt); expanded = false }) }
        }
    }
}

@Composable
private fun MipmapSegmented(value: Int, onValueChange: (Int) -> Unit) {
    Text("Mipmap Level (LIBGL_MIPMAP)", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf("Off" to 0, "Nearest" to 1, "Linear" to 2, "Trilinear" to 3).forEachIndexed { i, (label, v) ->
            SegmentedButton(selected = value == v, onClick = { onValueChange(v) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                label = { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

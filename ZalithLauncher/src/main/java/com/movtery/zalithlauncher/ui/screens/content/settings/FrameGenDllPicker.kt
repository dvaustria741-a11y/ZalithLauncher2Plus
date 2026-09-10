/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.setting.AllSettings

/**
 * Lets the user point Frame Generation at their own Lossless.dll (from a Steam install they
 * already legitimately purchased). This project never bundles, redistributes, or downloads
 * that file on the user's behalf — we only ever store the content URI the user picks here.
 *
 * NOTE: this only stores the path. [FrameGenBridge][com.movtery.zalithlauncher.framegen.FrameGenBridge]
 * doesn't do anything with it yet — see that module's TODOs.
 */
@Composable
fun FrameGenDllPicker(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUri = AllSettings.frameGenerationDllUri.state

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            // Persist read access across app restarts — without this the URI stops
            // resolving the next time the process is killed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            AllSettings.frameGenerationDllUri.updateState(it.toString())
            AllSettings.frameGenerationDllUri.save()
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                Text(if (currentUri.isBlank()) "Select Lossless.dll" else "Change Lossless.dll")
            }
        }
        if (currentUri.isNotBlank()) {
            Text(
                text = displayName(context, currentUri),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun displayName(context: android.content.Context, uriString: String): String =
    runCatching {
        val uri = Uri.parse(uriString)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else uriString
        } ?: uriString
    }.getOrDefault(uriString)

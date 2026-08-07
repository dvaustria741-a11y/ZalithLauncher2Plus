/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.utils.device.GpuInfo
import com.movtery.zalithlauncher.utils.device.GpuTier

/**
 * Frame Generation toggle, gated with a tier-aware warning rather than a silent on/off.
 *
 * Deliberately does NOT hide the toggle on low-tier GPUs (e.g. Adreno 619) — it's still the
 * user's device and their call — but it stops the feature from reading as a free 2x with no
 * caveats, which is what the toggle-only UI in the reference screenshots implied.
 */
@Composable
fun FrameGenerationSetting(
    gpuInfo: GpuInfo?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Frame Generation", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Experimental. Interpolates frames to raise perceived FPS.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        val tier = gpuInfo?.tier
        if (enabled && (tier == GpuTier.LOW || tier == GpuTier.UNSUPPORTED || tier == null)) {
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = warningText(gpuInfo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun warningText(gpuInfo: GpuInfo?): String = when (gpuInfo?.tier) {
    GpuTier.UNSUPPORTED, null ->
        "Couldn't identify your GPU${gpuInfo?.renderer?.let { " ($it)" } ?: ""}. " +
        "Frame generation may not run correctly on unrecognized hardware."
    GpuTier.LOW ->
        "Your GPU (${gpuInfo.renderer}) is below the recommended tier for frame generation. " +
        "The interpolation pass itself costs GPU time, so on this hardware it may lower your " +
        "real FPS instead of raising it. Enable at your own risk."
    else -> ""
}

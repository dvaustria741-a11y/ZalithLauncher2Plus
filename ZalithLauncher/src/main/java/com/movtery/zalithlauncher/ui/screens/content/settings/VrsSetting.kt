/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Variable Rate Shading toggle. Unlike Frame Generation, this doesn't need a tier-warning
 * banner: the native interceptor (vrs_shim.c) already detects a vkCreateDevice failure with
 * the feature requested and transparently retries with the original, unmodified call — so
 * enabling this on unsupported hardware is a safe no-op rather than a crash risk. Still
 * labeled experimental since the interception approach itself hasn't been confirmed on a
 * real device yet (see vrs_shim.c's STATUS comment).
 *
 * Fixed at a 2x2 shading rate for now — see AllSettings.vrsEnabled.
 */
@Composable
fun VrsSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Variable Rate Shading", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Experimental. Shades at a reduced rate (2x2) to trade some detail for FPS. " +
                        "Whole-frame, not distance-based yet. Safely does nothing on unsupported GPUs.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.download.assets.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformSortField

/**
 * Quick-access chips that let the user instantly switch between the most
 * commonly needed sort modes without scrolling down to the Sort By filter.
 * Tapping the active chip toggles it off (resets to RELEVANCE).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSortChips(
    currentSort: PlatformSortField,
    onSortChange: (PlatformSortField) -> Unit
) {
    val chips = listOf(
        PlatformSortField.NEWEST     to stringResource(R.string.download_assets_quick_filter_new),
        PlatformSortField.UPDATED    to stringResource(R.string.download_assets_quick_filter_updated),
        PlatformSortField.POPULARITY to stringResource(R.string.download_assets_quick_filter_popular),
    )

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (sortField, label) ->
            val selected = currentSort == sortField
            ElevatedFilterChip(
                selected = selected,
                onClick = {
                    onSortChange(if (selected) PlatformSortField.RELEVANCE else sortField)
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (selected) {
                    { Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null) }
                } else null
            )
        }
    }
}

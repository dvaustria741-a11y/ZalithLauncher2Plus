package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.CardTitleLayout
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.upgrade.GithubReleaseApi
import com.movtery.zalithlauncher.utils.driver.TurnipDownloader
import com.movtery.zalithlauncher.utils.driver.TurnipRelease
import java.io.File

private data class TurnipEntry(val release: TurnipRelease, val asset: GithubReleaseApi.Asset)

private fun scanInstalledDrivers(): List<File> =
    PathManager.DIR_DRIVERS
        .listFiles { f -> f.isDirectory && f.listFiles { sf -> sf.extension == "so" }?.isNotEmpty() == true }
        ?.toList() ?: emptyList()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TurnipDriversScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?,
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.TurnipDrivers, settingsScreenKey, false)
    ) {
        val context = LocalContext.current

        var entries by remember { mutableStateOf<List<TurnipEntry>?>(null) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var installedDrivers by remember { mutableStateOf(emptyList<File>()) }
        var driverToDelete by remember { mutableStateOf<File?>(null) }

        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val displayName = run {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
                } ?: uri.lastPathSegment ?: "imported-driver.zip"
            }.let { if (!it.endsWith(".zip", ignoreCase = true)) "$it.zip" else it }
            TurnipDownloader.importFromFile(context, uri, displayName)
        }

        LaunchedEffect(Unit) {
            installedDrivers = scanInstalledDrivers()
            TurnipDownloader.driverChanges.collect {
                installedDrivers = scanInstalledDrivers()
            }
        }

        LaunchedEffect(Unit) {
            try {
                val releases = TurnipDownloader.fetchAllReleases()
                entries = releases.flatMap { release ->
                    release.assets.map { asset -> TurnipEntry(release, asset) }
                }
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
            } finally {
                loading = false
            }
        }

        if (error != null) {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_error),
                text = error!!,
                onDismiss = { error = null }
            )
        }

        driverToDelete?.let { driver ->
            SimpleAlertDialog(
                title = stringResource(R.string.generic_delete),
                text = stringResource(R.string.turnip_driver_delete_confirm, driver.name),
                confirmText = stringResource(R.string.generic_delete),
                onConfirm = {
                    val deleted = driver.deleteRecursively()
                    if (deleted) {
                        DriverPluginManager.scanExternalDrivers(context)
                        installedDrivers = scanInstalledDrivers()
                    } else {
                        error = context.getString(R.string.generic_error)
                    }
                    driverToDelete = null
                },
                onDismiss = { driverToDelete = null }
            )
        }

        BackgroundCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CardTitleLayout {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        text = stringResource(R.string.settings_renderer_download_turnip),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { importLauncher.launch("application/zip") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.turnip_driver_import),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (installedDrivers.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.turnip_driver_installed),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .alpha(0.7f)
                                )
                            }
                            items(installedDrivers, key = { it.absolutePath }) { driver ->
                                InstalledDriverEntry(
                                    name = driver.name,
                                    onDeleteClick = { driverToDelete = driver }
                                )
                            }
                            item {
                                Text(
                                    text = stringResource(R.string.turnip_driver_available),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .alpha(0.7f)
                                )
                            }
                        }

                        if (entries.isNullOrEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.stats_no_data),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            items(entries!!, key = { "${it.release.tagName}_${it.asset.name}" }) { entry ->
                                DriverEntry(entry = entry, onClick = {
                                    TurnipDownloader.downloadAsset(context, entry.asset)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledDriverEntry(name: String, onDeleteClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onDeleteClick) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.ic_delete_filled),
                    contentDescription = stringResource(R.string.generic_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DriverEntry(entry: TurnipEntry, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.asset.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                Text(
                    text = entry.release.tagName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.alpha(0.7f)
                )
            }
            Text(
                text = "%.1f MB".format(entry.asset.size / (1024.0 * 1024.0)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .alpha(0.6f)
            )
        }
    }
}

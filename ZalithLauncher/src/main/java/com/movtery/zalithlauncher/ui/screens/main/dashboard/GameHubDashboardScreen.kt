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

package com.movtery.zalithlauncher.ui.screens.main.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R

/** One real, wired-up sidebar action (icon + label + click handler) */
data class DashboardSidebarAction(
    val iconRes: Int,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

private val defaultPreviewActions = listOf(
    DashboardSidebarAction(R.drawable.ic_nav_home, "Home", selected = true) {},
    DashboardSidebarAction(R.drawable.ic_nav_versions, "Versions") {},
    DashboardSidebarAction(R.drawable.ic_nav_mods, "Mods") {},
    DashboardSidebarAction(R.drawable.ic_nav_settings, "Settings") {}
)

/**
 * Standalone "GameHub" style dashboard, matching the landscape mockup:
 * icon-only left rail, full-width hero profile banner, a 3-card stat row,
 * and a floating pill Launch button bottom-right.
 *
 * [sidebarActions] takes real click handlers from the caller (e.g. LauncherScreen's
 * onVersionsClick/onFpsClick/onInfoClick) rather than hardcoding icons that don't
 * map to anything real. Defaults to a 4-icon preview set for standalone use.
 */
@Composable
fun GameHubDashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    sidebarActions: List<DashboardSidebarAction> = defaultPreviewActions,
    avatarContent: (@Composable () -> Unit)? = null,
    onAvatarClick: () -> Unit = {},
    onLaunch: () -> Unit = {},
    onVersionRowClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GameHubColors.Background)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            DashboardSidebar(actions = sidebarActions)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(20.dp)
            ) {
                ProfileBanner(
                    state = state,
                    avatarContent = avatarContent,
                    onAvatarClick = onAvatarClick,
                    onVersionRowClick = onVersionRowClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PlayTimeCard(
                            state = state,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        PlayTimeByVersionCard(
                            versions = state.playTimeByVersion,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        RecentSessionsCard(
                            sessions = state.recentSessions,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }

        Button(
            onClick = onLaunch,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = GameHubColors.Accent),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Launch", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DashboardSidebar(
    actions: List<DashboardSidebarAction>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(GameHubColors.Background)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        actions.forEach { action ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (action.selected) Modifier.border(1.dp, GameHubColors.Accent, RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .clickable(onClick = action.onClick)
            ) {
                Icon(
                    painter = painterResource(action.iconRes),
                    contentDescription = action.label,
                    tint = if (action.selected) GameHubColors.Accent else GameHubColors.OnSurfaceMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProfileBanner(
    state: DashboardUiState,
    avatarContent: (@Composable () -> Unit)?,
    onAvatarClick: () -> Unit,
    onVersionRowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GameHubColors.Surface)
    ) {
        // Hero art bleeding off the right edge
        Image(
            painter = painterResource(R.drawable.hero_zenkaifrag),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(420.dp)
        )
        // Scrim so avatar/text stay legible over the art
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(GameHubColors.HeroScrim)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(2.dp, GameHubColors.Accent, CircleShape)
                        .background(GameHubColors.SurfaceElevated)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarContent != null) {
                        avatarContent()
                    } else {
                        Text("🐧", fontSize = 28.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.playerName,
                            color = GameHubColors.Accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Pill(text = "★ ${state.experienceLabel}")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (if (state.isOnline) "Online" else "Offline") +
                            "   •   Play Time: ${state.totalPlayTimeHours} Hours",
                        color = GameHubColors.OnSurfaceMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(GameHubColors.SurfaceElevated)
                    .clickable(onClick = onVersionRowClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = state.activeVersion.versionName,
                    color = GameHubColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = state.activeVersion.loaderLabel,
                    color = GameHubColors.OnSurfaceMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Version settings",
                    tint = GameHubColors.OnSurfaceMuted,
                    modifier = Modifier
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text = text,
        color = GameHubColors.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, GameHubColors.AccentDim, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(GameHubColors.Surface)
            .border(1.dp, GameHubColors.Outline, RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = GameHubColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            action?.invoke()
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun PlayTimeCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    DashboardCard(
        title = "Play Time",
        modifier = modifier,
        action = { TabSwitcher() }
    ) {
        Text("Today", color = GameHubColors.OnSurfaceMuted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${state.todayPlayTimeHours}",
                color = GameHubColors.OnSurface,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Hours",
                color = GameHubColors.OnSurfaceMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PlayTimeContributionGrid(days = state.weeklyPlayTime)
    }
}

@Composable
private fun TabSwitcher() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GameHubColors.SurfaceElevated)
            .padding(3.dp)
    ) {
        Text(
            "Today",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, GameHubColors.Accent, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Text(
            "Weekly",
            color = GameHubColors.OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PlayTimeByVersionCard(versions: List<VersionPlayTime>, modifier: Modifier = Modifier) {
    val maxHours = (versions.maxOfOrNull { it.hours } ?: 1f).coerceAtLeast(1f)
    DashboardCard(
        title = "Play Time by Version",
        modifier = modifier,
        action = { Text("View All", color = GameHubColors.Accent, fontSize = 13.sp) }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(versions) { version ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(version.versionName, color = GameHubColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${version.hours} Hours", color = GameHubColors.OnSurfaceMuted, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { version.hours / maxHours },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = GameHubColors.Accent,
                        trackColor = GameHubColors.SurfaceElevated
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<GameSession>, modifier: Modifier = Modifier) {
    DashboardCard(
        title = "Recent Sessions",
        modifier = modifier,
        action = { Text("View All", color = GameHubColors.Accent, fontSize = 13.sp) }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(sessions) { session ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(session.versionName, color = GameHubColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(session.dateLabel, color = GameHubColors.OnSurfaceMuted, fontSize = 12.sp)
                    }
                    Text("${session.hours}h", color = GameHubColors.OnSurfaceMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

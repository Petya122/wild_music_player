package com.wildmusicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.DownloadNetworkMode
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.ui.components.PermissionsDialog
import com.wildmusicplayer.ui.components.TagRepairDialog

// Settings sections: downloads & network, permissions, and the tag repair tool

@Composable
internal fun DownloadsSection(isOfflineOnlyMode: Boolean, onOfflineOnlyModeToggled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val networkPolicyManager = remember { NetworkPolicyManager.getInstance(context) }
    var downloadNetworkMode by remember { mutableStateOf(networkPolicyManager.downloadNetworkMode) }
    var blockMeteredHotspots by remember { mutableStateOf(networkPolicyManager.blockMeteredHotspots) }
    var backgroundDownloads by remember { mutableStateOf(networkPolicyManager.backgroundDownloads) }
    var showNetworkModeDialog by remember { mutableStateOf(false) }

    SettingsCard(
        icon = Icons.Default.CloudDownload,
        title = "Downloads & Network",
        subtitle = "Configure network limits, background downloads, and metered hotspots"
    ) {
        // Scaffolded Row: Offline Only Mode Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOfflineOnlyModeToggled(!isOfflineOnlyMode) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isOfflineOnlyMode) Icons.Default.CloudOff else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isOfflineOnlyMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Offline Only Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isOfflineOnlyMode) "Zero network usage: Online search and streaming are paused" else "Disable online streaming and search for 100% offline, battery-saving listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isOfflineOnlyMode,
                onCheckedChange = onOfflineOnlyModeToggled
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Scaffolded Row 1: Network Mode Picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showNetworkModeDialog = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Download Network",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = downloadNetworkMode.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Row 2: Block on Metered Hotspots Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = !blockMeteredHotspots
                    blockMeteredHotspots = next
                    networkPolicyManager.blockMeteredHotspots = next
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Block on Metered Hotspots",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Prevents downloads when tethered to mobile hotspots or metered Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = blockMeteredHotspots,
                onCheckedChange = {
                    blockMeteredHotspots = it
                    networkPolicyManager.blockMeteredHotspots = it
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Row 3: Background Downloads Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = !backgroundDownloads
                    backgroundDownloads = next
                    networkPolicyManager.backgroundDownloads = next
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Background Downloading",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Keep downloading if app is closed or tabbed out, then shuts off automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = backgroundDownloads,
                onCheckedChange = {
                    backgroundDownloads = it
                    networkPolicyManager.backgroundDownloads = it
                }
            )
        }
    }

    if (showNetworkModeDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkModeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Download Network",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadNetworkMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    downloadNetworkMode = mode
                                    networkPolicyManager.downloadNetworkMode = mode
                                    showNetworkModeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = downloadNetworkMode == mode,
                                onClick = {
                                    downloadNetworkMode = mode
                                    networkPolicyManager.downloadNetworkMode = mode
                                    showNetworkModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNetworkModeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/** Why each permission is needed, with links to manage them. */
@Composable
internal fun PermissionsCard() {
    var showPermissionsDialog by remember { mutableStateOf(false) }
    SettingsLinkCard(
        icon = Icons.Default.Security,
        title = "Permissions & Privacy",
        subtitle = "Learn why each permission is needed and manage access",
        onClick = { showPermissionsDialog = true }
    )
    if (showPermissionsDialog) {
        PermissionsDialog(onDismiss = { showPermissionsDialog = false })
    }
}

/** Fix tags on songs downloaded before v0.6.0. */
@Composable
internal fun TagRepairCard(
    downloadManager: YouTubeDownloadManager,
    isOfflineOnlyMode: Boolean,
    onLibraryChanged: () -> Unit
) {
    var showTagRepair by remember { mutableStateOf(false) }
    SettingsLinkCard(
        icon = Icons.Default.AutoFixHigh,
        title = "Fix Downloaded Song Tags",
        subtitle = "Add the artist and cover art to songs downloaded before v0.6.0",
        onClick = { showTagRepair = true }
    )
    if (showTagRepair) {
        TagRepairDialog(
            downloadManager = downloadManager,
            isOfflineOnlyMode = isOfflineOnlyMode,
            onDismiss = { changed ->
                showTagRepair = false
                if (changed) onLibraryChanged()
            }
        )
    }
}

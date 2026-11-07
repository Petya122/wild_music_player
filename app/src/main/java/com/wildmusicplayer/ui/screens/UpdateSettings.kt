package com.wildmusicplayer.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.BuildConfig
import com.wildmusicplayer.data.UpdateChecker
import com.wildmusicplayer.data.UpdateInfo
import com.wildmusicplayer.data.UpdateResult
import kotlinx.coroutines.launch
import java.io.File

/**
 * The "Updates & Version" and "Storage & Cache" sections. They share the update checker: a downloaded
 * update is part of the cache, so downloading or deleting one updates the cache size.
 */
@Composable
internal fun UpdateAndStorageSections(onShowWhatsNew: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker.getInstance(context) }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var autoPromptInstall by remember { mutableStateOf(updateChecker.autoPromptInstall) }
    var pendingInstallDialogFile by remember { mutableStateOf<File?>(null) }
    var pendingInstallVersion by remember { mutableStateOf<String?>(null) }
    var onHoldRefreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        cacheSizeBytes = updateChecker.getCacheSizeBytes()
    }

    SettingsCard(icon = Icons.Default.SystemUpdate, title = if (BuildConfig.SELF_UPDATE) "Updates & Version" else "Version") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Installed Version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // The update log tells the GitHub version's story (YouTube features included)
            if (BuildConfig.YOUTUBE) TextButton(
                onClick = onShowWhatsNew,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Update Log",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Checking, downloading and installing new versions: only the GitHub version updates itself
        if (BuildConfig.SELF_UPDATE) {
            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Toggle: Auto-Prompt Install vs Keep on Hold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !autoPromptInstall
                        autoPromptInstall = next
                        updateChecker.autoPromptInstall = next
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Auto-Prompt Install After Download",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ask to install immediately when download finishes, or keep ready on hold",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoPromptInstall,
                    onCheckedChange = {
                        autoPromptInstall = it
                        updateChecker.autoPromptInstall = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (availableUpdate != null) {
                val update = availableUpdate!!
                val downloadedFile = remember(availableUpdate, isDownloadingUpdate, onHoldRefreshTrigger) {
                    updateChecker.getDownloadedUpdateFile(update.latestVersion)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "New Release Available: ${update.latestVersion}! 🎉",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isDownloadingUpdate) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading update in background...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${(downloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        } else if (downloadedFile != null) {
                            // APK is already on disk and kept on hold!
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "✅ Update ${update.latestVersion} is downloaded and ready on hold.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            updateChecker.triggerApkInstall(downloadedFile, context)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Install Update Now")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            updateChecker.deleteDownloadedUpdate(update.latestVersion)
                                            onHoldRefreshTrigger++
                                            scope.launch {
                                                cacheSizeBytes = updateChecker.getCacheSizeBytes()
                                            }
                                            Toast.makeText(context, "Downloaded update removed", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Download",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    isDownloadingUpdate = true
                                    downloadProgress = 0f
                                    updateStatusText = null
                                    scope.launch {
                                        val apkFile = updateChecker.downloadUpdateSilently(
                                            apkUrl = update.downloadUrl,
                                            fileName = "wild_music_player-${update.latestVersion}.apk",
                                            onProgress = { progress ->
                                                downloadProgress = progress
                                            }
                                        )
                                        isDownloadingUpdate = false
                                        onHoldRefreshTrigger++
                                        if (apkFile != null) {
                                            cacheSizeBytes = updateChecker.getCacheSizeBytes()
                                            if (autoPromptInstall) {
                                                pendingInstallDialogFile = apkFile
                                                pendingInstallVersion = update.latestVersion
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Update downloaded! Kept on hold ready to install.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } else {
                                            updateStatusText = "Download failed. Please check internet connection."
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Update")
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        isCheckingUpdates = true
                        updateStatusText = null
                        scope.launch {
                            when (val result = updateChecker.checkForUpdates(BuildConfig.VERSION_NAME)) {
                                is UpdateResult.UpdateAvailable -> {
                                    availableUpdate = result.info
                                    updateStatusText = null
                                }
                                is UpdateResult.UpToDate -> {
                                    updateStatusText = "You are on the latest version!"
                                }
                                is UpdateResult.Error -> {
                                    updateStatusText = "Update check failed: ${result.message}"
                                }
                            }
                            isCheckingUpdates = false
                        }
                    },
                    enabled = !isCheckingUpdates,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking GitHub...")
                    } else {
                        Text("Check for Updates")
                    }
                }
            }

            if (updateStatusText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = updateStatusText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    SettingsCard(
        icon = Icons.Default.Storage,
        title = "Storage & Cache",
        subtitle = if (BuildConfig.SELF_UPDATE) "Free up temporary files, stream cache, and old APKs" else "Free up temporary files"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current Cache Size:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = UpdateChecker.formatFileSize(cacheSizeBytes ?: 0L),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                isClearingCache = true
                scope.launch {
                    updateChecker.clearAppCache()
                    cacheSizeBytes = updateChecker.getCacheSizeBytes()
                    isClearingCache = false
                    Toast.makeText(context, "Cache cleared successfully!", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !isClearingCache,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isClearingCache) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clearing...")
            } else {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear App Cache")
            }
        }
    }

    if (BuildConfig.SELF_UPDATE && pendingInstallDialogFile != null) {
        val file = pendingInstallDialogFile!!
        AlertDialog(
            onDismissRequest = { pendingInstallDialogFile = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Update Ready to Install 🎉",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Wild Music Player ${pendingInstallVersion ?: ""} has finished downloading.\n\nWould you like to install it now, or keep it on hold for later?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingInstallDialogFile = null
                        updateChecker.triggerApkInstall(file, context)
                    }
                ) {
                    Text("Install Now")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingInstallDialogFile = null
                        Toast.makeText(context, "Update kept on hold. You can install it anytime from Settings.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Keep on Hold")
                }
            }
        )
    }
}

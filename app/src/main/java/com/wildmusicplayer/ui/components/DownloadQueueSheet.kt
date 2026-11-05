package com.wildmusicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.DownloadPause
import com.wildmusicplayer.data.DownloadQueue
import com.wildmusicplayer.data.DownloadState
import com.wildmusicplayer.data.QueuedDownload

/**
 * The download queue: the song downloading now (with its progress), the ones waiting and the ones that
 * failed. Each can be cancelled; failed ones retried. While Offline Mode is on, everything waits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueSheet(
    onRetry: (String) -> Unit,
    onGoOnline: () -> Unit,
    onDismiss: () -> Unit
) {
    val items by DownloadQueue.items.collectAsState()
    val pausedFor by DownloadQueue.pausedFor.collectAsState()
    val offline = DownloadPause.OFFLINE in pausedFor

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val waiting = items.count { it.state != DownloadState.FAILED }
            Text(
                text = when {
                    items.isEmpty() -> "Nothing downloading"
                    offline -> "Paused: Offline Mode is on"
                    waiting == 0 -> "Nothing downloading"
                    waiting == 1 -> "1 song"
                    else -> "$waiting songs, one at a time"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (offline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (offline && waiting > 0) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "They continue when you go online again",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(onClick = onGoOnline) { Text("Go online") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(
                    text = "Songs you download from the Downloader or a playlist show up here while they download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.track.id }) { item ->
                        DownloadRow(item, paused = pausedFor.isNotEmpty(), onRetry = onRetry)
                    }
                }
                if (items.any { it.state == DownloadState.FAILED }) {
                    TextButton(onClick = { DownloadQueue.clearFailed() }, modifier = Modifier.align(Alignment.End)) {
                        Text("Clear failed")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: QueuedDownload, paused: Boolean, onRetry: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SongThumbnail(albumArtUri = item.track.thumbnailUrl, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (item.state) {
                DownloadState.DOWNLOADING -> {
                    Text(
                        text = "Downloading · ${(item.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                }
                DownloadState.WAITING -> Text(
                    text = if (paused) "Paused" else "Waiting",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DownloadState.FAILED -> Text(
                    text = "Failed" + (item.error?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.state == DownloadState.FAILED) {
            IconButton(onClick = { onRetry(item.track.id) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Try again")
            }
        }
        IconButton(onClick = { DownloadQueue.remove(item.track.id) }) {
            Icon(Icons.Default.Close, contentDescription = if (item.state == DownloadState.DOWNLOADING) "Cancel" else "Remove")
        }
    }
}

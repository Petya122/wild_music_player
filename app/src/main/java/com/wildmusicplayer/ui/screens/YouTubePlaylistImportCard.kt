package com.wildmusicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.YouTubePlaylist
import com.wildmusicplayer.data.YouTubePlaylistImporter
import com.wildmusicplayer.data.playlistImportBlocker
import kotlinx.coroutines.launch

/**
 * Shown in the Downloader when a YouTube or YouTube Music playlist link is pasted: the playlist's cover,
 * name and size, and a button that imports it as a playlist of streamed songs. A playlist imported
 * before ([existing]) gets its new songs instead of a second copy.
 */
@Composable
fun YouTubePlaylistImportCard(
    playlistId: String,
    importer: YouTubePlaylistImporter,
    existing: Playlist?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val blocker = remember(playlistId) { playlistImportBlocker(playlistId) }
    var preview by remember(playlistId) { mutableStateOf<YouTubePlaylist?>(null) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }
    var isLoadingPreview by remember(playlistId) { mutableStateOf(blocker == null) }
    var isImporting by remember(playlistId) { mutableStateOf(false) }
    var loadedSongs by remember(playlistId) { mutableIntStateOf(0) }
    var done by remember(playlistId) { mutableStateOf<String?>(null) }
    var attempt by remember(playlistId) { mutableIntStateOf(0) }

    LaunchedEffect(playlistId, attempt) {
        if (blocker != null) return@LaunchedEffect
        isLoadingPreview = true
        error = null
        importer.preview(playlistId)
            .onSuccess { preview = it }
            .onFailure { error = it.message }
        isLoadingPreview = false
    }

    val startImport = {
        isImporting = true
        loadedSongs = 0
        error = null
        scope.launch {
            importer.import(playlistId) { loadedSongs = it }
                .onSuccess { result ->
                    done = when (result) {
                        is YouTubePlaylistImporter.ImportResult.Created -> buildString {
                            append("Imported \"${result.playlist.name}\": ${result.songs} songs. Find it in Playlists.")
                            if (result.skipped > 0) append(" ${result.skipped} deleted or private videos were left out.")
                            if (result.truncated) append(" Only the first ${result.songs} songs fit in one playlist.")
                        }
                        is YouTubePlaylistImporter.ImportResult.Updated -> when (result.added) {
                            0 -> "\"${result.playlist.name}\" is already up to date."
                            1 -> "Added 1 new song to \"${result.playlist.name}\"."
                            else -> "Added ${result.added} new songs to \"${result.playlist.name}\"."
                        }
                    }
                }
                .onFailure { error = it.message ?: "Couldn't import this playlist." }
            isImporting = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val cover = preview?.thumbnailUrl
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp)
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YOUTUBE PLAYLIST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = preview?.name ?: if (isLoadingPreview) "Loading playlist…" else "Playlist link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    preview?.let { loaded ->
                        val count = if (loaded.totalCount > 0) "${loaded.totalCount} songs" else null
                        Text(
                            text = listOfNotNull(loaded.uploader.ifBlank { null }, count).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isLoadingPreview) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            when {
                blocker != null -> Text(blocker, style = MaterialTheme.typography.bodyMedium)
                isImporting -> {
                    val total = preview?.totalCount?.takeIf { it > 0 }
                    if (total != null) {
                        LinearProgressIndicator(
                            progress = { (loadedSongs.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = if (total != null) "Loading songs… $loadedSongs of $total" else "Loading songs… $loadedSongs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                done != null -> Text(done ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                error != null -> {
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { if (preview == null) attempt++ else startImport() }) { Text("Try again") }
                }
                preview != null -> {
                    Button(onClick = { startImport() }, modifier = Modifier.fillMaxWidth()) {
                        if (existing != null) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update \"${existing.name}\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import as playlist")
                        }
                    }
                    Text(
                        text = if (existing != null) {
                            "Already imported: this adds the songs added on YouTube since."
                        } else {
                            "The songs stream when you play them. The playlist keeps up with YouTube: open it in Playlists and new songs are added."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}

package com.wildmusicplayer.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.PlaylistBackup
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Back up all playlists, favorites and recently played to a file the user picks, and restore them.
 * Restoring only adds: nothing already in the app is removed.
 */
@Composable
internal fun BackupSection(librarySongs: List<Song>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PlaylistRepository.getInstance(context) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = PlaylistBackup.create(repository, librarySongs, System.currentTimeMillis())
        scope.launch {
            val saved = writeText(context, uri, text)
            val count = repository.customPlaylists.value.size
            Toast.makeText(
                context,
                if (saved) "Backed up $count ${if (count == 1) "playlist" else "playlists"}" else "Couldn't save the backup",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readText(context, uri)
            val summary = text?.let { PlaylistBackup.restore(it, repository, librarySongs) }
            restoreMessage = when {
                text == null -> "Couldn't read that file."
                summary == null -> "That file isn't a Wild Music Player backup."
                else -> buildString {
                    append("Restored ${summary.playlists} ${if (summary.playlists == 1) "playlist" else "playlists"}")
                    if (summary.songsTotal > 0) append(", ${summary.songsFound} of ${summary.songsTotal} songs found on this phone")
                    if (summary.favorites > 0) append(", and ${summary.favorites} favorites")
                    append('.')
                    if (summary.songsFound < summary.songsTotal) {
                        append("\n\nSongs that weren't found aren't on this phone (or were renamed). Restore again after adding them.")
                    }
                }
            }
        }
    }

    SettingsCard(
        icon = Icons.Default.SettingsBackupRestore,
        title = "Backup & Restore",
        subtitle = "Save your playlists and favorites to a file, for a new phone or a reinstall"
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { backupLauncher.launch("wild-music-player-backup-${today()}.json") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back Up")
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restore")
            }
        }
    }

    restoreMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreMessage = null },
            title = { Text("Restore") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { restoreMessage = null }) { Text("OK") } }
        )
    }
}

/**
 * Saves a playlist as an .m3u8 file (where the user picks) for other music players.
 * Returns the function to call with the playlist to export.
 */
@Composable
fun rememberPlaylistExporter(librarySongs: List<Song>): (Playlist) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Playlist?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val playlist = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        val byId = librarySongs.associateBy { it.id }
        val text = PlaylistBackup.toM3u8(playlist.name, playlist.songIds.mapNotNull { byId[it] }, playlist.onlineTracks)
        scope.launch {
            val saved = writeText(context, uri, text)
            Toast.makeText(context, if (saved) "Saved \"${playlist.name}\" as M3U8" else "Couldn't save the playlist", Toast.LENGTH_SHORT).show()
        }
    }

    return { playlist ->
        pending = playlist
        val fileName = playlist.name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "playlist" }
        launcher.launch("$fileName.m3u8")
    }
}

private suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
    } catch (e: Exception) {
        false
    }
}

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    } catch (e: Exception) {
        null
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

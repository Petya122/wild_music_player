package com.wildmusicplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.readableFolderPath
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.platform.SongFileInfo
import com.wildmusicplayer.platform.SongInfoSource
import com.wildmusicplayer.ui.screens.formatSongDuration
import kotlin.math.roundToInt

/** Everything known about [song]: its tags, how often it was played, and its file. */
@Composable
fun SongInfoDialog(song: Song, source: SongInfoSource, onDismiss: () -> Unit) {
    var fileInfo by remember(song.id) { mutableStateOf<SongFileInfo?>(null) }
    var loading by remember(song.id) { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        fileInfo = try {
            source.fileInfo(song)
        } catch (e: Exception) {
            null
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        icon = {
            SongThumbnail(albumArtUri = song.albumArtUri, modifier = Modifier.size(72.dp), shape = RoundedCornerShape(12.dp))
        },
        title = { Text(song.title, maxLines = 2) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                songInfoRows(song, source.playCount(song.id), fileInfo, loading).forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(92.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    )
}

/** The rows of the sheet as (label, value); unknown details are left out. */
internal fun songInfoRows(song: Song, plays: Int, file: SongFileInfo?, loading: Boolean): List<Pair<String, String>> = buildList {
    add("Artist" to song.artist)
    add("Album" to song.album)
    if (song.trackNumber > 0) {
        val disc = song.trackNumber / 1000
        val track = song.trackNumber % 1000
        add("Track" to if (disc > 0) "$track (disc $disc)" else "$track")
    }
    if (song.year > 0) add("Year" to song.year.toString())
    add("Length" to formatSongDuration(song.duration))
    if (song.isOnline) {
        add("Source" to "Online stream")
        return@buildList
    }
    add("Played" to when (plays) {
        0 -> "Not yet"
        1 -> "Once"
        else -> "$plays times"
    })
    if (loading) {
        add("File" to "Reading…")
        return@buildList
    }
    file?.format?.let { add("Format" to it) }
    file?.bitrateKbps?.let { add("Bitrate" to "$it kbps") }
    file?.sampleRateHz?.let { add("Sample rate" to formatKhz(it)) }
    file?.sizeBytes?.let { add("Size" to formatFileSize(it)) }
    file?.addedOn?.let { add("Added" to it) }
    song.filePath?.let { path ->
        add("File name" to path.substringAfterLast('/'))
        song.folderPath?.let { add("Folder" to readableFolderPath(it)) }
    }
}

/** 44100 → "44.1 kHz", 48000 → "48 kHz". */
internal fun formatKhz(hz: Int): String {
    val tenths = (hz / 100.0).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
}

/** 4_718_592 → "4.5 MB", 812_000 → "793 KB". */
internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024 * 1024) return "${(bytes / 1024.0).roundToInt()} KB"
    val tenths = (bytes / (1024.0 * 1024.0) * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10} MB"
}

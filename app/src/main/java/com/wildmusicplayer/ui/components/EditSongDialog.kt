package com.wildmusicplayer.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.SongEditor
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The new details for a song, from [EditSongDialog]. [coverJpeg] is null when the cover stays. */
class SongEdit(val song: Song, val title: String, val artist: String, val album: String, val coverJpeg: ByteArray?)

/** Title, artist, album and cover of [song]; MP3 and M4A files only ([format] null = can't be edited). */
@Composable
fun EditSongDialog(song: Song, format: SongEditor.Format?, onSave: (SongEdit) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist.takeUnless { it == "Unknown Artist" }.orEmpty()) }
    var album by remember { mutableStateOf(song.album.takeUnless { it == "Unknown Album" }.orEmpty()) }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picture ->
        if (picture != null) scope.launch {
            cover = withContext(Dispatchers.IO) { SongEditor.coverJpeg(context, picture) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit details") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (format == null) {
                    val type = song.filePath?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotBlank() }
                    Text(
                        text = if (song.isOnline) "Online streams can't be edited."
                            else "Editing works for MP3 and M4A files" + (type?.let { ". This one is $it." } ?: "."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val picked = cover
                    if (picked != null) {
                        val bitmap = remember(picked) { BitmapFactory.decodeByteArray(picked, 0, picked.size)?.asImageBitmap() }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "New cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                    } else {
                        SongThumbnail(albumArtUri = song.albumArtUri, modifier = Modifier.size(72.dp), shape = RoundedCornerShape(10.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = {
                            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(if (cover == null) "Change cover" else "Pick another") }
                        Text(
                            text = "${format.label} file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Saved into the file, so your car and other apps show it too. Android may ask you to allow the change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (format != null) {
                TextButton(
                    onClick = { onSave(SongEdit(song, title.trim(), artist.trim(), album.trim(), cover)) },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (format == null) "Close" else "Cancel") } }
    )
}

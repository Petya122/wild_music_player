package com.wildmusicplayer.ui.screens

import androidx.compose.material.icons.outlined.Edit
import com.wildmusicplayer.ui.LocalEditSong
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Info
import com.wildmusicplayer.ui.LocalSongInfo
import com.wildmusicplayer.ui.components.SongInfoDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.wildmusicplayer.ui.LocalShowMessage
import com.wildmusicplayer.ui.LocalQueueActions
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.wildmusicplayer.data.albumsOf
import com.wildmusicplayer.data.artistsOf
import com.wildmusicplayer.data.foldersOf
import com.wildmusicplayer.data.withKey
import com.wildmusicplayer.ui.PlatformBackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.ui.components.SongThumbnail

enum class SongSortOrder(val label: String) {
    TITLE("Title (A-Z)"),
    ARTIST("Artist (A-Z)"),
    DURATION("Duration"),
    DEFAULT("Recently Added")
}

@Composable
fun SongsScreen(
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    currentPlayingSongId: Long?,
    bottomPadding: Dp,
    onSongClick: (Song) -> Unit,
    onShuffleAll: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteSong: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    // Albums / Artists / Folders: play a song within its group, or the whole group
    onPlayInList: (Song, List<Song>) -> Unit = { song, _ -> onSongClick(song) },
    onPlayGroup: (List<Song>, shuffle: Boolean) -> Unit = { list, _ -> list.firstOrNull()?.let(onSongClick) }
) {
    var viewName by rememberSaveable { mutableStateOf(LibraryView.SONGS.name) }
    val view = LibraryView.entries.firstOrNull { it.name == viewName } ?: LibraryView.SONGS
    var openGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(songs, view) {
        when (view) {
            LibraryView.SONGS -> emptyList()
            LibraryView.ALBUMS -> albumsOf(songs)
            LibraryView.ARTISTS -> artistsOf(songs)
            LibraryView.FOLDERS -> foldersOf(songs)
        }
    }
    val openGroup = groups.withKey(openGroupKey)

    // Back from an album / artist / folder returns to the list, not to Home
    PlatformBackHandler(enabled = openGroup != null) { openGroupKey = null }

    if (openGroup != null) {
        SongGroupDetail(
            group = openGroup,
            favoriteSongIds = favoriteSongIds,
            currentPlayingSongId = currentPlayingSongId,
            bottomPadding = bottomPadding,
            onBack = { openGroupKey = null },
            onSongClick = { song -> onPlayInList(song, openGroup.songs) },
            onPlay = { shuffle -> onPlayGroup(openGroup.songs, shuffle) },
            onToggleFavorite = onToggleFavorite,
            onDeleteSong = onDeleteSong,
            onAddToPlaylist = onAddToPlaylist
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryViewChips(selected = view, onSelect = { viewName = it.name })
        when (view) {
            LibraryView.SONGS -> AllSongsList(
                songs, favoriteSongIds, currentPlayingSongId, bottomPadding,
                onSongClick, onShuffleAll, onToggleFavorite, onDeleteSong, onAddToPlaylist
            )
            LibraryView.ALBUMS -> AlbumGrid(groups, bottomPadding) { openGroupKey = it.key }
            LibraryView.ARTISTS, LibraryView.FOLDERS -> GroupList(groups, bottomPadding) { openGroupKey = it.key }
        }
    }
}

@Composable
private fun AllSongsList(
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    currentPlayingSongId: Long?,
    bottomPadding: Dp,
    onSongClick: (Song) -> Unit,
    onShuffleAll: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteSong: ((Song) -> Unit)?,
    onAddToPlaylist: ((Song) -> Unit)?
) {
    var sortOrder by rememberSaveable { mutableStateOf(SongSortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            SongSortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            SongSortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SongSortOrder.DURATION -> songs.sortedByDescending { it.duration }
            // MediaStore IDs grow as files are added: highest first = newest first
            SongSortOrder.DEFAULT -> songs.sortedByDescending { it.id }
        }
    }

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No songs found on device storage.\nTransfer some MP3s to get started!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            // Controls Header: Track count, Shuffle All, Sort Menu
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "All Tracks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${sortedSongs.size} songs • ${sortOrder.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onShuffleAll,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort order",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SongSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = order.label,
                                                fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                                color = if (order == sortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            sortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Song list items with key = it.id for 120 FPS recycling
            items(sortedSongs, key = { it.id }) { song ->
                val isCurrent = song.id == currentPlayingSongId
                val isFavorite = favoriteSongIds.contains(song.id)

                SongRowItem(
                    song = song,
                    isCurrent = isCurrent,
                    isFavorite = isFavorite,
                    onClick = { onSongClick(song) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onDelete = onDeleteSong?.let { delete -> { delete(song) } },
                    onAddToPlaylist = onAddToPlaylist?.let { add -> { add(song) } }
                )
            }
        }
    }
}

@Composable
fun SongRowItem(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    // Only inside a custom playlist
    onRemoveFromPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val queueActions = LocalQueueActions.current
    val showMessage = LocalShowMessage.current
    val songInfo = LocalSongInfo.current
    val editSong = LocalEditSong.current?.takeIf { !song.isOnline }
    if (showInfo && songInfo != null) {
        SongInfoDialog(song = song, source = songInfo, onDismiss = { showInfo = false })
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Long-press: Song info
            .combinedClickable(onClick = onClick, onLongClick = songInfo?.let { { showInfo = true } })
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongThumbnail(
            albumArtUri = song.albumArtUri,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (song.artist.isNotBlank() && song.artist != "<unknown>")
                    "${song.artist} • ${formatSongDuration(song.duration)}"
                else
                    formatSongDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (queueActions != null || onDelete != null || onAddToPlaylist != null || onRemoveFromPlaylist != null || songInfo != null || editSong != null) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (queueActions != null) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            leadingIcon = {
                                Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                queueActions.playNext(song)
                                showMessage("Playing next: ${song.title}")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = {
                                Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                queueActions.addToQueue(song)
                                showMessage("Added to queue: ${song.title}")
                            }
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist…") },
                            leadingIcon = {
                                Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from this playlist") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.PlaylistRemove, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onRemoveFromPlaylist()
                            }
                        )
                    }
                    if (editSong != null) {
                        DropdownMenuItem(
                            text = { Text("Edit details\u2026") },
                            leadingIcon = { Icon(imageVector = Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                editSong(song)
                            }
                        )
                    }
                    if (songInfo != null) {
                        DropdownMenuItem(
                            text = { Text("Song info") },
                            leadingIcon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showInfo = true
                            }
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete from phone", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

fun formatSongDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

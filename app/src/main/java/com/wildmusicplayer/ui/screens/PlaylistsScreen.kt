package com.wildmusicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.model.Song

sealed class PlaylistSelection {
    data object None : PlaylistSelection()
    data object Favorites : PlaylistSelection()
    data class Folder(val folderName: String, val songs: List<Song>) : PlaylistSelection()
    data class Custom(val playlist: Playlist) : PlaylistSelection()
}

@Composable
fun PlaylistsScreen(
    allSongs: List<Song>,
    favoriteSongIds: Set<Long>,
    customPlaylists: List<Playlist>,
    currentPlayingSongId: Long?,
    bottomPadding: Dp,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayPlaylist: (List<Song>, shuffle: Boolean) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onCreatePlaylist: (name: String, songIds: List<Long>) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    var selectedPlaylist by remember { mutableStateOf<PlaylistSelection>(PlaylistSelection.None) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableIntStateOf(0) } // 0 = From Folder, 1 = Custom Name
    var newPlaylistName by remember { mutableStateOf("") }

    val favoriteSongs = remember(allSongs, favoriteSongIds) {
        allSongs.filter { favoriteSongIds.contains(it.id) }
    }

    // Auto-organize all songs by their parent device folder
    val folderPlaylists = remember(allSongs) {
        allSongs.groupBy { it.folderName }
            .map { (folder, songs) -> folder to songs }
            .sortedByDescending { it.second.size }
    }

    when (val selection = selectedPlaylist) {
        is PlaylistSelection.None -> {
            // Main Playlists Overview Screen
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                // Header with "Create Playlist" + button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Your Playlists",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Folders, favorites, and custom collections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New")
                        }
                    }
                }

                // Built-in Favorites Playlist Card
                item {
                    PlaylistCard(
                        title = "Favorite Songs",
                        subtitle = "${favoriteSongs.size} tracks liked",
                        icon = Icons.Default.Favorite,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { selectedPlaylist = PlaylistSelection.Favorites }
                    )
                }

                // Device Music Folders Section (Automatic Folder Playlists like "Personal Favorite")
                if (folderPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Music Folders (${folderPlaylists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "Auto-organized by folder on your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                        )
                    }

                    items(folderPlaylists, key = { "folder_${it.first}" }) { (folderName, songs) ->
                        PlaylistCard(
                            title = folderName,
                            subtitle = "${songs.size} ${if (songs.size == 1) "track" else "tracks"}",
                            icon = Icons.Default.Folder,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            onClick = { selectedPlaylist = PlaylistSelection.Folder(folderName, songs) }
                        )
                    }
                }

                // Custom Playlists Section
                if (customPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Custom Playlists (${customPlaylists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    items(customPlaylists, key = { it.id }) { playlist ->
                        val trackCount = playlist.songIds.size
                        PlaylistCard(
                            title = playlist.name,
                            subtitle = "$trackCount ${if (trackCount == 1) "track" else "tracks"}",
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            trailingAction = {
                                IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Playlist",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            },
                            onClick = { selectedPlaylist = PlaylistSelection.Custom(playlist) }
                        )
                    }
                }
            }
        }

        is PlaylistSelection.Favorites -> {
            // Detailed Favorites View
            PlaylistDetailView(
                title = "Favorite Songs ❤️",
                subtitle = "${favoriteSongs.size} tracks",
                songs = favoriteSongs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song -> onSongClick(song, favoriteSongs) },
                onPlayAll = { onPlayPlaylist(favoriteSongs, false) },
                onShuffleAll = { onPlayPlaylist(favoriteSongs, true) },
                onToggleFavorite = onToggleFavorite
            )
        }

        is PlaylistSelection.Folder -> {
            // Detailed Folder Playlist View (e.g. "Personal Favorite")
            PlaylistDetailView(
                title = selection.folderName,
                subtitle = "${selection.songs.size} tracks • Folder playlist",
                songs = selection.songs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song -> onSongClick(song, selection.songs) },
                onPlayAll = { onPlayPlaylist(selection.songs, false) },
                onShuffleAll = { onPlayPlaylist(selection.songs, true) },
                onToggleFavorite = onToggleFavorite
            )
        }

        is PlaylistSelection.Custom -> {
            // Detailed Custom Playlist View
            val playlist = customPlaylists.find { it.id == selection.playlist.id } ?: selection.playlist
            val playlistSongs = remember(allSongs, playlist.songIds) {
                playlist.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
            }

            PlaylistDetailView(
                title = playlist.name,
                subtitle = "${playlistSongs.size} tracks",
                songs = playlistSongs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song -> onSongClick(song, playlistSongs) },
                onPlayAll = { onPlayPlaylist(playlistSongs, false) },
                onShuffleAll = { onPlayPlaylist(playlistSongs, true) },
                onToggleFavorite = onToggleFavorite
            )
        }
    }

    // Reworked "New Playlist" Modal Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = {
                Text(
                    text = "New Playlist",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mode Selector Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = (createMode == 0),
                            onClick = { createMode = 0 },
                            label = { Text("From Folder") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = (createMode == 1),
                            onClick = { createMode = 1 },
                            label = { Text("Custom Name") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (createMode == 0) {
                        // Option 1: Import directly from an existing device folder (e.g. "Personal Favorite")
                        Text(
                            text = "Select a folder to import as a custom playlist:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (folderPlaylists.isEmpty()) {
                            Text(
                                text = "No device folders found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                folderPlaylists.forEach { (folderName, songs) ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onCreatePlaylist(folderName, songs.map { it.id })
                                                showCreateDialog = false
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = folderName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${songs.size} tracks",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    onCreatePlaylist(folderName, songs.map { it.id })
                                                    showCreateDialog = false
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Import", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Option 2: Enter custom playlist name
                        Text(
                            text = "Give your playlist a custom name:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("e.g. Chill Beats, Car Mix...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (createMode == 1) {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName.trim(), emptyList())
                                newPlaylistName = ""
                                showCreateDialog = false
                            }
                        },
                        enabled = newPlaylistName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun PlaylistCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    trailingAction: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    title: String,
    subtitle: String,
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    currentPlayingSongId: Long?,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Detail Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to playlists",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (songs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All")
                }
                FilledTonalButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isCurrent = song.id == currentPlayingSongId
                    val isFavorite = favoriteSongIds.contains(song.id)

                    SongRowItem(
                        song = song,
                        isCurrent = isCurrent,
                        isFavorite = isFavorite,
                        onClick = { onSongClick(song) },
                        onToggleFavorite = { onToggleFavorite(song.id) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This playlist is empty.\nAdd tracks to see them here!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

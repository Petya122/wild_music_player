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
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildmusicplayer.data.DownloadPrecondition
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.YouTubeTrack
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.service.MusicControllerManager
import com.wildmusicplayer.service.SongDownloadService
import kotlinx.coroutines.launch

enum class PlaylistFilter(val label: String) {
    ALL("All Playlists"),
    OFFLINE_ONLY("Offline Ready"),
    HYBRID("Hybrid (Cloud)")
}

enum class PlaylistDetailFilter(val label: String) {
    ALL("All"),
    LOCAL("Local"),
    ONLINE("Online")
}

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
    onDeletePlaylist: (String) -> Unit,
    downloadManager: YouTubeDownloadManager? = null,
    controllerManager: MusicControllerManager? = null,
    networkPolicyManager: NetworkPolicyManager? = null,
    onRemoveOnlineTrack: ((playlistId: String, trackId: String) -> Unit)? = null
) {
    var selectedPlaylist by remember { mutableStateOf<PlaylistSelection>(PlaylistSelection.None) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableIntStateOf(0) } // 0 = From Folder, 1 = Custom Name
    var newPlaylistName by remember { mutableStateOf("") }
    var overviewFilter by remember { mutableStateOf(PlaylistFilter.ALL) }

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
            val hybridCount = remember(customPlaylists) {
                customPlaylists.count { it.onlineTracks.isNotEmpty() }
            }
            val offlineCount = remember(folderPlaylists, customPlaylists) {
                folderPlaylists.size + 1 + customPlaylists.count { it.onlineTracks.isEmpty() }
            }
            val totalCount = folderPlaylists.size + 1 + customPlaylists.size

            val filteredCustomPlaylists = remember(customPlaylists, overviewFilter) {
                when (overviewFilter) {
                    PlaylistFilter.ALL -> customPlaylists
                    PlaylistFilter.OFFLINE_ONLY -> customPlaylists.filter { it.onlineTracks.isEmpty() }
                    PlaylistFilter.HYBRID -> customPlaylists.filter { it.onlineTracks.isNotEmpty() }
                }
            }

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

                // Filter Chips Row (All, Offline Ready, Hybrid Cloud)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = (overviewFilter == PlaylistFilter.ALL),
                            onClick = { overviewFilter = PlaylistFilter.ALL },
                            label = { Text("All ($totalCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = (overviewFilter == PlaylistFilter.OFFLINE_ONLY),
                            onClick = { overviewFilter = PlaylistFilter.OFFLINE_ONLY },
                            label = { Text("Offline ($offlineCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = (overviewFilter == PlaylistFilter.HYBRID),
                            onClick = { overviewFilter = PlaylistFilter.HYBRID },
                            label = { Text("Hybrid ($hybridCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Built-in Favorites Playlist Card (Hidden if Hybrid filter is active)
                if (overviewFilter != PlaylistFilter.HYBRID) {
                    item {
                        PlaylistCard(
                            title = "Favorite Songs",
                            subtitle = "${favoriteSongs.size} tracks liked",
                            icon = Icons.Default.Favorite,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { selectedPlaylist = PlaylistSelection.Favorites }
                        )
                    }
                }

                // Device Music Folders Section (Hidden if Hybrid filter is active)
                if (overviewFilter != PlaylistFilter.HYBRID && folderPlaylists.isNotEmpty()) {
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

                // Custom Playlists Section (Filtered by overviewFilter)
                if (overviewFilter == PlaylistFilter.HYBRID && filteredCustomPlaylists.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No Hybrid Playlists Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Search YouTube in the Downloader tab and tap 'Add to Playlist' to create hybrid collections!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else if (filteredCustomPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Custom Playlists (${filteredCustomPlaylists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    items(filteredCustomPlaylists, key = { it.id }) { playlist ->
                        val localCount = playlist.songIds.size
                        val onlineCount = playlist.onlineTracks.size
                        val totalCount = localCount + onlineCount
                        val subtitle = if (onlineCount > 0) {
                            "$totalCount tracks ($localCount local, $onlineCount online)"
                        } else {
                            "$totalCount ${if (totalCount == 1) "track" else "tracks"}"
                        }
                        PlaylistCard(
                            title = playlist.name,
                            subtitle = subtitle,
                            icon = if (onlineCount > 0) Icons.Default.CloudSync else Icons.AutoMirrored.Filled.QueueMusic,
                            iconTint = if (onlineCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
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
            val localCount = playlistSongs.size
            val onlineCount = playlist.onlineTracks.size
            val totalCount = localCount + onlineCount
            val subtitle = if (onlineCount > 0) {
                "$totalCount tracks ($localCount local, $onlineCount online)"
            } else {
                "$totalCount tracks"
            }

            PlaylistDetailView(
                title = playlist.name,
                subtitle = subtitle,
                songs = playlistSongs,
                onlineTracks = playlist.onlineTracks,
                allSongs = allSongs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song -> onSongClick(song, playlistSongs) },
                onPlayAll = { onPlayPlaylist(playlistSongs, false) },
                onShuffleAll = { onPlayPlaylist(playlistSongs, true) },
                onToggleFavorite = onToggleFavorite,
                downloadManager = downloadManager,
                controllerManager = controllerManager,
                networkPolicyManager = networkPolicyManager,
                onRemoveOnlineTrack = { trackId ->
                    onRemoveOnlineTrack?.invoke(playlist.id, trackId)
                }
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
    onToggleFavorite: (Long) -> Unit,
    onlineTracks: List<OnlineTrack> = emptyList(),
    allSongs: List<Song> = emptyList(),
    downloadManager: YouTubeDownloadManager? = null,
    controllerManager: MusicControllerManager? = null,
    networkPolicyManager: NetworkPolicyManager? = null,
    onRemoveOnlineTrack: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detailFilter by remember { mutableStateOf(PlaylistDetailFilter.ALL) }

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

        // Hybrid Sync Offline Banner
        if (onlineTracks.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hybrid Playlist",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${onlineTracks.size} online ${if (onlineTracks.size == 1) "track" else "tracks"} can be synced for offline play.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val isOffline = networkPolicyManager?.isOfflineOnlyMode ?: false
                            if (isOffline) {
                                Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to sync playlist.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            when (networkPolicyManager?.checkDownloadPrecondition()) {
                                is DownloadPrecondition.BlockedOfflineMode -> {
                                    Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to sync playlist.", Toast.LENGTH_SHORT).show()
                                }
                                is DownloadPrecondition.BlockedNoNetwork -> {
                                    Toast.makeText(context, "No internet connection available.", Toast.LENGTH_SHORT).show()
                                }
                                is DownloadPrecondition.BlockedMetered -> {
                                    Toast.makeText(context, "Downloads blocked on metered network (check Settings).", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    onlineTracks.forEach { ot ->
                                        val ytTrack = YouTubeTrack(
                                            id = ot.id,
                                            title = ot.title,
                                            uploaderName = ot.artist,
                                            durationSeconds = ot.durationSeconds,
                                            thumbnailUrl = ot.thumbnailUrl,
                                            webUrl = ot.webUrl
                                        )
                                        SongDownloadService.startDownload(context, ytTrack)
                                    }
                                    Toast.makeText(context, "Syncing ${onlineTracks.size} tracks for offline playback in background...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Filter Chips Row for hybrid playlist (All, Local, Online)
        if (onlineTracks.isNotEmpty() && songs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = (detailFilter == PlaylistDetailFilter.ALL),
                    onClick = { detailFilter = PlaylistDetailFilter.ALL },
                    label = { Text("All (${songs.size + onlineTracks.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = (detailFilter == PlaylistDetailFilter.LOCAL),
                    onClick = { detailFilter = PlaylistDetailFilter.LOCAL },
                    label = { Text("Local (${songs.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = (detailFilter == PlaylistDetailFilter.ONLINE),
                    onClick = { detailFilter = PlaylistDetailFilter.ONLINE },
                    label = { Text("Online (${onlineTracks.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (songs.isNotEmpty() || onlineTracks.isNotEmpty()) {
            if (songs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onPlayAll()
                            if (onlineTracks.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Playing ${songs.size} offline tracks. Sync remaining ${onlineTracks.size} online tracks for complete offline listening.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All")
                    }
                    FilledTonalButton(
                        onClick = {
                            onShuffleAll()
                            if (onlineTracks.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Shuffling ${songs.size} offline tracks. Sync remaining ${onlineTracks.size} online tracks for complete offline listening.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                if (songs.isNotEmpty() && (detailFilter == PlaylistDetailFilter.ALL || detailFilter == PlaylistDetailFilter.LOCAL)) {
                    if (onlineTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Local Tracks (${songs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

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

                if (onlineTracks.isNotEmpty() && (detailFilter == PlaylistDetailFilter.ALL || detailFilter == PlaylistDetailFilter.ONLINE)) {
                    item {
                        Text(
                            text = "Online Stream Tracks (${onlineTracks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    items(onlineTracks, key = { it.id }) { track ->
                        val cleanOnline = track.title.trim().lowercase()
                        val localMatch = allSongs.find { local ->
                            val cleanLocal = local.title.trim().lowercase()
                            cleanLocal == cleanOnline ||
                            (cleanLocal.length >= 5 && cleanOnline.length >= 5 &&
                                (cleanLocal.contains(cleanOnline) || cleanOnline.contains(cleanLocal)))
                        }

                        OnlineTrackRowItem(
                            track = track,
                            isLocallyAvailable = (localMatch != null),
                            onTrackClick = {
                                if (localMatch != null) {
                                    Toast.makeText(context, "Playing local offline audio: ${localMatch.title}", Toast.LENGTH_SHORT).show()
                                    onSongClick(localMatch)
                                } else {
                                    val isOffline = networkPolicyManager?.isOfflineOnlyMode ?: false
                                    if (isOffline) {
                                        Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to stream.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (downloadManager != null && controllerManager != null) {
                                            Toast.makeText(context, "Extracting stream: ${track.title}...", Toast.LENGTH_SHORT).show()
                                            scope.launch {
                                                val ytTrack = YouTubeTrack(
                                                    id = track.id,
                                                    title = track.title,
                                                    uploaderName = track.artist,
                                                    durationSeconds = track.durationSeconds,
                                                    thumbnailUrl = track.thumbnailUrl,
                                                    webUrl = track.webUrl
                                                )
                                                val streamUrl = downloadManager.getStreamUrl(ytTrack)
                                                if (!streamUrl.isNullOrBlank()) {
                                                    controllerManager.playOnlineStream(
                                                        title = track.title,
                                                        artist = track.artist,
                                                        streamUrl = streamUrl,
                                                        artworkUrl = track.thumbnailUrl
                                                    )
                                                } else {
                                                    Toast.makeText(context, "Unable to extract stream for playback", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onDownloadClick = {
                                val isOffline = networkPolicyManager?.isOfflineOnlyMode ?: false
                                if (isOffline) {
                                    Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to download.", Toast.LENGTH_SHORT).show()
                                    return@OnlineTrackRowItem
                                }
                                when (networkPolicyManager?.checkDownloadPrecondition()) {
                                    is DownloadPrecondition.BlockedOfflineMode -> {
                                        Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to download.", Toast.LENGTH_SHORT).show()
                                    }
                                    is DownloadPrecondition.BlockedNoNetwork -> {
                                        Toast.makeText(context, "No internet connection available.", Toast.LENGTH_SHORT).show()
                                    }
                                    is DownloadPrecondition.BlockedMetered -> {
                                        Toast.makeText(context, "Downloads blocked on metered network (check Settings).", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        val ytTrack = YouTubeTrack(
                                            id = track.id,
                                            title = track.title,
                                            uploaderName = track.artist,
                                            durationSeconds = track.durationSeconds,
                                            thumbnailUrl = track.thumbnailUrl,
                                            webUrl = track.webUrl
                                        )
                                        SongDownloadService.startDownload(context, ytTrack)
                                        Toast.makeText(context, "Downloading in background: ${track.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onRemoveClick = if (onRemoveOnlineTrack != null) {
                                { onRemoveOnlineTrack(track.id) }
                            } else null
                        )
                    }
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

@Composable
private fun OnlineTrackRowItem(
    track: OnlineTrack,
    isLocallyAvailable: Boolean = false,
    onTrackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onTrackClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(46.dp)
            ) {
                if (!track.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLocallyAvailable) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        }
                    ) {
                        Text(
                            text = if (isLocallyAvailable) "💾 Local Ready" else "🌐 Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLocallyAvailable) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Download / Check single track action
            if (isLocallyAvailable) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Downloaded to device",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Offline",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Remove from playlist
            if (onRemoveClick != null) {
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

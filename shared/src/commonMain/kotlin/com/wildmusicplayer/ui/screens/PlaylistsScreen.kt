package com.wildmusicplayer.ui.screens

import com.wildmusicplayer.data.playQueue
import com.wildmusicplayer.data.upscaleGoogleThumbnail
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
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Whatshot
import com.wildmusicplayer.ui.LocalShowUndo
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.wildmusicplayer.ui.LocalQueueActions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import coil3.request.crossfade
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.wildmusicplayer.platform.DownloadBlock
import com.wildmusicplayer.platform.TrackDownloader
import com.wildmusicplayer.ui.LocalShowMessage
import com.wildmusicplayer.ui.PlatformBackHandler
import kotlinx.coroutines.flow.MutableStateFlow
import coil3.request.ImageRequest
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.PLAYLIST_AUTO_UPDATE_MS
import com.wildmusicplayer.data.updatedAgoText
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Sync
import kotlinx.coroutines.launch
import kotlin.time.Clock
import com.wildmusicplayer.data.foldersOf
import com.wildmusicplayer.data.isDownloadedCopyOf
import com.wildmusicplayer.model.Song

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
    data object MostPlayed : PlaylistSelection()
    data object RecentlyAdded : PlaylistSelection()
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
    downloadIndex: DownloadIndex,
    isOfflineOnlyMode: Boolean = false,
    // False in a version without online songs: no All / Offline / Hybrid filter
    showOnlineFilters: Boolean = true,
    downloader: TrackDownloader? = null,
    onRemoveOnlineTrack: ((playlistId: String, trackId: String) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onRemoveSongFromPlaylist: ((playlistId: String, songId: Long) -> Unit)? = null,
    onExportPlaylist: ((Playlist) -> Unit)? = null,
    onRenamePlaylist: ((playlistId: String, name: String) -> Unit)? = null,
    // Undo for the removals above: put the song / track back at its old place
    onRestoreSongToPlaylist: ((playlistId: String, songId: Long, index: Int) -> Unit)? = null,
    onRestoreOnlineTrack: ((playlistId: String, track: OnlineTrack, index: Int) -> Unit)? = null,
    onReorderPlaylist: ((playlistId: String, songIds: List<Long>, onlineTrackIds: List<String>) -> Unit)? = null,
    // How often each song was played, for the automatic "Most Played" playlist
    playCounts: Map<Long, Int> = emptyMap(),
    // A custom playlist started playing (for the app icon's "last playlist" shortcut)
    onPlaylistPlayed: (Playlist) -> Unit = {},
    // GitHub version: "Update from YouTube" for an imported playlist (how many songs it added, or why it failed)
    onUpdateFromYouTube: (suspend (Playlist) -> Result<Int>)? = null,
    // GitHub version: the "Keep downloaded" switch of a playlist with online songs
    onKeepDownloadedChange: ((Playlist, Boolean) -> Unit)? = null
) {
    val mostPlayedSongs = remember(allSongs, playCounts) {
        allSongs.filter { (playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { playCounts[it.id] ?: 0 }
            .take(AUTO_PLAYLIST_SIZE)
    }
    // MediaStore numbers files in the order they were added, so the highest IDs are the newest
    val recentlyAddedSongs = remember(allSongs) {
        allSongs.sortedByDescending { it.id }.take(AUTO_PLAYLIST_SIZE)
    }
    var selectedPlaylist by remember { mutableStateOf<PlaylistSelection>(PlaylistSelection.None) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableIntStateOf(0) } // 0 = From Folder, 1 = Custom Name
    var newPlaylistName by remember { mutableStateOf("") }
    var overviewFilter by remember { mutableStateOf(PlaylistFilter.ALL) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    val showUndo = LocalShowUndo.current

    // Back from an open playlist returns to the playlist list, not to Home
    PlatformBackHandler(enabled = selectedPlaylist !is PlaylistSelection.None) {
        selectedPlaylist = PlaylistSelection.None
    }

    val favoriteSongs = remember(allSongs, favoriteSongIds) {
        allSongs.filter { favoriteSongIds.contains(it.id) }
    }

    // Auto-organize all songs by their folder on the phone (by full path: two "Music" folders stay apart)
    val folderPlaylists = remember(allSongs) {
        foldersOf(allSongs).sortedByDescending { it.songs.size }
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
                if (showOnlineFilters) item {
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
                            subtitle = "${favoriteSongs.size} ${if (favoriteSongs.size == 1) "track" else "tracks"} liked",
                            icon = Icons.Default.Favorite,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { selectedPlaylist = PlaylistSelection.Favorites }
                        )
                    }
                    if (mostPlayedSongs.isNotEmpty()) {
                        item {
                            PlaylistCard(
                                title = "Most Played",
                                subtitle = "Your top ${mostPlayedSongs.size} ${if (mostPlayedSongs.size == 1) "song" else "songs"}",
                                icon = Icons.Default.Whatshot,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = { selectedPlaylist = PlaylistSelection.MostPlayed }
                            )
                        }
                    }
                    if (recentlyAddedSongs.isNotEmpty()) {
                        item {
                            PlaylistCard(
                                title = "Recently Added",
                                subtitle = "The newest ${recentlyAddedSongs.size} ${if (recentlyAddedSongs.size == 1) "song" else "songs"} on your phone",
                                icon = Icons.Default.LibraryAdd,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = { selectedPlaylist = PlaylistSelection.RecentlyAdded }
                            )
                        }
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

                    items(folderPlaylists, key = { "folder_${it.key}" }) { folder ->
                        val count = folder.songs.size
                        PlaylistCard(
                            title = folder.title,
                            subtitle = "${folder.subtitle} • $count ${if (count == 1) "track" else "tracks"}",
                            icon = Icons.Default.Folder,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            onClick = { selectedPlaylist = PlaylistSelection.Folder(folder.title, folder.songs) }
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
                                Row {
                                    if (onExportPlaylist != null) {
                                        IconButton(onClick = { onExportPlaylist(playlist) }) {
                                            Icon(
                                                imageVector = Icons.Default.IosShare,
                                                contentDescription = "Export as M3U8",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(onClick = { playlistToDelete = playlist }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Playlist",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
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
                onToggleFavorite = onToggleFavorite,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist
            )
        }

        is PlaylistSelection.MostPlayed -> {
            PlaylistDetailView(
                title = "Most Played 🔥",
                subtitle = "${mostPlayedSongs.size} tracks • most played first",
                songs = mostPlayedSongs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song -> onSongClick(song, mostPlayedSongs) },
                onPlayAll = { onPlayPlaylist(mostPlayedSongs, false) },
                onShuffleAll = { onPlayPlaylist(mostPlayedSongs, true) },
                onToggleFavorite = onToggleFavorite,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist
            )
        }

        is PlaylistSelection.RecentlyAdded -> {
            PlaylistDetailView(
                title = "Recently Added 🆕",
                subtitle = "${recentlyAddedSongs.size} tracks • newest first",
                songs = recentlyAddedSongs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song -> onSongClick(song, recentlyAddedSongs) },
                onPlayAll = { onPlayPlaylist(recentlyAddedSongs, false) },
                onShuffleAll = { onPlayPlaylist(recentlyAddedSongs, true) },
                onToggleFavorite = onToggleFavorite,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist
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
                onToggleFavorite = onToggleFavorite,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist
            )
        }

        is PlaylistSelection.Custom -> {
            // Detailed Custom Playlist View
            val playlist = customPlaylists.find { it.id == selection.playlist.id } ?: selection.playlist
            val playlistSongs = remember(allSongs, playlist.songIds) {
                playlist.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
            }

            // One queue for the whole playlist: local songs, then online tracks (the downloaded copy
            // if there is one, otherwise streamed). Undownloaded online tracks are skipped offline.
            val playQueue = remember(allSongs, playlist, isOfflineOnlyMode) {
                playlist.playQueue(allSongs, { downloadIndex.downloadedIdFor(it) }, isOfflineOnlyMode)
            }
            // Online tracks already downloaded count as local
            val downloadedOnline = playlist.onlineTracks.count { downloadedCopyOf(it, allSongs, downloadIndex) != null }
            val localCount = playlistSongs.size + downloadedOnline
            val onlineCount = playlist.onlineTracks.size - downloadedOnline
            val totalCount = localCount + onlineCount
            val subtitle = if (onlineCount > 0) {
                "$totalCount tracks ($localCount local, $onlineCount online)"
            } else {
                "$totalCount tracks"
            }

            // Imported from YouTube: where from, the update button, and a quiet update when it's been a while
            val source = playlist.source
            val now = remember(source?.updatedAt) { Clock.System.now().toEpochMilliseconds() }
            val updateFromYouTube: (suspend () -> Result<Int>)? =
                if (source != null && onUpdateFromYouTube != null) suspend { onUpdateFromYouTube(playlist) } else null

            PlaylistDetailView(
                title = playlist.name,
                subtitle = subtitle,
                sourceLabel = source?.let { "From YouTube · ${updatedAgoText(it.updatedAt, now)}" },
                keepDownloaded = playlist.keepDownloaded,
                onKeepDownloadedChange = onKeepDownloadedChange
                    ?.takeIf { downloader != null && playlist.onlineTracks.isNotEmpty() }
                    ?.let { change -> { on: Boolean -> change(playlist, on) } },
                onUpdateFromYouTube = updateFromYouTube,
                autoUpdate = updateFromYouTube != null && !isOfflineOnlyMode &&
                    now - (source?.updatedAt ?: now) > PLAYLIST_AUTO_UPDATE_MS,
                songs = playlistSongs,
                onlineTracks = playlist.onlineTracks,
                allSongs = allSongs,
                favoriteSongIds = favoriteSongIds,
                currentPlayingSongId = currentPlayingSongId,
                bottomPadding = bottomPadding,
                onBack = { selectedPlaylist = PlaylistSelection.None },
                onSongClick = { song ->
                    onSongClick(song, playQueue)
                    onPlaylistPlayed(playlist)
                },
                onPlayAll = {
                    onPlayPlaylist(playQueue, false)
                    onPlaylistPlayed(playlist)
                },
                onShuffleAll = {
                    onPlayPlaylist(playQueue, true)
                    onPlaylistPlayed(playlist)
                },
                onToggleFavorite = onToggleFavorite,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist,
                onRemoveFromPlaylist = { song ->
                    val index = playlist.songIds.indexOf(song.id)
                    onRemoveSongFromPlaylist?.invoke(playlist.id, song.id)
                    onRestoreSongToPlaylist?.let { restore ->
                        showUndo("Removed from ${playlist.name}") { restore(playlist.id, song.id, index) }
                    }
                },
                onRename = onRenamePlaylist?.let { rename -> { name: String -> rename(playlist.id, name) } },
                onReorder = onReorderPlaylist?.let { reorder ->
                    { songIds: List<Long>, trackIds: List<String> -> reorder(playlist.id, songIds, trackIds) }
                },
                isOfflineOnlyMode = isOfflineOnlyMode,
                downloadIndex = downloadIndex,
                downloader = downloader,
                onPlayOnlineTrack = { track ->
                    val song = playQueue.firstOrNull { Song.webUrlOf(it.contentUri) == track.webUrl }
                    if (song != null) onSongClick(song, playQueue)
                },
                onRemoveOnlineTrack = { trackId ->
                    val index = playlist.onlineTracks.indexOfFirst { it.id == trackId }
                    val track = playlist.onlineTracks.getOrNull(index)
                    onRemoveOnlineTrack?.invoke(playlist.id, trackId)
                    if (track != null) {
                        onRestoreOnlineTrack?.let { restore ->
                            showUndo("Removed from ${playlist.name}") { restore(playlist.id, track, index) }
                        }
                    }
                }
            )
        }
    }

    // One tap on the bin shouldn't lose a playlist
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete \"${playlist.name}\"?", fontWeight = FontWeight.Bold) },
            text = { Text("The playlist is removed from the app. The songs stay on your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(playlist.id)
                    playlistToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") } }
        )
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
                                folderPlaylists.forEach { folder ->
                                    val folderName = folder.title
                                    val songs = folder.songs
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
                                                        text = "${folder.subtitle} • ${songs.size} tracks",
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
    isOfflineOnlyMode: Boolean = false,
    downloadIndex: DownloadIndex? = null,
    downloader: TrackDownloader? = null,
    onPlayOnlineTrack: ((OnlineTrack) -> Unit)? = null,
    onRemoveOnlineTrack: ((String) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onRemoveFromPlaylist: ((Song) -> Unit)? = null,
    // Only custom playlists can be renamed and reordered
    onRename: ((String) -> Unit)? = null,
    onReorder: ((songIds: List<Long>, onlineTrackIds: List<String>) -> Unit)? = null,
    // Imported from YouTube: "From YouTube · updated 3 h ago", the update button, and whether to update now
    sourceLabel: String? = null,
    onUpdateFromYouTube: (suspend () -> Result<Int>)? = null,
    autoUpdate: Boolean = false,
    // "Keep downloaded": the online songs download by themselves; null hides the switch
    keepDownloaded: Boolean = false,
    onKeepDownloadedChange: ((Boolean) -> Unit)? = null
) {
    val showMessage = LocalShowMessage.current
    val updateScope = rememberCoroutineScope()
    var isUpdating by remember { mutableStateOf(false) }
    // quiet: the automatic update only speaks up when it found new songs
    val runUpdate: (Boolean) -> Unit = { quiet ->
        val update = onUpdateFromYouTube
        if (update != null && !isUpdating) {
            isUpdating = true
            updateScope.launch {
                val result = update()
                isUpdating = false
                result.onSuccess { added ->
                    when {
                        added == 1 -> showMessage("1 new song added from YouTube")
                        added > 1 -> showMessage("$added new songs added from YouTube")
                        !quiet -> showMessage("Already up to date with YouTube")
                    }
                }.onFailure { error ->
                    if (!quiet) showMessage(error.message ?: "Couldn't update from YouTube")
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (autoUpdate) runUpdate(true)
    }
    val queueActions = LocalQueueActions.current
    var detailFilter by remember { mutableStateOf(PlaylistDetailFilter.ALL) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }

    // Local copies follow the finger while dragging; the playlist is saved when a song is dropped
    var isDragging by remember { mutableStateOf(false) }
    var songOrder by remember { mutableStateOf(songs) }
    var trackOrder by remember { mutableStateOf(onlineTracks) }
    LaunchedEffect(songs, onlineTracks) {
        if (!isDragging) {
            songOrder = songs
            trackOrder = onlineTracks
        }
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key
        val toKey = to.key
        // Songs move among songs, online tracks among online tracks
        if (fromKey is Long && toKey is Long) {
            val i = songOrder.indexOfFirst { it.id == fromKey }
            val j = songOrder.indexOfFirst { it.id == toKey }
            if (i >= 0 && j >= 0) songOrder = songOrder.toMutableList().apply { add(j, removeAt(i)) }
        } else if (fromKey is String && toKey is String && fromKey.startsWith(ONLINE_KEY) && toKey.startsWith(ONLINE_KEY)) {
            val i = trackOrder.indexOfFirst { ONLINE_KEY + it.id == fromKey }
            val j = trackOrder.indexOfFirst { ONLINE_KEY + it.id == toKey }
            if (i >= 0 && j >= 0) trackOrder = trackOrder.toMutableList().apply { add(j, removeAt(i)) }
        }
    }
    val commitOrder = {
        isDragging = false
        onReorder?.invoke(songOrder.map { it.id }, trackOrder.map { it.id })
    }
    val activeDownloads by (downloader?.activeDownloads ?: remember { MutableStateFlow(emptyMap()) }).collectAsState()

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
                if (sourceLabel != null) {
                    Text(
                        text = if (isUpdating) "Updating from YouTube…" else sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onUpdateFromYouTube != null) {
                IconButton(onClick = { runUpdate(false) }, enabled = !isUpdating) {
                    if (isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = "Update from YouTube", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (onRename != null) {
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onReorder != null && songs.size + onlineTracks.size > 1) {
                IconButton(onClick = { isReordering = !isReordering }) {
                    Icon(
                        imageVector = if (isReordering) Icons.Default.Check else Icons.Default.SwapVert,
                        contentDescription = if (isReordering) "Done reordering" else "Reorder songs",
                        tint = if (isReordering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showRenameDialog && onRename != null) {
            RenamePlaylistDialog(
                currentName = title,
                onRename = { name ->
                    onRename(name)
                    showRenameDialog = false
                },
                onDismiss = { showRenameDialog = false }
            )
        }

        // Hybrid Sync Offline Banner
        // Only tracks not on the phone yet: Sync All never downloads a song twice
        val notDownloaded = onlineTracks.filter { downloadedCopyOf(it, allSongs, downloadIndex) == null }
        // Without a downloader (a version with no downloads) there's nothing to sync
        if (notDownloaded.isNotEmpty() && downloader != null) {
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
                            text = "${notDownloaded.size} online ${if (notDownloaded.size == 1) "track" else "tracks"} can be synced for offline play.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (startDownloads(downloader, notDownloaded, "sync playlist", showMessage)) {
                                showMessage("Syncing ${notDownloaded.size} tracks for offline playback in background...")
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

        if (onKeepDownloadedChange != null && downloader != null) {
            KeepDownloadedRow(keepDownloaded, onKeepDownloadedChange)
            // While it's on, whatever isn't on the phone yet downloads (new songs from an update too),
            // as far as the download settings allow right now; the background check does the rest
            val missingIds = notDownloaded.map { it.id }
            LaunchedEffect(keepDownloaded, missingIds) {
                if (keepDownloaded && notDownloaded.isNotEmpty() && downloader.blockedReason() == null) {
                    val active = downloader.activeDownloads.value.keys
                    downloader.start(notDownloaded.filter { it.id !in active })
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
            // Online tracks join the queue unless Offline Only Mode is on
            val offlineSkipNotice = if (isOfflineOnlyMode && onlineTracks.isNotEmpty()) {
                "Offline Only Mode is active: online tracks that aren't downloaded are skipped."
            } else null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onPlayAll()
                        offlineSkipNotice?.let { showMessage(it) }
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
                        offlineSkipNotice?.let { showMessage(it) }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                if (songs.isNotEmpty() && (detailFilter == PlaylistDetailFilter.ALL || detailFilter == PlaylistDetailFilter.LOCAL)) {
                    if (onlineTracks.isNotEmpty()) {
                        item(key = "header:local") {
                            Text(
                                text = "Local Tracks (${songs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(songOrder, key = { it.id }) { song ->
                        val isCurrent = song.id == currentPlayingSongId
                        val isFavorite = favoriteSongIds.contains(song.id)

                        ReorderableItem(reorderState, key = song.id) { isDraggingItem ->
                            Row(
                                modifier = Modifier.background(
                                    if (isDraggingItem) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongRowItem(
                                    song = song,
                                    isCurrent = isCurrent,
                                    isFavorite = isFavorite,
                                    onClick = { onSongClick(song) },
                                    onToggleFavorite = { onToggleFavorite(song.id) },
                                    onDelete = onDeleteSong?.let { delete -> { delete(song) } },
                                    onAddToPlaylist = onAddToPlaylist?.let { add -> { add(song) } },
                                    onRemoveFromPlaylist = onRemoveFromPlaylist?.let { remove -> { remove(song) } },
                                    modifier = Modifier.weight(1f)
                                )
                                if (isReordering) {
                                    ReorderHandle(Modifier.draggableHandle(onDragStarted = { isDragging = true }, onDragStopped = { commitOrder() }))
                                }
                            }
                        }
                    }
                }

                if (onlineTracks.isNotEmpty() && (detailFilter == PlaylistDetailFilter.ALL || detailFilter == PlaylistDetailFilter.ONLINE)) {
                    item(key = "header:online") {
                        Text(
                            text = "Online Stream Tracks (${onlineTracks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    items(trackOrder, key = { ONLINE_KEY + it.id }) { track ->
                        // Only the file downloaded from this exact video counts as this track;
                        // another recording with the same title/artist is just "similar"
                        val localMatch = downloadedCopyOf(track, allSongs, downloadIndex)
                        val hasSimilarLocal = localMatch == null && allSongs.any { local ->
                            isDownloadedCopyOf(local.title, local.artist, track.title, track.artist)
                        }

                        // What "Play next" / "Add to queue" queue: the downloaded file, or the stream when online
                        val queueSong = localMatch ?: track.takeIf { !isOfflineOnlyMode && it.webUrl.isNotBlank() }
                            ?.let { Song.fromOnline(it.title, it.artist, it.webUrl, it.durationSeconds, it.thumbnailUrl) }

                        ReorderableItem(reorderState, key = ONLINE_KEY + track.id) { isDraggingItem ->
                            Row(
                                modifier = Modifier.background(
                                    if (isDraggingItem) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OnlineTrackRowItem(
                                    modifier = Modifier.weight(1f),
                                    track = track,
                                    isLocallyAvailable = (localMatch != null),
                                    hasSimilarLocal = hasSimilarLocal,
                                    downloadProgress = activeDownloads[track.id],
                                    onTrackClick = {
                                        if (localMatch != null) {
                                            showMessage("Playing local offline audio: ${localMatch.title}")
                                            onSongClick(localMatch)
                                        } else if (isOfflineOnlyMode) {
                                            showMessage("Offline Only Mode is active. Switch to Online Mode to stream.")
                                        } else {
                                            onPlayOnlineTrack?.invoke(track)
                                        }
                                    },
                                    onDownloadClick = {
                                        if (startDownloads(downloader, listOf(track), "download", showMessage)) {
                                            showMessage("Downloading in background: ${track.title}")
                                        }
                                    },
                                    onRemoveClick = if (onRemoveOnlineTrack != null) {
                                        { onRemoveOnlineTrack(track.id) }
                                    } else null,
                                    onPlayNext = queueSong?.let { queued -> queueActions?.let { actions -> {
                                        actions.playNext(queued)
                                        showMessage("Playing next: ${track.title}")
                                    } } },
                                    onAddToQueue = queueSong?.let { queued -> queueActions?.let { actions -> {
                                        actions.addToQueue(queued)
                                        showMessage("Added to queue: ${track.title}")
                                    } } }
                                )
                                if (isReordering) {
                                    ReorderHandle(Modifier.draggableHandle(onDragStarted = { isDragging = true }, onDragStopped = { commitOrder() }))
                                }
                            }
                        }
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

// How many songs the automatic playlists (Most Played, Recently Added) hold
private const val AUTO_PLAYLIST_SIZE = 50

// Online track keys in the playlist list, apart from song IDs (numbers) and section headers
private const val ONLINE_KEY = "online:"

@Composable
private fun ReorderHandle(modifier: Modifier) {
    IconButton(onClick = {}, modifier = modifier) {
        Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RenamePlaylistDialog(currentName: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OnlineTrackRowItem(
    modifier: Modifier = Modifier,
    track: OnlineTrack,
    isLocallyAvailable: Boolean = false,
    // Another song with the same title and artist is on the phone (not this exact video)
    hasSimilarLocal: Boolean = false,
    // 0..1 while this track is downloading, null otherwise
    downloadProgress: Float? = null,
    onTrackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
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
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(upscaleGoogleThumbnail(track.thumbnailUrl))
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
                            text = when {
                                isLocallyAvailable -> "💾 Local Ready"
                                hasSimilarLocal -> "🌐 Online · similar on phone"
                                else -> "🌐 Online"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                if (downloadProgress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Download / Check single track action
            if (downloadProgress != null) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        // Waiting in the queue or extracting the stream
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                }
            } else if (isLocallyAvailable) {
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

            if (onRemoveClick != null || onPlayNext != null || onAddToQueue != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onPlayNext != null) {
                            DropdownMenuItem(
                                text = { Text("Play next") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                onClick = { menuOpen = false; onPlayNext() }
                            )
                        }
                        if (onAddToQueue != null) {
                            DropdownMenuItem(
                                text = { Text("Add to queue") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                                onClick = { menuOpen = false; onAddToQueue() }
                            )
                        }
                        if (onRemoveClick != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from this playlist") },
                                leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null) },
                                onClick = { menuOpen = false; onRemoveClick() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Starts background downloads of [tracks], or tells the user why they can't start.
 * [action] completes "Switch to Online Mode to …". Returns true if the downloads started.
 */
private fun startDownloads(
    downloader: TrackDownloader?,
    tracks: List<OnlineTrack>,
    action: String,
    showMessage: (String) -> Unit
): Boolean {
    if (downloader == null) return false
    when (downloader.blockedReason()) {
        DownloadBlock.OFFLINE_MODE -> showMessage("Offline Only Mode is active. Switch to Online Mode to $action.")
        DownloadBlock.NO_NETWORK -> showMessage("No internet connection available.")
        DownloadBlock.METERED -> showMessage("Downloads blocked on metered network (check Settings).")
        null -> {
            downloader.start(tracks)
            return true
        }
    }
    return false
}

// The file the app downloaded from exactly this online track, if it's still on the phone
private fun downloadedCopyOf(track: OnlineTrack, allSongs: List<Song>, downloadIndex: DownloadIndex?): Song? =
    downloadIndex?.downloadedIdFor(track.webUrl.ifBlank { track.id })
        ?.let { id -> allSongs.find { it.id == id } }

/** The "Keep downloaded" switch under a playlist with online songs. */
@Composable
private fun KeepDownloadedRow(keepDownloaded: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Keep downloaded",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "New songs download by themselves, in the background on Wi-Fi. Good for the car.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = keepDownloaded, onCheckedChange = onChange)
    }
}

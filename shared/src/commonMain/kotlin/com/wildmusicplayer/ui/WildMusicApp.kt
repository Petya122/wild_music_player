package com.wildmusicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.platform.AudioEffects
import com.wildmusicplayer.ui.components.AddToPlaylistDialog
import com.wildmusicplayer.ui.components.QueueSheet
import com.wildmusicplayer.ui.components.DraggablePlayerSheet
import com.wildmusicplayer.ui.screens.HomeScreen
import com.wildmusicplayer.ui.screens.NavigationTab
import com.wildmusicplayer.ui.screens.PlaylistsScreen
import com.wildmusicplayer.ui.screens.SearchScreen
import com.wildmusicplayer.ui.screens.SongsScreen
import com.wildmusicplayer.ui.theme.ThemeMode
import com.wildmusicplayer.ui.theme.WildMusicPlayerTheme
import kotlinx.coroutines.launch

/**
 * The whole app built from shared screens only: used by iOS. (Android still uses MainActivity's own
 * shell, which adds the Downloader, Settings and other Android-only parts.)
 *
 * [emptyLibraryHint] explains how to add music when the library is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WildMusicApp(
    library: LibraryViewModel,
    audioEffects: AudioEffects,
    emptyLibraryHint: String
) {
    val themeName by library.settings.themeMode.collectAsState()
    val theme = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    CompositionLocalProvider(
        LocalShowMessage provides showMessage,
        LocalAudioEffects provides audioEffects,
        LocalQueueActions provides QueueActions(library.player::playNext, library.player::addToQueue)
    ) {
        WildMusicPlayerTheme(themeMode = theme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppContent(library, emptyLibraryHint, snackbarHostState, showMessage)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    library: LibraryViewModel,
    emptyLibraryHint: String,
    snackbarHostState: SnackbarHostState,
    showMessage: (String) -> Unit
) {
    val player = library.player
    val playlists = library.playlists

    val songs by library.songs.collectAsState()
    val favoriteSongs by library.favoriteSongs.collectAsState()
    val recentlyPlayedSongs by library.recentlyPlayedSongs.collectAsState()
    val favoriteSongIds by playlists.favoriteSongIds.collectAsState()
    val customPlaylists by playlists.customPlaylists.collectAsState()

    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    val isShuffleEnabled by player.isShuffleEnabled.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }

    val toggleFavorite: (Long) -> Unit = { songId ->
        if (!library.toggleFavorite(songId)) showMessage("Only songs on this device can be added to Favorites")
    }
    // The Downloader is Android-only: YouTube downloads aren't available on iOS
    val tabs = NavigationTab.entries.filter { it != NavigationTab.DOWNLOADER }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Wild Music Player") }) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                val bottomPadding = if (currentSong != null) 86.dp else 16.dp
                if (songs.isEmpty() && currentTab != NavigationTab.PLAYLISTS) {
                    EmptyLibrary(emptyLibraryHint)
                } else when (currentTab) {
                    NavigationTab.HOME -> HomeScreen(
                        allSongs = songs,
                        recentlyPlayedSongs = recentlyPlayedSongs,
                        favoriteSongs = favoriteSongs,
                        currentPlayingSongId = currentSong?.id,
                        bottomPadding = bottomPadding,
                        onSongClick = library::playSong,
                        onShuffleAll = library::shuffleAll,
                        onNavigateToSongs = { currentTab = NavigationTab.SONGS },
                        onNavigateToPlaylists = { currentTab = NavigationTab.PLAYLISTS },
                        onRemoveRecentSong = playlists::removeRecentlyPlayed,
                        onClearRecentSongs = playlists::clearRecentlyPlayed
                    )
                    NavigationTab.SONGS -> SongsScreen(
                        songs = songs,
                        favoriteSongIds = favoriteSongIds,
                        currentPlayingSongId = currentSong?.id,
                        bottomPadding = bottomPadding,
                        onSongClick = { song -> library.playSong(song, songs) },
                        onShuffleAll = library::shuffleAll,
                        onToggleFavorite = toggleFavorite,
                        onAddToPlaylist = { songForPlaylistPicker = it }
                    )
                    NavigationTab.SEARCH -> SearchScreen(
                        songs = songs,
                        favoriteSongIds = favoriteSongIds,
                        currentPlayingSongId = currentSong?.id,
                        bottomPadding = bottomPadding,
                        onSongClick = library::playSong,
                        onToggleFavorite = toggleFavorite,
                        onAddToPlaylist = { songForPlaylistPicker = it }
                    )
                    NavigationTab.PLAYLISTS -> PlaylistsScreen(
                        allSongs = songs,
                        favoriteSongIds = favoriteSongIds,
                        customPlaylists = customPlaylists,
                        currentPlayingSongId = currentSong?.id,
                        bottomPadding = bottomPadding,
                        onSongClick = library::playSong,
                        onPlayPlaylist = library::playPlaylist,
                        onToggleFavorite = toggleFavorite,
                        onCreatePlaylist = { name, songIds -> playlists.createPlaylist(name, songIds) },
                        onDeletePlaylist = playlists::deletePlaylist,
                        downloadIndex = library.downloadIndex,
                        // No streaming on iOS: online tracks show as unavailable, like in Offline Mode
                        isOfflineOnlyMode = true,
                        onRemoveOnlineTrack = playlists::removeOnlineTrackFromPlaylist,
                        onAddToPlaylist = { songForPlaylistPicker = it },
                        onRemoveSongFromPlaylist = playlists::removeSongFromPlaylist
                    )
                    NavigationTab.DOWNLOADER -> Unit
                }
            }
        }

        currentSong?.let { song ->
            DraggablePlayerSheet(
                song = song,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isFavorite = song.id in favoriteSongIds,
                onTogglePlay = player::togglePlayPause,
                onSkipNext = player::skipToNext,
                onSkipPrevious = player::skipToPrevious,
                onSeekTo = player::seekTo,
                onToggleShuffle = player::toggleShuffle,
                onToggleRepeat = player::toggleRepeatMode,
                onStop = {
                    isPlayerExpanded = false
                    player.stopPlayback()
                },
                onToggleFavorite = toggleFavorite,
                bottomBarHeight = 80.dp,
                isExpanded = isPlayerExpanded,
                onExpandedChange = { isPlayerExpanded = it },
                onShowQueue = { showQueue = true }
            )
        }

        if (showQueue) {
            val queue by player.queue.collectAsState()
            val queuePosition by player.currentQueuePosition.collectAsState()
            QueueSheet(
                queue = queue,
                currentPosition = queuePosition,
                isShuffleEnabled = isShuffleEnabled,
                onPlayItem = player::skipToQueueItem,
                onRemoveItem = player::removeQueueItem,
                onMoveItem = player::moveQueueItem,
                onDismiss = { showQueue = false }
            )
        }

        songForPlaylistPicker?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = customPlaylists,
                onToggle = { playlistId -> playlists.toggleSongInPlaylist(playlistId, song.id) },
                onCreate = { name -> playlists.createPlaylist(name, listOf(song.id)) },
                onDismiss = { songForPlaylistPicker = null }
            )
        }
    }
}

@Composable
private fun EmptyLibrary(hint: String) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text("No music yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            hint,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

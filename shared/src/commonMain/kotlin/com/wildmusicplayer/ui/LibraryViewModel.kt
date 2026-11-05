package com.wildmusicplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.platform.MusicLibrary
import com.wildmusicplayer.platform.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The music library and what the user does with it: the song list, favorites, recently played,
 * playlists and starting playback. Shared by Android and iOS; the platform supplies the library and player.
 */
class LibraryViewModel(
    private val library: MusicLibrary,
    val player: PlayerController,
    val playlists: PlaylistRepository,
    val downloadIndex: DownloadIndex,
    val settings: AppSettings
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    /** Whether the app may read the device's music (always true where no permission is needed). */
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val favoriteSongs: StateFlow<List<Song>> =
        combine(_songs, playlists.favoriteSongIds) { songs, ids -> songs.filter { it.id in ids } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> =
        combine(_songs, playlists.recentlyPlayedIds) { songs, ids ->
            val byId = songs.associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Every song that starts goes to the front of Recently Played: tapped, skipped to, or next in line.
        // Only real library songs (id > 0); online streams have no ID to remember.
        viewModelScope.launch {
            player.currentSong.map { it?.id }.distinctUntilChanged().collect { id ->
                if (id != null && id > 0) playlists.addRecentlyPlayed(id)
            }
        }
    }

    /** Permission granted or denied; a grant loads the library. */
    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) reload()
    }

    /** Reloads the songs (e.g. after a filter changed), then moves downloaded playlist tracks to local songs. */
    fun reload() {
        if (!_hasPermission.value) return
        viewModelScope.launch { loadSongs() }
    }

    private suspend fun loadSongs() {
        _songs.value = library.loadSongs(settings.filterShortAudio.value, settings.filterRecordings.value)
        if (_songs.value.isNotEmpty()) {
            playlists.autoMigrateDownloadedTracks(_songs.value.map { it.id }.toSet(), ::downloadedIdFor)
        }
    }

    /** The ID of the file downloaded from exactly this online track, if any. */
    fun downloadedIdFor(track: OnlineTrack): Long? =
        downloadIndex.downloadedIdFor(track.webUrl.ifBlank { track.id })

    /** A background download finished: pick up the new file and stop streaming queued copies of it. */
    fun onDownloadsCompleted() {
        if (!_hasPermission.value) return
        viewModelScope.launch {
            delay(1200) // Allow the media scanner to finish indexing the newly written file
            loadSongs()
            player.useDownloadedCopies(downloadIndex)
        }
    }

    fun playSong(song: Song, playlist: List<Song>) {
        player.playSong(song, playlist)
    }

    fun shuffleAll() {
        val shuffled = _songs.value.shuffled()
        if (shuffled.isEmpty()) return
        player.playSong(shuffled.first(), shuffled)
    }

    fun playPlaylist(list: List<Song>, shuffle: Boolean) {
        if (list.isEmpty()) return
        val target = if (shuffle) list.shuffled() else list
        player.playQueue(target, 0)
    }

    /** Toggles a favorite; returns false for online streams, which can only be liked once downloaded. */
    fun toggleFavorite(songId: Long): Boolean {
        if (songId <= 0) return false
        playlists.toggleFavorite(songId)
        return true
    }

    /** A song was deleted from the device: skip it if playing and forget it everywhere. */
    fun onSongDeleted(song: Song) {
        if (player.currentSong.value?.id == song.id) player.skipToNext()
        playlists.removeSongEverywhere(song.id)
        downloadIndex.forgetMediaStoreId(song.id)
        _songs.value = _songs.value.filter { it.id != song.id }
    }

    override fun onCleared() {
        player.release()
    }
}

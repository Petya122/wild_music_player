package com.wildmusicplayer.data

import com.wildmusicplayer.platform.KeyValueStore
import com.wildmusicplayer.service.storageJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val webUrl: String = ""
)

data class Playlist(
    val id: String = Uuid.random().toString(),
    val name: String,
    val songIds: List<Long> = emptyList(),
    val onlineTracks: List<OnlineTrack> = emptyList(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Manages persistence for Favorites, Recently Played tracks, and Custom User Playlists
 * backed by a [KeyValueStore] (SharedPreferences "wmp_playlists_prefs" on Android) with reactive StateFlows.
 */
class PlaylistRepository(private val prefs: KeyValueStore) {

    private val _favoriteSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSongIds: StateFlow<Set<Long>> = _favoriteSongIds.asStateFlow()

    private val _recentlyPlayedIds = MutableStateFlow<List<Long>>(emptyList())
    val recentlyPlayedIds: StateFlow<List<Long>> = _recentlyPlayedIds.asStateFlow()

    private val _customPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val customPlaylists: StateFlow<List<Playlist>> = _customPlaylists.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _favoriteSongIds.value = prefs.getStringSet(KEY_FAVORITES).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

        _recentlyPlayedIds.value = try {
            storageJson.decodeFromString(idListSerializer, prefs.getString(KEY_RECENTLY_PLAYED) ?: "[]")
        } catch (_: Exception) {
            emptyList()
        }

        _customPlaylists.value = try {
            storageJson.decodeFromString(playlistListSerializer, prefs.getString(KEY_CUSTOM_PLAYLISTS) ?: "[]")
                .map { it.toPlaylist() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavorite(songId: Long): Boolean {
        return _favoriteSongIds.value.contains(songId)
    }

    fun toggleFavorite(songId: Long): Boolean {
        val current = _favoriteSongIds.value.toMutableSet()
        val isFav = if (current.contains(songId)) {
            current.remove(songId)
            false
        } else {
            current.add(songId)
            true
        }
        _favoriteSongIds.value = current
        prefs.putStringSet(KEY_FAVORITES, current.map { it.toString() }.toSet())
        return isFav
    }

    fun addRecentlyPlayed(songId: Long) {
        val current = _recentlyPlayedIds.value.toMutableList()
        current.remove(songId)
        current.add(0, songId)
        val trimmed = current.take(20)
        _recentlyPlayedIds.value = trimmed

        prefs.putString(KEY_RECENTLY_PLAYED, storageJson.encodeToString(idListSerializer, trimmed))
    }

    fun removeRecentlyPlayed(songId: Long) {
        val current = _recentlyPlayedIds.value.toMutableList()
        current.remove(songId)
        _recentlyPlayedIds.value = current

        prefs.putString(KEY_RECENTLY_PLAYED, storageJson.encodeToString(idListSerializer, current))
    }

    /** A song was deleted from the phone: drop it from favorites, recently played and every playlist. */
    fun removeSongEverywhere(songId: Long) {
        if (songId in _favoriteSongIds.value) {
            val favorites = _favoriteSongIds.value - songId
            _favoriteSongIds.value = favorites
            prefs.putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet())
        }
        if (songId in _recentlyPlayedIds.value) {
            removeRecentlyPlayed(songId)
        }
        if (_customPlaylists.value.any { songId in it.songIds }) {
            val updated = _customPlaylists.value.map { it.copy(songIds = it.songIds - songId) }
            _customPlaylists.value = updated
            savePlaylists(updated)
        }
    }

    fun clearRecentlyPlayed() {
        _recentlyPlayedIds.value = emptyList()
        prefs.remove(KEY_RECENTLY_PLAYED)
    }

    fun createPlaylist(
        name: String,
        songIds: List<Long> = emptyList(),
        onlineTracks: List<OnlineTrack> = emptyList()
    ): Playlist {
        val newPlaylist = Playlist(name = name, songIds = songIds, onlineTracks = onlineTracks)
        val updated = _customPlaylists.value + newPlaylist
        _customPlaylists.value = updated
        savePlaylists(updated)
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _customPlaylists.value.filter { it.id != playlistId }
        _customPlaylists.value = updated
        savePlaylists(updated)
    }

    fun toggleSongInPlaylist(playlistId: String, songId: Long): Boolean {
        val updated = _customPlaylists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val songs = playlist.songIds.toMutableList()
                val added = if (songs.contains(songId)) {
                    songs.remove(songId)
                    false
                } else {
                    songs.add(songId)
                    true
                }
                playlist.copy(songIds = songs)
            } else playlist
        }
        _customPlaylists.value = updated
        savePlaylists(updated)
        return isSongInPlaylist(playlistId, songId)
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val updated = _customPlaylists.value.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(songIds = playlist.songIds - songId) else playlist
        }
        _customPlaylists.value = updated
        savePlaylists(updated)
    }

    fun isSongInPlaylist(playlistId: String, songId: Long): Boolean {
        return _customPlaylists.value.find { it.id == playlistId }?.songIds?.contains(songId) == true
    }

    fun isOnlineTrackInPlaylist(playlistId: String, trackId: String): Boolean {
        return _customPlaylists.value.find { it.id == playlistId }?.onlineTracks?.any { it.id == trackId } == true
    }

    fun toggleOnlineTrackInPlaylist(playlistId: String, track: OnlineTrack): Boolean {
        var added = false
        val updated = _customPlaylists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val tracks = playlist.onlineTracks.toMutableList()
                val existingIndex = tracks.indexOfFirst { it.id == track.id }
                if (existingIndex >= 0) {
                    tracks.removeAt(existingIndex)
                    added = false
                } else {
                    tracks.add(track)
                    added = true
                }
                playlist.copy(onlineTracks = tracks)
            } else playlist
        }
        _customPlaylists.value = updated
        savePlaylists(updated)
        return added
    }

    fun removeOnlineTrackFromPlaylist(playlistId: String, trackId: String) {
        val updated = _customPlaylists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val tracks = playlist.onlineTracks.filter { it.id != trackId }
                playlist.copy(onlineTracks = tracks)
            } else playlist
        }
        _customPlaylists.value = updated
        savePlaylists(updated)
    }

    /**
     * Inspects all custom playlists containing online tracks.
     * If the app downloaded that exact video ([downloadedIdFor] returns its MediaStore ID),
     * it migrates the track into [Playlist.songIds] as a native offline song
     * and removes it from [Playlist.onlineTracks]. Songs that merely share the title and
     * artist (another recording, a remix) never replace the track you picked.
     * Returns the total number of migrated tracks.
     */
    fun autoMigrateDownloadedTracks(localSongIds: Set<Long>, downloadedIdFor: (OnlineTrack) -> Long?): Int {
        if (localSongIds.isEmpty()) return 0
        var migratedCount = 0
        var anyChanged = false

        val updated = _customPlaylists.value.map { playlist ->
            if (playlist.onlineTracks.isEmpty()) {
                playlist
            } else {
                val remainingOnline = mutableListOf<OnlineTrack>()
                val newSongIds = playlist.songIds.toMutableList()

                for (online in playlist.onlineTracks) {
                    val matchId = downloadedIdFor(online)?.takeIf { it in localSongIds }

                    if (matchId != null) {
                        if (!newSongIds.contains(matchId)) {
                            newSongIds.add(matchId)
                        }
                        migratedCount++
                        anyChanged = true
                    } else {
                        remainingOnline.add(online)
                    }
                }

                if (remainingOnline.size != playlist.onlineTracks.size) {
                    playlist.copy(songIds = newSongIds, onlineTracks = remainingOnline)
                } else {
                    playlist
                }
            }
        }

        if (anyChanged) {
            _customPlaylists.value = updated
            savePlaylists(updated)
        }
        return migratedCount
    }

    private fun savePlaylists(list: List<Playlist>) {
        prefs.putString(KEY_CUSTOM_PLAYLISTS, storageJson.encodeToString(playlistListSerializer, list.map { it.toJson() }))
    }

    // The JSON layout stored since v0.5; a missing thumbnail is stored as ""
    @Serializable
    private class PlaylistJson(
        val id: String,
        val name: String,
        val createdAt: Long = 0L,
        val songIds: List<Long> = emptyList(),
        val onlineTracks: List<OnlineTrackJson> = emptyList()
    ) {
        fun toPlaylist() = Playlist(id, name, songIds, onlineTracks.map { it.toTrack() }, createdAt)
    }

    @Serializable
    private class OnlineTrackJson(
        val id: String,
        val title: String,
        val artist: String = "Unknown Artist",
        val durationSeconds: Long = 0L,
        val thumbnailUrl: String = "",
        val webUrl: String = ""
    ) {
        fun toTrack() = OnlineTrack(id, title, artist, durationSeconds, thumbnailUrl.takeIf { it.isNotBlank() }, webUrl)
    }

    private fun Playlist.toJson() = PlaylistJson(
        id, name, createdAt, songIds,
        onlineTracks.map { OnlineTrackJson(it.id, it.title, it.artist, it.durationSeconds, it.thumbnailUrl ?: "", it.webUrl) }
    )

    companion object {
        private const val KEY_FAVORITES = "key_favorite_songs"
        private const val KEY_RECENTLY_PLAYED = "key_recently_played_songs"
        private const val KEY_CUSTOM_PLAYLISTS = "key_custom_playlists"

        private val idListSerializer = ListSerializer(Long.serializer())
        private val playlistListSerializer = ListSerializer(PlaylistJson.serializer())
    }
}

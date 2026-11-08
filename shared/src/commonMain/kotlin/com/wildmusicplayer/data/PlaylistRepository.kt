package com.wildmusicplayer.data

import com.wildmusicplayer.platform.KeyValueStore
import com.wildmusicplayer.service.storageJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
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
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // Set for a playlist imported from YouTube: it can be updated from there
    val source: PlaylistSource? = null
)

/** Where an imported YouTube playlist came from, for "Update from YouTube". */
data class PlaylistSource(
    val playlistId: String,
    // Every video imported so far: one you removed here isn't added back by an update
    val knownVideoIds: Set<String> = emptySet(),
    val updatedAt: Long = 0L
) {
    val url: String get() = youTubePlaylistUrl(playlistId)
}

/** The video a track plays, so the same song is recognized whichever link form it was saved with. */
internal fun OnlineTrack.videoKey(): String = youTubeVideoId(webUrl.ifBlank { id }) ?: id

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

    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    /** How many times each song was played (counted since v0.9.2), for "Most Played". */
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()

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

        _playCounts.value = try {
            storageJson.decodeFromString(playCountSerializer, prefs.getString(KEY_PLAY_COUNTS) ?: "{}")
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** A song has been listened to (PlaybackService decides when that is). */
    fun countPlay(songId: Long) {
        val counts = _playCounts.value + (songId to (_playCounts.value[songId] ?: 0) + 1)
        _playCounts.value = counts
        prefs.putString(KEY_PLAY_COUNTS, storageJson.encodeToString(playCountSerializer, counts))
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
        if (songId in _playCounts.value) {
            val counts = _playCounts.value - songId
            _playCounts.value = counts
            prefs.putString(KEY_PLAY_COUNTS, storageJson.encodeToString(playCountSerializer, counts))
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

    /** The playlist imported from the YouTube playlist with this ID, if there is one. */
    fun playlistFromYouTube(youTubePlaylistId: String): Playlist? =
        _customPlaylists.value.firstOrNull { it.source?.playlistId == youTubePlaylistId }

    /** A YouTube playlist imported as a new playlist of streamed songs that remembers where it came from. */
    fun importYouTubePlaylist(name: String, youTubePlaylistId: String, tracks: List<OnlineTrack>, now: Long): Playlist {
        val unique = tracks.distinctBy { it.videoKey() }
        val playlist = Playlist(
            name = name.trim().ifEmpty { "YouTube playlist" },
            onlineTracks = unique,
            createdAt = now,
            source = PlaylistSource(youTubePlaylistId, unique.mapTo(mutableSetOf()) { it.videoKey() }, now)
        )
        val updated = _customPlaylists.value + playlist
        _customPlaylists.value = updated
        savePlaylists(updated)
        return playlist
    }

    /**
     * Adds the songs added to the YouTube playlist since the last update ([tracks] is the playlist as it
     * is now) to the end, and returns how many. Songs you removed here aren't added back, songs removed
     * on YouTube stay here, and your order is kept.
     */
    fun applyYouTubeUpdate(playlistId: String, tracks: List<OnlineTrack>, now: Long): Int {
        var added = 0
        updatePlaylist(playlistId) { playlist ->
            val source = playlist.source ?: return@updatePlaylist playlist
            val present = playlist.onlineTracks.mapTo(mutableSetOf()) { it.videoKey() }
            val new = tracks.distinctBy { it.videoKey() }
                .filter { it.videoKey() !in source.knownVideoIds && it.videoKey() !in present }
            added = new.size
            playlist.copy(
                onlineTracks = playlist.onlineTracks + new,
                source = source.copy(knownVideoIds = source.knownVideoIds + tracks.map { it.videoKey() }, updatedAt = now)
            )
        }
        return added
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _customPlaylists.value.filter { it.id != playlistId }
        _customPlaylists.value = updated
        savePlaylists(updated)
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updatePlaylist(playlistId) { it.copy(name = trimmed) }
    }

    /**
     * Puts a playlist's songs and online tracks in the given order. Entries the order doesn't mention
     * (added meanwhile) keep their place at the end; unknown IDs are ignored, so nothing is lost or invented.
     */
    fun reorderPlaylist(playlistId: String, songIds: List<Long>, onlineTrackIds: List<String>) {
        updatePlaylist(playlistId) { playlist ->
            val songs = songIds.filter { it in playlist.songIds }.distinct()
            val tracksById = playlist.onlineTracks.associateBy { it.id }
            val tracks = onlineTrackIds.distinct().mapNotNull { tracksById[it] }
            playlist.copy(
                songIds = songs + playlist.songIds.filter { it !in songs },
                onlineTracks = tracks + playlist.onlineTracks.filter { it !in tracks }
            )
        }
    }

    /** Undo for "Remove from this playlist": puts the song back where it was. */
    fun insertSongInPlaylist(playlistId: String, songId: Long, index: Int) {
        updatePlaylist(playlistId) { playlist ->
            if (songId in playlist.songIds) return@updatePlaylist playlist
            val songs = playlist.songIds.toMutableList()
            songs.add(index.coerceIn(0, songs.size), songId)
            playlist.copy(songIds = songs)
        }
    }

    /** Undo for removing an online track: puts it back where it was. */
    fun insertOnlineTrackInPlaylist(playlistId: String, track: OnlineTrack, index: Int) {
        updatePlaylist(playlistId) { playlist ->
            if (playlist.onlineTracks.any { it.id == track.id }) return@updatePlaylist playlist
            val tracks = playlist.onlineTracks.toMutableList()
            tracks.add(index.coerceIn(0, tracks.size), track)
            playlist.copy(onlineTracks = tracks)
        }
    }

    private fun updatePlaylist(playlistId: String, change: (Playlist) -> Playlist) {
        val updated = _customPlaylists.value.map { if (it.id == playlistId) change(it) else it }
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

    /**
     * Adds what a backup restore found; never removes anything. A playlist whose name already exists
     * gets the songs it's missing; other playlists are added. Favorites are joined, and recently played
     * songs not in the list yet go to its end.
     */
    fun mergeRestored(playlists: List<Playlist>, favoriteIds: Set<Long>, recentIds: List<Long>) {
        val updated = _customPlaylists.value.toMutableList()
        for (restored in playlists) {
            val index = updated.indexOfFirst { it.name.equals(restored.name, ignoreCase = true) }
            if (index < 0) {
                updated += restored
                continue
            }
            val existing = updated[index]
            val trackIds = existing.onlineTracks.map { it.id }.toSet()
            updated[index] = existing.copy(
                songIds = existing.songIds + restored.songIds.filter { it !in existing.songIds },
                onlineTracks = existing.onlineTracks + restored.onlineTracks.filter { it.id !in trackIds },
                source = existing.source ?: restored.source
            )
        }
        _customPlaylists.value = updated
        savePlaylists(updated)

        val favorites = _favoriteSongIds.value + favoriteIds
        _favoriteSongIds.value = favorites
        prefs.putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet())

        val recent = (_recentlyPlayedIds.value + recentIds.filter { it !in _recentlyPlayedIds.value }).take(20)
        _recentlyPlayedIds.value = recent
        prefs.putString(KEY_RECENTLY_PLAYED, storageJson.encodeToString(idListSerializer, recent))
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
        val onlineTracks: List<OnlineTrackJson> = emptyList(),
        // Since v0.9.9: the YouTube playlist it was imported from
        val source: PlaylistSourceJson? = null
    ) {
        fun toPlaylist() = Playlist(id, name, songIds, onlineTracks.map { it.toTrack() }, createdAt, source?.toSource())
    }

    @Serializable
    private class PlaylistSourceJson(
        val playlistId: String,
        val knownVideoIds: List<String> = emptyList(),
        val updatedAt: Long = 0L
    ) {
        fun toSource() = PlaylistSource(playlistId, knownVideoIds.toSet(), updatedAt)
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
        onlineTracks.map { OnlineTrackJson(it.id, it.title, it.artist, it.durationSeconds, it.thumbnailUrl ?: "", it.webUrl) },
        source?.let { PlaylistSourceJson(it.playlistId, it.knownVideoIds.toList(), it.updatedAt) }
    )

    companion object {
        private const val KEY_FAVORITES = "key_favorite_songs"
        private const val KEY_RECENTLY_PLAYED = "key_recently_played_songs"
        private const val KEY_CUSTOM_PLAYLISTS = "key_custom_playlists"
        private const val KEY_PLAY_COUNTS = "key_play_counts"

        private val idListSerializer = ListSerializer(Long.serializer())
        private val playlistListSerializer = ListSerializer(PlaylistJson.serializer())
        private val playCountSerializer = MapSerializer(Long.serializer(), Int.serializer())
    }
}

package com.wildmusicplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.wildmusicplayer.model.Song

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val webUrl: String = ""
)

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList(),
    val onlineTracks: List<OnlineTrack> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Manages persistence for Favorites, Recently Played tracks, and Custom User Playlists
 * backed by SharedPreferences with reactive StateFlows.
 */
class PlaylistRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wmp_playlists_prefs", Context.MODE_PRIVATE)

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
        // Load Favorites
        val rawFavs = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        _favoriteSongIds.value = rawFavs.mapNotNull { it.toLongOrNull() }.toSet()

        // Load Recently Played
        val rawRecent = prefs.getString(KEY_RECENTLY_PLAYED, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(rawRecent)
            val list = mutableListOf<Long>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getLong(i))
            }
            _recentlyPlayedIds.value = list
        } catch (_: Exception) {
            _recentlyPlayedIds.value = emptyList()
        }

        // Load Custom Playlists
        val rawPlaylists = prefs.getString(KEY_CUSTOM_PLAYLISTS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(rawPlaylists)
            val list = mutableListOf<Playlist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val songIdsArray = obj.optJSONArray("songIds") ?: JSONArray()
                val songIds = mutableListOf<Long>()
                for (j in 0 until songIdsArray.length()) {
                    songIds.add(songIdsArray.getLong(j))
                }

                val onlineTracksArray = obj.optJSONArray("onlineTracks") ?: JSONArray()
                val onlineTracks = mutableListOf<OnlineTrack>()
                for (k in 0 until onlineTracksArray.length()) {
                    val trackObj = onlineTracksArray.optJSONObject(k)
                    if (trackObj != null) {
                        onlineTracks.add(
                            OnlineTrack(
                                id = trackObj.getString("id"),
                                title = trackObj.getString("title"),
                                artist = trackObj.optString("artist", "Unknown Artist"),
                                durationSeconds = trackObj.optLong("durationSeconds", 0L),
                                thumbnailUrl = trackObj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                webUrl = trackObj.optString("webUrl", "")
                            )
                        )
                    }
                }

                list.add(
                    Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        songIds = songIds,
                        onlineTracks = onlineTracks,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            _customPlaylists.value = list
        } catch (_: Exception) {
            _customPlaylists.value = emptyList()
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
        prefs.edit().putStringSet(KEY_FAVORITES, current.map { it.toString() }.toSet()).apply()
        return isFav
    }

    fun addRecentlyPlayed(songId: Long) {
        val current = _recentlyPlayedIds.value.toMutableList()
        current.remove(songId)
        current.add(0, songId)
        val trimmed = current.take(20)
        _recentlyPlayedIds.value = trimmed

        val jsonArray = JSONArray()
        trimmed.forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_RECENTLY_PLAYED, jsonArray.toString()).apply()
    }

    fun removeRecentlyPlayed(songId: Long) {
        val current = _recentlyPlayedIds.value.toMutableList()
        current.remove(songId)
        _recentlyPlayedIds.value = current

        val jsonArray = JSONArray()
        current.forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_RECENTLY_PLAYED, jsonArray.toString()).apply()
    }

    fun clearRecentlyPlayed() {
        _recentlyPlayedIds.value = emptyList()
        prefs.edit().remove(KEY_RECENTLY_PLAYED).apply()
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
     * If an online track's title matches a downloaded local song,
     * it migrates the track into [Playlist.songIds] as a native offline song
     * and removes it from [Playlist.onlineTracks].
     * Returns the total number of migrated tracks.
     */
    fun autoMigrateDownloadedTracks(localSongs: List<Song>): Int {
        if (localSongs.isEmpty()) return 0
        var migratedCount = 0
        var anyChanged = false

        val updated = _customPlaylists.value.map { playlist ->
            if (playlist.onlineTracks.isEmpty()) {
                playlist
            } else {
                val remainingOnline = mutableListOf<OnlineTrack>()
                val newSongIds = playlist.songIds.toMutableList()

                for (online in playlist.onlineTracks) {
                    val cleanOnline = online.title.trim().lowercase()
                    val match = localSongs.find { local ->
                        val cleanLocal = local.title.trim().lowercase()
                        cleanLocal == cleanOnline ||
                        (cleanLocal.length >= 5 && cleanOnline.length >= 5 && 
                            (cleanLocal.contains(cleanOnline) || cleanOnline.contains(cleanLocal)))
                    }

                    if (match != null) {
                        if (!newSongIds.contains(match.id)) {
                            newSongIds.add(match.id)
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
        val jsonArray = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("createdAt", p.createdAt)
                val songsArr = JSONArray()
                p.songIds.forEach { songsArr.put(it) }
                put("songIds", songsArr)

                val onlineArr = JSONArray()
                p.onlineTracks.forEach { ot ->
                    val otObj = JSONObject().apply {
                        put("id", ot.id)
                        put("title", ot.title)
                        put("artist", ot.artist)
                        put("durationSeconds", ot.durationSeconds)
                        put("thumbnailUrl", ot.thumbnailUrl ?: "")
                        put("webUrl", ot.webUrl)
                    }
                    onlineArr.put(otObj)
                }
                put("onlineTracks", onlineArr)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_PLAYLISTS, jsonArray.toString()).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "key_favorite_songs"
        private const val KEY_RECENTLY_PLAYED = "key_recently_played_songs"
        private const val KEY_CUSTOM_PLAYLISTS = "key_custom_playlists"
    }
}

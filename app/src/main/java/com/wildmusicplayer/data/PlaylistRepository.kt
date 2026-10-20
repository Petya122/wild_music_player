package com.wildmusicplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList(),
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
                list.add(
                    Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        songIds = songIds,
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

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()): Playlist {
        val newPlaylist = Playlist(name = name, songIds = songIds)
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

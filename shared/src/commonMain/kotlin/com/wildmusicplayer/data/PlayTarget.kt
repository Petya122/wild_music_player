package com.wildmusicplayer.data

import com.wildmusicplayer.model.Song

/**
 * Something that can be played in one tap from outside the app: an app icon shortcut or the playlist
 * widget. Saved as text ([key]) so it survives restarts.
 */
sealed class PlayTarget(val key: String) {
    data object ShuffleAll : PlayTarget("shuffle_all")
    data object Favorites : PlayTarget("favorites")
    data object MostPlayed : PlayTarget("most_played")
    data object RecentlyAdded : PlayTarget("recently_added")
    data class CustomPlaylist(val id: String) : PlayTarget("playlist:$id")

    /** The name shown on the widget or shortcut. */
    fun label(playlists: List<Playlist>): String = when (this) {
        ShuffleAll -> "Shuffle All"
        Favorites -> "Favorites"
        MostPlayed -> "Most Played"
        RecentlyAdded -> "Recently Added"
        is CustomPlaylist -> playlists.firstOrNull { it.id == id }?.name ?: "Playlist"
    }

    /**
     * The songs to play, in order. [AUTO_PLAYLIST_SIZE] songs for Most Played and Recently Added, like the
     * automatic playlists in the app. Only songs on the phone: a playlist's online tracks are left out.
     */
    fun songs(library: List<Song>, favoriteIds: Set<Long>, playCounts: Map<Long, Int>, playlists: List<Playlist>): List<Song> =
        when (this) {
            ShuffleAll -> library
            Favorites -> library.filter { it.id in favoriteIds }
            MostPlayed -> library.filter { (playCounts[it.id] ?: 0) > 0 }
                .sortedByDescending { playCounts[it.id] ?: 0 }
                .take(AUTO_PLAYLIST_SIZE)
            // MediaStore numbers files in the order they were added, so the highest IDs are the newest
            RecentlyAdded -> library.sortedByDescending { it.id }.take(AUTO_PLAYLIST_SIZE)
            is CustomPlaylist -> {
                val byId = library.associateBy { it.id }
                playlists.firstOrNull { it.id == id }?.songIds?.mapNotNull { byId[it] }.orEmpty()
            }
        }

    companion object {
        const val AUTO_PLAYLIST_SIZE = 50

        fun fromKey(key: String?): PlayTarget? = when {
            key == null -> null
            key.startsWith("playlist:") -> CustomPlaylist(key.removePrefix("playlist:")).takeIf { it.id.isNotBlank() }
            else -> listOf(ShuffleAll, Favorites, MostPlayed, RecentlyAdded).firstOrNull { it.key == key }
        }
    }
}

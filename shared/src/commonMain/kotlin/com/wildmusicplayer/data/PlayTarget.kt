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
     * automatic playlists in the app. A custom playlist plays like in the app ([Playlist.playQueue]): its
     * online tracks too, from the downloaded copy ([downloadedIdFor]) or streamed unless [offline].
     */
    fun songs(
        library: List<Song>,
        favoriteIds: Set<Long>,
        playCounts: Map<Long, Int>,
        playlists: List<Playlist>,
        downloadedIdFor: (webUrl: String) -> Long? = { null },
        offline: Boolean = true
    ): List<Song> =
        when (this) {
            ShuffleAll -> library
            Favorites -> library.filter { it.id in favoriteIds }
            MostPlayed -> library.filter { (playCounts[it.id] ?: 0) > 0 }
                .sortedByDescending { playCounts[it.id] ?: 0 }
                .take(AUTO_PLAYLIST_SIZE)
            // MediaStore numbers files in the order they were added, so the highest IDs are the newest
            RecentlyAdded -> library.sortedByDescending { it.id }.take(AUTO_PLAYLIST_SIZE)
            is CustomPlaylist -> playlists.firstOrNull { it.id == id }?.playQueue(library, downloadedIdFor, offline).orEmpty()
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

/**
 * A custom playlist as one queue: its songs on the phone, then its online tracks, each from the downloaded
 * copy when there is one ([downloadedIdFor] gives its MediaStore ID), otherwise streamed. Online tracks
 * that aren't downloaded are left out when [offline]. Songs no longer on the phone are skipped.
 */
fun Playlist.playQueue(library: List<Song>, downloadedIdFor: (webUrl: String) -> Long?, offline: Boolean): List<Song> {
    val byId = library.associateBy { it.id }
    val local = songIds.mapNotNull { byId[it] }
    val online = onlineTracks.mapNotNull { track ->
        val copy = downloadedIdFor(track.webUrl.ifBlank { track.id })?.let { byId[it] }
        when {
            copy != null -> copy
            offline -> null
            else -> Song.fromOnline(
                title = track.title,
                artist = track.artist,
                webUrl = track.webUrl,
                durationSeconds = track.durationSeconds,
                thumbnailUrl = track.thumbnailUrl
            )
        }
    }
    return (local + online).distinctBy { it.mediaId }
}

package com.wildmusicplayer.data

import com.wildmusicplayer.model.Song

/** What a [BrowseNode] is: a folder to open, or something to play. */
enum class BrowseKind { TAB, LIST, ALBUM, ARTIST, SHUFFLE, SONG, CONTINUE }

/**
 * One row in the car's music browser (Android Auto, or a car radio that can browse over Bluetooth).
 * [song] is set for song rows. Folders open into more rows; the rest play when tapped.
 */
data class BrowseNode(
    val id: String,
    val kind: BrowseKind,
    val title: String,
    val subtitle: String? = null,
    val artUri: String? = null,
    val song: Song? = null
) {
    val isBrowsable: Boolean get() = kind == BrowseKind.TAB || kind == BrowseKind.LIST || kind == BrowseKind.ALBUM || kind == BrowseKind.ARTIST
}

/** What tapping a playable row starts: [songs] from [startIndex], already shuffled when it was a Shuffle row. */
data class BrowsePlay(val songs: List<Song>, val startIndex: Int = 0, val shuffled: Boolean = false)

/**
 * The music browser for cars: four tabs (Home, Playlists, Albums, Artists), lists with a "Shuffle" row on top,
 * then their songs. Built fresh from what the app knows right now; IDs are text the car hands back when the
 * user taps, so [play] can start the same list again.
 *
 * IDs: tabs "tab:home"…; lists "list|<key>"; songs "song|<key>|<index>|<mediaId>"; shuffle rows
 * "shuffle|<key>"; and "continue" for the last queue. List keys: "recent", "favorites", PlayTarget keys
 * ("most_played", "recently_added", "playlist:<id>"), "album:<key>" and "artist:<key>".
 */
class BrowseCatalog(
    private val library: List<Song>,
    private val favoriteIds: Set<Long>,
    private val playCounts: Map<Long, Int>,
    private val playlists: List<Playlist>,
    private val recentIds: List<Long>,
    private val downloadedIdFor: (webUrl: String) -> Long? = { null },
    private val offline: Boolean = true,
    // The queue the app would pick up again (title for the Continue row), null when there's none
    private val continueTitle: String? = null
) {
    private val albums by lazy { albumsOf(library) }
    private val artists by lazy { artistsOf(library) }
    private val byId by lazy { library.associateBy { it.id } }

    /** The rows inside [parentId], or null when there's no such folder. */
    fun children(parentId: String): List<BrowseNode>? = when (parentId) {
        ROOT -> listOf(
            BrowseNode(TAB_HOME, BrowseKind.TAB, "Home"),
            BrowseNode(TAB_PLAYLISTS, BrowseKind.TAB, "Playlists"),
            BrowseNode(TAB_ALBUMS, BrowseKind.TAB, "Albums"),
            BrowseNode(TAB_ARTISTS, BrowseKind.TAB, "Artists")
        )
        TAB_HOME -> buildList {
            continueTitle?.let { add(BrowseNode(CONTINUE, BrowseKind.CONTINUE, "Continue", it)) }
            add(BrowseNode(shuffleId(KEY_ALL), BrowseKind.SHUFFLE, "Shuffle all", "${library.size} songs"))
            listNode(KEY_RECENT)?.let(::add)
            listNode(KEY_FAVORITES)?.let(::add)
        }
        TAB_PLAYLISTS -> buildList {
            listNode(PlayTarget.MostPlayed.key)?.let(::add)
            listNode(PlayTarget.RecentlyAdded.key)?.let(::add)
            playlists.forEach { playlist -> listNode(PlayTarget.CustomPlaylist(playlist.id).key)?.let(::add) }
        }
        TAB_ALBUMS -> albums.map { album ->
            BrowseNode(listId(ALBUM_PREFIX + album.key), BrowseKind.ALBUM, album.title, album.subtitle, album.artUri)
        }
        TAB_ARTISTS -> artists.map { artist ->
            BrowseNode(listId(ARTIST_PREFIX + artist.key), BrowseKind.ARTIST, artist.title, artist.subtitle, artist.artUri)
        }
        else -> listKeyOf(parentId)?.let { key ->
            val songs = songsOf(key) ?: return null
            if (songs.isEmpty()) return emptyList()
            listOf(BrowseNode(shuffleId(key), BrowseKind.SHUFFLE, "Shuffle", "${songs.size} songs")) +
                songs.mapIndexed { index, song -> songNode(key, index, song) }
        }
    }

    /** One row by its ID (the car asks when it shows a row it remembered), or null when it's gone. */
    fun item(id: String): BrowseNode? {
        if (id == ROOT) return BrowseNode(ROOT, BrowseKind.TAB, "Wild Music")
        children(ROOT)!!.firstOrNull { it.id == id }?.let { return it }
        if (id == CONTINUE) return continueTitle?.let { BrowseNode(CONTINUE, BrowseKind.CONTINUE, "Continue", it) }
        listKeyOf(id)?.let { key -> return listNode(key) ?: albumOrArtistNode(key) }
        shuffleKeyOf(id)?.let { key ->
            val count = (if (key == KEY_ALL) library else songsOf(key))?.size ?: return null
            return BrowseNode(id, BrowseKind.SHUFFLE, if (key == KEY_ALL) "Shuffle all" else "Shuffle", "$count songs")
        }
        val (key, index) = songRef(id) ?: return null
        val song = songsOf(key)?.getOrNull(index) ?: return null
        return songNode(key, index, song)
    }

    /**
     * What tapping [id] plays: a song (its whole list, from that song), a Shuffle row (the list shuffled), or
     * null for Continue and unknown IDs (the player handles those).
     */
    fun play(id: String): BrowsePlay? {
        shuffleKeyOf(id)?.let { key ->
            val songs = (if (key == KEY_ALL) library else songsOf(key)).orEmpty()
            return if (songs.isEmpty()) null else BrowsePlay(songs.shuffled(), 0, shuffled = true)
        }
        val (key, index, mediaId) = songRef(id) ?: return null
        val songs = songsOf(key).orEmpty()
        // The list may have changed since the car showed it: find the song again by its ID
        val start = index.takeIf { songs.getOrNull(it)?.mediaId == mediaId }
            ?: songs.indexOfFirst { it.mediaId == mediaId }.takeIf { it >= 0 }
            ?: return null
        return BrowsePlay(songs, start)
    }

    /**
     * Voice and typed search ("play Basic Element on Wild Music"): an artist, album or playlist whose name
     * matches plays whole; otherwise the matching songs. An empty query means "just play something":
     * null, so the player continues the last queue.
     */
    fun playFromSearch(query: String): BrowsePlay? {
        val wanted = normalizeForSearch(query)
        if (wanted.isBlank()) return null
        playlists.firstOrNull { normalizeForSearch(it.name) == wanted }?.let { playlist ->
            val songs = songsOf(PlayTarget.CustomPlaylist(playlist.id).key).orEmpty()
            if (songs.isNotEmpty()) return BrowsePlay(songs)
        }
        artists.firstOrNull { normalizeForSearch(it.title) == wanted }?.let { return BrowsePlay(it.songs) }
        albums.firstOrNull { normalizeForSearch(it.title) == wanted }?.let { return BrowsePlay(it.songs) }
        val songs = searchSongs(query)
        return if (songs.isEmpty()) null else BrowsePlay(songs)
    }

    /** Song rows matching [query] for the car's search screen, best first. */
    fun search(query: String): List<BrowseNode> =
        searchSongs(query).mapIndexed { index, song -> songNode(searchKey(query), index, song) }

    private fun searchSongs(query: String): List<Song> {
        val words = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return library
            .map { song -> song to score(song, words) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(MAX_SEARCH_RESULTS)
    }

    // Every word must appear in the title, artist or album; title hits count most
    private fun score(song: Song, words: List<String>): Int {
        val title = normalizeForSearch(song.title)
        val artist = normalizeForSearch(song.artist)
        val album = normalizeForSearch(song.album)
        var total = 0
        for (word in words) {
            total += when {
                title.contains(word) -> 3
                artist.contains(word) -> 2
                album.contains(word) -> 1
                else -> return 0
            }
        }
        return total
    }

    /** The songs of list [key] in their order, or null when there's no such list. */
    fun songsOf(key: String): List<Song>? = when {
        key == KEY_RECENT -> recentIds.mapNotNull { byId[it] }
        key == KEY_FAVORITES -> PlayTarget.Favorites.songs(library, favoriteIds, playCounts, playlists)
        key.startsWith(ALBUM_PREFIX) -> albums.firstOrNull { it.key == key.removePrefix(ALBUM_PREFIX) }?.songs
        key.startsWith(ARTIST_PREFIX) -> artists.firstOrNull { it.key == key.removePrefix(ARTIST_PREFIX) }?.songs
        key.startsWith(SEARCH_PREFIX) -> searchSongs(key.removePrefix(SEARCH_PREFIX))
        else -> {
            val target = PlayTarget.fromKey(key)
            when {
                target == null || target == PlayTarget.ShuffleAll -> null
                target is PlayTarget.CustomPlaylist && playlists.none { it.id == target.id } -> null
                else -> target.songs(library, favoriteIds, playCounts, playlists, downloadedIdFor, offline)
            }
        }
    }

    private fun listNode(key: String): BrowseNode? {
        val songs = songsOf(key) ?: return null
        val title = when (key) {
            KEY_RECENT -> "Recently Played"
            KEY_FAVORITES -> "Favorites"
            else -> PlayTarget.fromKey(key)?.label(playlists) ?: return null
        }
        val count = if (songs.size == 1) "1 song" else "${songs.size} songs"
        return BrowseNode(listId(key), BrowseKind.LIST, title, count, songs.firstNotNullOfOrNull { it.albumArtUri })
    }

    private fun albumOrArtistNode(key: String): BrowseNode? = when {
        key.startsWith(ALBUM_PREFIX) -> albums.firstOrNull { it.key == key.removePrefix(ALBUM_PREFIX) }
            ?.let { BrowseNode(listId(key), BrowseKind.ALBUM, it.title, it.subtitle, it.artUri) }
        key.startsWith(ARTIST_PREFIX) -> artists.firstOrNull { it.key == key.removePrefix(ARTIST_PREFIX) }
            ?.let { BrowseNode(listId(key), BrowseKind.ARTIST, it.title, it.subtitle, it.artUri) }
        else -> null
    }

    private fun songNode(key: String, index: Int, song: Song) = BrowseNode(
        id = "song|${escape(key)}|$index|${song.mediaId}",
        kind = BrowseKind.SONG,
        title = song.title,
        subtitle = song.artist,
        artUri = song.albumArtUri,
        song = song
    )

    companion object {
        const val ROOT = "root"
        const val TAB_HOME = "tab:home"
        const val TAB_PLAYLISTS = "tab:playlists"
        const val TAB_ALBUMS = "tab:albums"
        const val TAB_ARTISTS = "tab:artists"
        const val CONTINUE = "continue"

        const val KEY_RECENT = "recent"
        const val KEY_FAVORITES = "favorites"
        private const val KEY_ALL = "all"
        private const val ALBUM_PREFIX = "album:"
        private const val ARTIST_PREFIX = "artist:"
        private const val SEARCH_PREFIX = "search:"
        private const val MAX_SEARCH_RESULTS = 50

        fun listId(key: String) = "list|" + escape(key)
        fun shuffleId(key: String) = "shuffle|" + escape(key)
        private fun searchKey(query: String) = SEARCH_PREFIX + query

        private fun listKeyOf(id: String): String? = id.takeIf { it.startsWith("list|") }?.let { unescape(it.removePrefix("list|")) }
        private fun shuffleKeyOf(id: String): String? = id.takeIf { it.startsWith("shuffle|") }?.let { unescape(it.removePrefix("shuffle|")) }

        /** "song|<key>|<index>|<mediaId>" → (key, index, mediaId); the media ID may itself contain "|". */
        private fun songRef(id: String): Triple<String, Int, String>? {
            if (!id.startsWith("song|")) return null
            val parts = id.removePrefix("song|").split('|', limit = 3)
            if (parts.size < 3) return null
            val index = parts[1].toIntOrNull() ?: return null
            return Triple(unescape(parts[0]), index, parts[2])
        }

        // List keys go inside IDs separated by "|": keep those characters out
        private fun escape(key: String) = key.replace("%", "%25").replace("|", "%7C")
        private fun unescape(key: String) = key.replace("%7C", "|").replace("%25", "%")
    }
}

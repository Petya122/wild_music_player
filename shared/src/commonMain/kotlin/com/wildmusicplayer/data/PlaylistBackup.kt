package com.wildmusicplayer.data

import com.wildmusicplayer.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** What a restore did: playlists created or added to, and how many of their songs were found. */
data class RestoreSummary(val playlists: Int, val songsFound: Int, val songsTotal: Int, val favorites: Int)

/**
 * Playlist backup and restore, plus M3U8 export for other players.
 *
 * Phone song IDs change after a reinstall or on another phone, so a backup describes each song by its
 * file path, title, artist and length, and a restore looks for it again: the same path first, then the
 * same title and artist (with about the same length), then the same title and length.
 */
object PlaylistBackup {

    private const val FORMAT = "wild-music-player-backup"
    private const val LENGTH_TOLERANCE_MS = 2_000L

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun create(repository: PlaylistRepository, library: List<Song>, now: Long): String {
        val byId = library.associateBy { it.id }
        fun describe(ids: Iterable<Long>) = ids.mapNotNull { byId[it]?.toBackup() }

        val backup = BackupJson(
            format = FORMAT,
            createdAt = now,
            playlists = repository.customPlaylists.value.map { playlist ->
                BackupPlaylistJson(
                    name = playlist.name,
                    createdAt = playlist.createdAt,
                    songs = describe(playlist.songIds),
                    onlineTracks = playlist.onlineTracks.map {
                        BackupTrackJson(it.id, it.title, it.artist, it.durationSeconds, it.thumbnailUrl, it.webUrl)
                    }
                )
            },
            favorites = describe(repository.favoriteSongIds.value),
            recentlyPlayed = describe(repository.recentlyPlayedIds.value)
        )
        return json.encodeToString(BackupJson.serializer(), backup)
    }

    /** Adds the backup's playlists and favorites to [repository]; null when [text] isn't a backup file. */
    fun restore(text: String, repository: PlaylistRepository, library: List<Song>): RestoreSummary? {
        val backup = runCatching { json.decodeFromString(BackupJson.serializer(), text) }.getOrNull()
            ?.takeIf { it.format == FORMAT }
            ?: return null
        val matcher = SongMatcher(library)

        var found = 0
        var total = 0
        val playlists = backup.playlists.map { saved ->
            val ids = saved.songs.mapNotNull { matcher.find(it) }.distinct()
            found += ids.size
            total += saved.songs.size
            Playlist(
                name = saved.name,
                songIds = ids,
                onlineTracks = saved.onlineTracks.map {
                    OnlineTrack(it.id, it.title, it.artist, it.durationSeconds, it.thumbnailUrl, it.webUrl)
                },
                createdAt = saved.createdAt
            )
        }
        val favorites = backup.favorites.mapNotNull { matcher.find(it) }.toSet()
        val recent = backup.recentlyPlayed.mapNotNull { matcher.find(it) }.distinct()

        repository.mergeRestored(playlists, favorites, recent)
        return RestoreSummary(playlists.size, found, total, favorites.size)
    }

    /** An M3U8 playlist: file paths for songs on the phone, the YouTube address for online tracks. */
    fun toM3u8(name: String, songs: List<Song>, onlineTracks: List<OnlineTrack>): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(name).append('\n')
        for (song in songs) {
            append("#EXTINF:").append(song.duration / 1000).append(',').append(song.artist).append(" - ").append(song.title).append('\n')
            append(song.filePath ?: song.contentUri).append('\n')
        }
        for (track in onlineTracks) {
            append("#EXTINF:").append(track.durationSeconds).append(',').append(track.artist).append(" - ").append(track.title).append('\n')
            append(track.webUrl).append('\n')
        }
    }

    private fun Song.toBackup() = BackupSongJson(title, artist, album, duration, filePath)

    private class SongMatcher(library: List<Song>) {
        private val local = library.filter { !it.isOnline }
        private val byPath = local.filter { it.filePath != null }.associateBy { it.filePath }
        private val byTitle = local.groupBy { normalizeForSearch(it.title) }

        fun find(saved: BackupSongJson): Long? {
            saved.path?.let { path -> byPath[path]?.let { return it.id } }
            val artist = normalizeForSearch(saved.artist)
            val sameTitle = byTitle[normalizeForSearch(saved.title)].orEmpty()
            val sameArtist = sameTitle.filter { normalizeForSearch(it.artist) == artist }
            return (sameArtist.firstOrNull { it.lengthMatches(saved) }
                ?: sameArtist.firstOrNull()
                ?: sameTitle.firstOrNull { it.lengthMatches(saved) })?.id
        }

        private fun Song.lengthMatches(saved: BackupSongJson) =
            saved.durationMs > 0 && abs(duration - saved.durationMs) <= LENGTH_TOLERANCE_MS
    }
}

@Serializable
internal class BackupJson(
    val format: String = "",
    val version: Int = 1,
    val createdAt: Long = 0L,
    val playlists: List<BackupPlaylistJson> = emptyList(),
    val favorites: List<BackupSongJson> = emptyList(),
    val recentlyPlayed: List<BackupSongJson> = emptyList()
)

@Serializable
internal class BackupPlaylistJson(
    val name: String,
    val createdAt: Long = 0L,
    val songs: List<BackupSongJson> = emptyList(),
    val onlineTracks: List<BackupTrackJson> = emptyList()
)

@Serializable
internal class BackupSongJson(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val path: String? = null
)

@Serializable
internal class BackupTrackJson(
    val id: String,
    val title: String,
    val artist: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val webUrl: String = ""
)

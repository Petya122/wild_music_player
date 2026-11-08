package com.wildmusicplayer.data

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Imports a public or unlisted YouTube playlist as a playlist of streamed songs, and keeps it up to date
 * ("Update from YouTube" adds the songs added there since). GitHub version only: in the Google Play
 * version [YouTubeDownloadManager] finds nothing and every call fails.
 */
class YouTubePlaylistImporter(
    private val youTube: YouTubeDownloadManager,
    private val playlists: PlaylistRepository,
    private val network: NetworkPolicyManager,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** What [import] did: made a new playlist, or added the new songs to the one imported before. */
    sealed interface ImportResult {
        val playlist: Playlist

        data class Created(override val playlist: Playlist, val songs: Int, val skipped: Int, val truncated: Boolean) : ImportResult
        data class Updated(override val playlist: Playlist, val added: Int) : ImportResult
    }

    /** The playlist's name, cover and first songs, before importing it. */
    suspend fun preview(playlistId: String): Result<YouTubePlaylist> = attempt {
        youTube.fetchPlaylist(playlistId, allPages = false)
    }

    /**
     * Imports the whole playlist [playlistId]; [onProgress] gets the number of songs loaded so far.
     * Imported before: that playlist gets the new songs instead of a second copy.
     */
    suspend fun import(playlistId: String, onProgress: (Int) -> Unit = {}): Result<ImportResult> = attempt {
        val loaded = youTube.fetchPlaylist(playlistId, allPages = true, onProgress)
        val tracks = loaded.tracks.map { it.toOnlineTrack() }
        val existing = playlists.playlistFromYouTube(playlistId)
        if (existing != null) {
            val added = playlists.applyYouTubeUpdate(existing.id, tracks, now())
            ImportResult.Updated(playlists.playlistFromYouTube(playlistId) ?: existing, added)
        } else {
            if (tracks.isEmpty()) throw YouTubeUnavailableException("This playlist has no songs that can be played.")
            val created = playlists.importYouTubePlaylist(loaded.name, playlistId, tracks, now())
            ImportResult.Created(created, created.onlineTracks.size, loaded.skipped, loaded.truncated)
        }
    }

    /** "Update from YouTube": adds the songs added to the playlist on YouTube since; returns how many. */
    suspend fun update(playlist: Playlist): Result<Int> = attempt {
        val source = playlist.source ?: throw YouTubeUnavailableException("This playlist wasn't imported from YouTube.")
        val loaded = youTube.fetchPlaylist(source.playlistId, allPages = true)
        playlists.applyYouTubeUpdate(playlist.id, loaded.tracks.map { it.toOnlineTrack() }, now())
    }

    /** Runs [block], turning what can go wrong into a failure whose message can be shown as it is. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> {
        if (network.isOfflineOnlyMode) {
            return Result.failure(YouTubeUnavailableException("Offline Mode is on. Switch to Online to load YouTube playlists."))
        }
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: YouTubeUnavailableException) {
            Result.failure(e)
        } catch (e: Exception) {
            val message = when {
                !network.isConnected() -> "No internet connection. Turn on Wi-Fi or mobile data and try again."
                e is IOException -> "Couldn't reach YouTube. Try again in a moment."
                else -> "Couldn't load this playlist from YouTube. Try again later."
            }
            Result.failure(YouTubeUnavailableException(message))
        }
    }
}

/** A YouTube result as a streamed song in a playlist. */
fun YouTubeTrack.toOnlineTrack(): OnlineTrack = OnlineTrack(
    id = id,
    title = title,
    artist = uploaderName,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    webUrl = webUrl
)

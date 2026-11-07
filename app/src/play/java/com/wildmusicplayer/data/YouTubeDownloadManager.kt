package com.wildmusicplayer.data

import android.content.Context
import java.io.File

/**
 * The Google Play version has no YouTube: this stand-in has the same functions as the full version's
 * YouTubeDownloadManager (src/full) so the shared code compiles, but finds, streams and downloads nothing.
 * The screens that would use it are switched off by BuildConfig.YOUTUBE.
 */
@Suppress("UNUSED_PARAMETER", "unused")
class YouTubeDownloadManager(context: Context) {

    fun getSearchHistory(): List<String> = emptyList()
    fun addSearchHistory(query: String) {}
    fun removeSearchHistory(query: String) {}
    fun clearSearchHistory() {}

    var preferredSearchMode: SearchMode
        get() = SearchMode.SONGS
        set(value) {}

    suspend fun search(query: String, mode: SearchMode = SearchMode.SONGS): List<YouTubeTrack> = emptyList()

    suspend fun fetchTrackDetails(track: YouTubeTrack): YouTubeTrack = track

    suspend fun downloadTrack(
        track: YouTubeTrack,
        keepPartial: () -> Boolean = { false },
        onProgress: (Float) -> Unit
    ): Result<String> = Result.failure(UnsupportedOperationException("No downloads in this version"))

    fun tagFile(source: File, metadata: TrackMetadata): File = source

    suspend fun getStreamUrl(track: YouTubeTrack): String? = null

    fun resolveStreamUrlBlocking(webUrl: String): String? = null
}

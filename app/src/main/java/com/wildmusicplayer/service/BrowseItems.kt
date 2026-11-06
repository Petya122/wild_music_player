package com.wildmusicplayer.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.wildmusicplayer.data.BrowseCatalog
import com.wildmusicplayer.data.BrowseKind
import com.wildmusicplayer.data.BrowseNode

// Android Auto's "content style" hints: how the car lays out a folder's rows
private const val CONTENT_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_LIST = 1
private const val CONTENT_STYLE_GRID = 2
private const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"

/** The root's settings for the car: lists by default, and a search button. */
internal fun rootParams(): LibraryParams = LibraryParams.Builder()
    .setExtras(Bundle().apply {
        putBoolean(SEARCH_SUPPORTED, true)
        putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_LIST)
        putInt(CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_LIST)
    })
    .build()

/**
 * A browse row as Media3 hands it to the car. Covers go through [ArtworkProvider], because the car app
 * may not read the phone's album-art addresses itself. Albums show as a grid of covers.
 */
internal fun BrowseNode.toMediaItem(artworkFor: (String?) -> Uri?): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setDisplayTitle(title)
        .setSubtitle(subtitle)
        .setArtist(song?.artist ?: subtitle)
        .setAlbumTitle(song?.album)
        .setArtworkUri(artworkFor(artUri))
        .setIsBrowsable(isBrowsable)
        .setIsPlayable(!isBrowsable)
        .setMediaType(
            when (kind) {
                BrowseKind.TAB -> if (id == BrowseCatalog.TAB_ALBUMS) MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                    else if (id == BrowseCatalog.TAB_ARTISTS) MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                    else if (id == BrowseCatalog.TAB_PLAYLISTS) MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                BrowseKind.LIST -> MediaMetadata.MEDIA_TYPE_PLAYLIST
                BrowseKind.ALBUM -> MediaMetadata.MEDIA_TYPE_ALBUM
                BrowseKind.ARTIST -> MediaMetadata.MEDIA_TYPE_ARTIST
                BrowseKind.SHUFFLE, BrowseKind.CONTINUE -> MediaMetadata.MEDIA_TYPE_PLAYLIST
                BrowseKind.SONG -> MediaMetadata.MEDIA_TYPE_MUSIC
            }
        )
        .apply {
            if (id == BrowseCatalog.TAB_ALBUMS) {
                setExtras(Bundle().apply { putInt(CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_GRID) })
            }
            song?.duration?.takeIf { it > 0 }?.let { setDurationMs(it) }
        }
        .build()
    return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
}

/** The [page] (of [pageSize]) of [items] the car asked for. */
internal fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return this
    val from = page.toLong() * pageSize
    if (from >= size) return emptyList()
    return subList(from.toInt(), minOf(size, (from + pageSize).toInt()))
}

/** Waits for a Media3 / Guava future without blocking the thread. */
internal suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitResult(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resumeWith(Result.success(get()))
            } catch (e: java.util.concurrent.ExecutionException) {
                continuation.resumeWith(Result.failure(e.cause ?: e))
            } catch (e: Exception) {
                continuation.resumeWith(Result.failure(e))
            }
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        continuation.invokeOnCancellation { cancel(false) }
    }

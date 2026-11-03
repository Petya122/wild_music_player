package com.wildmusicplayer.platform

import com.wildmusicplayer.model.Song
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVMetadataCommonKeyAlbumName
import platform.AVFoundation.AVMetadataCommonKeyArtist
import platform.AVFoundation.AVMetadataCommonKeyArtwork
import platform.AVFoundation.AVMetadataCommonKeyTitle
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.dataValue
import platform.AVFoundation.stringValue
import platform.AVFoundation.duration
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

/**
 * Songs in the app's Documents folder, which the Files app shows as "On My iPhone › Wild Music".
 * Title, artist, album and cover come from the files' own tags (the same tags the Android
 * downloader writes); covers are cached as image files so the shared screens can show them.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMusicLibrary : MusicLibrary {

    override suspend fun loadSongs(filterShortAudio: Boolean, filterRecordings: Boolean): List<Song> =
        withContext(Dispatchers.Default) {
            val root = documentsDir()
            val files = NSFileManager.defaultManager.subpathsOfDirectoryAtPath(root, error = null)
                ?.filterIsInstance<String>()
                .orEmpty()
                .filter { it.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }

            files.mapNotNull { relativePath -> readSong(root, relativePath) }
                .filter { !filterShortAudio || it.duration >= MIN_DURATION_MS }
                .sortedBy { it.title.lowercase() }
        }

    private fun readSong(root: String, relativePath: String): Song? {
        val path = "$root/$relativePath"
        val url = NSURL.fileURLWithPath(path)
        val asset = AVURLAsset(uRL = url, options = null)
        val metadata = asset.commonMetadata.filterIsInstance<AVMetadataItem>()

        fun text(key: String?): String? =
            metadata.firstOrNull { it.commonKey == key }?.stringValue?.trim()?.takeIf { it.isNotEmpty() }

        val seconds = CMTimeGetSeconds(asset.duration)
        if (seconds.isNaN() || seconds <= 0.0) return null

        val id = stableId(relativePath)
        val fileName = relativePath.substringAfterLast('/').substringBeforeLast('.')
        return Song(
            id = id,
            title = text(AVMetadataCommonKeyTitle) ?: fileName,
            artist = text(AVMetadataCommonKeyArtist) ?: "Unknown Artist",
            album = text(AVMetadataCommonKeyAlbumName) ?: "Unknown Album",
            duration = (seconds * 1000).toLong(),
            contentUri = url.absoluteString ?: return null,
            albumArtUri = cachedArtwork(id, metadata),
            folderName = relativePath.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { "Music" }
        )
    }

    /** Writes the embedded cover to the cache once and returns its path, or null if the song has none. */
    private fun cachedArtwork(id: Long, metadata: List<AVMetadataItem>): String? {
        val path = "${cachesDir()}/artwork_$id.jpg"
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) return path
        val data: NSData = metadata.firstOrNull { it.commonKey == AVMetadataCommonKeyArtwork }?.dataValue ?: return null
        return if (data.writeToFile(path, atomically = true)) path else null
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "aif", "aiff", "caf", "flac", "alac")
        private const val MIN_DURATION_MS = 30_000L

        fun documentsDir(): String =
            NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

        private fun cachesDir(): String =
            NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String

        /** A positive ID that stays the same for the same file, so favorites and playlists keep it (FNV-1a). */
        fun stableId(relativePath: String): Long {
            var hash = -0x340d631b7bdddcdbL
            for (byte in relativePath.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xFF)
                hash *= 0x100000001b3L
            }
            return (hash and Long.MAX_VALUE).coerceAtLeast(1L)
        }
    }
}

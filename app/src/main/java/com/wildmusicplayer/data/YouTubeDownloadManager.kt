package com.wildmusicplayer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class YouTubeTrack(
    val id: String,
    val title: String,
    val uploaderName: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val webUrl: String,
    val viewCount: Long = -1L,
    val likeCount: Long = -1L,
    val isUploaderVerified: Boolean = false,
    // True for YouTube Music "Songs" results: title is the clean song name, uploaderName the artist
    val isMusicSong: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedViews: String?
        get() = formatMetric(viewCount, "views")

    val formattedLikes: String?
        get() = formatMetric(likeCount, "likes")

    companion object {
        fun formatMetric(count: Long, unit: String): String? {
            if (count < 0) return null
            val formatted = when {
                count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
                else -> count.toString()
            }
            return "$formatted $unit"
        }
    }
}

enum class SearchMode(val label: String) {
    SONGS("Songs"),
    VIDEOS("Videos")
}

class YouTubeDownloadManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val historyPrefs by lazy {
        context.getSharedPreferences("downloader_search_history", Context.MODE_PRIVATE)
    }

    companion object {
        private val isInitialized = AtomicBoolean(false)
    }

    init {
        ensureInitialized()
    }

    fun getSearchHistory(): List<String> {
        val raw = historyPrefs.getString("recent_queries", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val trimmedList = current.take(15)
        val jsonArray = JSONArray(trimmedList)
        historyPrefs.edit().putString("recent_queries", jsonArray.toString()).apply()
    }

    fun removeSearchHistory(query: String) {
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        val jsonArray = JSONArray(current)
        historyPrefs.edit().putString("recent_queries", jsonArray.toString()).apply()
    }

    fun clearSearchHistory() {
        historyPrefs.edit().remove("recent_queries").apply()
    }

    var preferredSearchMode: SearchMode
        get() = historyPrefs.getString("search_mode", null)
            ?.let { saved -> SearchMode.entries.firstOrNull { it.name == saved } }
            ?: SearchMode.SONGS
        set(value) {
            historyPrefs.edit().putString("search_mode", value.name).apply()
        }

    private fun ensureInitialized() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(object : Downloader() {
                    override fun execute(request: Request): Response {
                        val httpMethod = request.httpMethod()
                        val url = request.url()
                        val headers = request.headers()
                        val dataToSend = request.dataToSend()

                        val contentType = headers["Content-Type"]?.firstOrNull()
                            ?: headers["content-type"]?.firstOrNull()
                            ?: "application/json; charset=utf-8"

                        val body = dataToSend?.toRequestBody(contentType.toMediaType())

                        val builder = OkHttpRequest.Builder()
                            .url(url)
                            .method(httpMethod, body)

                        var hasUserAgent = false
                        for ((key, valueList) in headers) {
                            if (key.equals("User-Agent", ignoreCase = true)) {
                                hasUserAgent = true
                            }
                            for (value in valueList) {
                                builder.header(key, value)
                            }
                        }

                        if (!hasUserAgent) {
                            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        }

                        val okResponse = httpClient.newCall(builder.build()).execute()
                        val responseBody = okResponse.body?.string()

                        return Response(
                            okResponse.code,
                            okResponse.message,
                            okResponse.headers.toMultimap(),
                            responseBody,
                            okResponse.request.url.toString()
                        )
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Searches YouTube. [SearchMode.SONGS] searches YouTube Music's Songs category (official tracks
     * with clean titles and artists) and falls back to regular videos when it finds nothing.
     * Pasted YouTube / YouTube Music links return that single track.
     */
    suspend fun search(query: String, mode: SearchMode = SearchMode.SONGS): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()

            // If the user pastes a direct YouTube / YouTube Music link
            val isUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) ||
                    trimmed.contains("youtube.com/", ignoreCase = true) ||
                    trimmed.contains("youtu.be/", ignoreCase = true)

            if (isUrl) {
                val cleanUrl = normalizeUrl(trimmed)
                val streamInfo = StreamInfo.getInfo(cleanUrl)
                return@withContext listOf(
                    YouTubeTrack(
                        id = streamInfo.url ?: cleanUrl,
                        title = streamInfo.name ?: "Unknown Title",
                        uploaderName = streamInfo.uploaderName ?: "Unknown Artist",
                        durationSeconds = streamInfo.duration,
                        thumbnailUrl = largestImageUrl(streamInfo.thumbnails),
                        webUrl = streamInfo.url ?: cleanUrl,
                        viewCount = streamInfo.viewCount,
                        likeCount = streamInfo.likeCount,
                        isUploaderVerified = streamInfo.isUploaderVerified
                    )
                )
            }

            if (mode == SearchMode.SONGS) {
                val songs = try {
                    runSearch(trimmed, SearchMode.SONGS)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
                if (songs.isNotEmpty()) return@withContext songs
            }
            runSearch(trimmed, SearchMode.VIDEOS)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun runSearch(query: String, mode: SearchMode): List<YouTubeTrack> {
        val contentFilter = when (mode) {
            SearchMode.SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            SearchMode.VIDEOS -> YoutubeSearchQueryHandlerFactory.VIDEOS
        }
        val searchExtractor = ServiceList.YouTube.getSearchExtractor(query, listOf(contentFilter), "")
        searchExtractor.fetchPage()

        return searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { item ->
            YouTubeTrack(
                id = item.url,
                title = item.name ?: "Unknown Title",
                uploaderName = item.uploaderName ?: "Unknown Artist",
                durationSeconds = item.duration,
                thumbnailUrl = largestImageUrl(item.thumbnails),
                webUrl = item.url,
                viewCount = item.viewCount,
                likeCount = -1L,
                isUploaderVerified = item.isUploaderVerified,
                isMusicSong = mode == SearchMode.SONGS
            )
        }
    }

    // Thumbnail lists run small → large; pick the biggest known size (or the last one if sizes are unknown)
    private fun largestImageUrl(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        val sized = images.filter { it.width > 0 }
        return (sized.maxByOrNull { it.width } ?: images.last()).url
    }

    suspend fun fetchTrackDetails(track: YouTubeTrack): YouTubeTrack = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val streamInfo = StreamInfo.getInfo(normalizeUrl(track.webUrl))
            track.copy(
                viewCount = if (streamInfo.viewCount >= 0) streamInfo.viewCount else track.viewCount,
                likeCount = streamInfo.likeCount,
                isUploaderVerified = streamInfo.isUploaderVerified
            )
        } catch (e: Exception) {
            e.printStackTrace()
            track
        }
    }

    suspend fun downloadTrack(
        track: YouTubeTrack,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val workDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val rawFile = File(workDir, "${System.nanoTime()}.m4a")
        var taggedFile: File? = null
        try {
            val streamInfo = StreamInfo.getInfo(normalizeUrl(track.webUrl))
            val bestAudioStream = pickBestAudioStream(streamInfo.audioStreams)
                ?: return@withContext Result.failure(Exception("No audio streams found for this track."))

            val streamUrl = bestAudioStream.content
                ?: return@withContext Result.failure(Exception("Audio stream URL is unavailable."))

            val metadata = trackMetadataFor(track)
            // Remembered with the saved file, so playlists know this exact video is on the phone
            val videoId = youTubeVideoId(track.webUrl) ?: youTubeVideoId(track.id)
            val downloadIndex = DownloadIndex.getInstance(context)

            // 1. Download the audio into the cache (first 90% of the progress bar)
            val request = OkHttpRequest.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download audio stream (HTTP ${response.code})"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                val totalBytes = body.contentLength()
                FileOutputStream(rawFile).use { out ->
                    body.byteStream().use { input ->
                        copyWithProgress(input, out, totalBytes) { onProgress(it * 0.9f) }
                    }
                }
            }

            // 2. Write title, artist, album and cover art into the file itself, because
            //    MediaStore re-reads the file's own tags and ignores the values we insert
            val finalFile = tagFile(rawFile, metadata)
            if (finalFile != rawFile) taggedFile = finalFile
            onProgress(0.95f)

            // 3. Save it into Music/WildMusic
            val baseName = sanitizeFileName("${metadata.artist} - ${metadata.title}")
            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore de-duplicates DISPLAY_NAME itself ("Title (1).m4a")
                val fileName = "$baseName.m4a"
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, metadata.title)
                    put(MediaStore.Audio.Media.ARTIST, metadata.artist)
                    put(MediaStore.Audio.Media.ALBUM, metadata.album)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WildMusic")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                try {
                    val output = resolver.openOutputStream(uri)
                        ?: throw IOException("Failed to open MediaStore output stream")
                    output.use { out -> finalFile.inputStream().use { it.copyTo(out) } }

                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                } catch (e: Exception) {
                    // Don't leave a half-written, pending entry behind in Music/WildMusic
                    resolver.delete(uri, null, null)
                    throw e
                }

                videoId?.let { downloadIndex.record(it, ContentUris.parseId(uri)) }
                "${Environment.DIRECTORY_MUSIC}/WildMusic/$fileName"
            } else {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "WildMusic")
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                }
                val fileName = uniqueFileName(baseName, "m4a") { File(musicDir, it).exists() }
                val targetFile = File(musicDir, fileName)

                try {
                    finalFile.copyTo(targetFile)
                } catch (e: Exception) {
                    targetFile.delete()
                    throw e
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mp4")
                ) { _, scannedUri ->
                    if (videoId != null && scannedUri != null) {
                        downloadIndex.record(videoId, ContentUris.parseId(scannedUri))
                    }
                }

                targetFile.absolutePath
            }

            onProgress(1f)
            Result.success(savedPath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            rawFile.delete()
            taggedFile?.delete()
        }
    }

    /**
     * Writes [metadata] and its cover art into a copy of [source] (same folder, "_tagged" suffix).
     * Returns that copy, or [source] itself if tagging failed or the result wouldn't play,
     * so a song is never lost to a bad tag write. Blocking: call from a background thread.
     */
    fun tagFile(source: File, metadata: TrackMetadata): File {
        val cover = CoverArt.fetchSquareJpeg(httpClient, metadata.coverUrl)
        val tagged = File(source.parentFile, "${source.nameWithoutExtension}_tagged.m4a")
        val tags = Mp4TagWriter.Tags(metadata.title, metadata.artist, metadata.album, cover)
        return if (Mp4TagWriter.writeTags(source, tagged, tags) && CoverArt.isPlayableAudio(tagged)) {
            tagged
        } else {
            tagged.delete()
            source
        }
    }

    suspend fun getStreamUrl(track: YouTubeTrack): String? = withContext(Dispatchers.IO) {
        try {
            resolveStreamUrlBlocking(track.webUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extracts the best playable audio stream URL for a YouTube page URL.
     * Blocks the calling thread, so only call it from a background thread
     * (e.g. ExoPlayer's loader thread in [com.wildmusicplayer.service.PlaybackService]).
     */
    fun resolveStreamUrlBlocking(webUrl: String): String? {
        ensureInitialized()
        val streamInfo = StreamInfo.getInfo(normalizeUrl(webUrl))
        return pickBestAudioStream(streamInfo.audioStreams)?.content
    }

    // Prefer M4A / AAC format with highest bitrate
    private fun pickBestAudioStream(audioStreams: List<AudioStream>?): AudioStream? {
        if (audioStreams.isNullOrEmpty()) return null
        return audioStreams
            .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.maxByOrNull { it.averageBitrate }
    }

    // Normalize pasted links and YouTube Music to standard YouTube for stream extraction
    private fun normalizeUrl(url: String): String {
        var target = url.trim()
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            target = "https://$target"
        }
        return target.replace("music.youtube.com", "www.youtube.com")
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var downloadedBytes = 0L
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead
            if (totalBytes > 0) {
                onProgress(downloadedBytes.toFloat() / totalBytes.toFloat())
            }
        }
    }
}

/** Replaces characters that aren't allowed in file names and caps the length. */
internal fun sanitizeFileName(title: String): String {
    val cleaned = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120).trim()
    return cleaned.ifEmpty { "track" }
}

/** Returns "base.ext", or "base (2).ext", "base (3).ext"... for the first name that isn't taken yet. */
internal fun uniqueFileName(base: String, extension: String, exists: (String) -> Boolean): String {
    var candidate = "$base.$extension"
    var n = 2
    while (exists(candidate)) {
        candidate = "$base ($n).$extension"
        n++
    }
    return candidate
}

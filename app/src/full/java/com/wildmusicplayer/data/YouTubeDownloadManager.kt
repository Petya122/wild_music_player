package com.wildmusicplayer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
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
                        val responseBody = okResponse.body.string()

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
                // Just the video: NewPipe refuses a song link that also carries "&list=…" (copied inside a playlist)
                val cleanUrl = youTubeVideoId(trimmed)?.let { "https://www.youtube.com/watch?v=$it" } ?: normalizeUrl(trimmed)
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

    /**
     * Loads the YouTube playlist [playlistId] (public or unlisted). With [allPages] false only its first
     * page (about 100 songs), enough for a preview; otherwise every song up to [MAX_PLAYLIST_TRACKS],
     * telling [onProgress] how many are loaded so far. Throws [YouTubeUnavailableException] for a private
     * or deleted playlist, and an IOException when the connection fails.
     */
    suspend fun fetchPlaylist(
        playlistId: String,
        allPages: Boolean,
        onProgress: (Int) -> Unit = {}
    ): YouTubePlaylist = withContext(Dispatchers.IO) {
        ensureInitialized()
        val url = youTubePlaylistUrl(playlistId)
        val info = try {
            PlaylistInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: ContentNotAvailableException) {
            throw YouTubeUnavailableException(
                "This playlist is private or no longer exists. Only public and unlisted playlists can be imported."
            )
        }
        val items = info.relatedItems.toMutableList()
        var nextPage = info.nextPage
        var hasMore = info.hasNextPage()
        if (allPages) {
            onProgress(items.size)
            while (hasMore && items.size < MAX_PLAYLIST_TRACKS) {
                ensureActive()
                val page = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, nextPage)
                items += page.items
                nextPage = page.nextPage
                hasMore = page.hasNextPage()
                onProgress(items.size)
            }
        }
        // Deleted and private videos stay in a playlist as "[Deleted video]" / "[Private video]"
        val playable = items.filter { item ->
            val name = item.name.orEmpty()
            name.isNotBlank() && !unavailableVideo.matches(name) && item.url.isNotBlank()
        }
        val tracks = playable.take(MAX_PLAYLIST_TRACKS).map { item ->
            YouTubeTrack(
                id = item.url,
                title = item.name ?: "Unknown Title",
                // "Queen - Topic" (YouTube Music's channels) → "Queen"
                uploaderName = cleanChannelName(item.uploaderName.orEmpty()).ifBlank { "Unknown Artist" },
                durationSeconds = item.duration,
                thumbnailUrl = largestImageUrl(item.thumbnails),
                webUrl = item.url
            )
        }
        YouTubePlaylist(
            id = playlistId,
            name = info.name?.takeIf { it.isNotBlank() } ?: "YouTube playlist",
            uploader = cleanChannelName(info.uploaderName.orEmpty()),
            thumbnailUrl = largestImageUrl(info.thumbnails),
            totalCount = info.streamCount,
            tracks = tracks,
            skipped = items.size - playable.size,
            truncated = allPages && (hasMore || playable.size > MAX_PLAYLIST_TRACKS)
        )
    }

    private val unavailableVideo = Regex("""\[(Deleted|Private) video]""", RegexOption.IGNORE_CASE)

    // Thumbnail lists run small → large; pick the biggest known size (or the last one if sizes are unknown)
    private fun largestImageUrl(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        val sized = images.filter { it.width > 0 }
        return upscaleGoogleThumbnail((sized.maxByOrNull { it.width } ?: images.last()).url)
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

    /**
     * Downloads [track] into Music/WildMusic with its tags. A download stopped by a pause
     * ([keepPartial] true when it ends) leaves its bytes in the cache and continues from there next time,
     * if YouTube still offers the same audio stream; otherwise it starts over.
     */
    suspend fun downloadTrack(
        track: YouTubeTrack,
        keepPartial: () -> Boolean = { false },
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val workDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val partialName = "partial_" + sanitizeFileName(youTubeVideoId(track.webUrl) ?: youTubeVideoId(track.id) ?: track.id)
        val rawFile = File(workDir, "$partialName.m4a")
        val partialInfo = File(workDir, "$partialName.txt")
        var taggedFile: File? = null
        var saved = false
        try {
            val streamInfo = StreamInfo.getInfo(normalizeUrl(track.webUrl))
            val bestAudioStream = pickBestAudioStream(streamInfo.audioStreams)
                ?: return@withContext Result.failure(Exception("No audio streams found for this track."))

            val streamUrl = bestAudioStream.content
                .ifBlank { return@withContext Result.failure(Exception("Audio stream URL is unavailable.")) }

            // Bytes from an earlier, paused download count only for the very same stream
            val streamId = "${bestAudioStream.itag}"
            val resumable = PartialDownload.parse(partialInfo.takeIf { it.exists() }?.readText())
                ?.takeIf { it.streamId == streamId && rawFile.length() in 1 until it.totalBytes }
            if (resumable == null) {
                rawFile.delete()
                partialInfo.delete()
            }

            val metadata = trackMetadataFor(track)
            // Remembered with the saved file, so playlists know this exact video is on the phone
            val videoId = youTubeVideoId(track.webUrl) ?: youTubeVideoId(track.id)
            val downloadIndex = DownloadIndex.getInstance(context)

            // 1. Download the audio into the cache (first 90% of the progress bar)
            downloadInChunks(
                url = streamUrl,
                target = rawFile,
                onTotalKnown = { total -> partialInfo.writeText(PartialDownload(streamId, total).format()) }
            ) { onProgress(it * 0.9f) }

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

            saved = true
            onProgress(1f)
            Result.success(savedPath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            taggedFile?.delete()
            if (saved || !keepPartial()) {
                rawFile.delete()
                partialInfo.delete()
            }
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

    /**
     * Downloads [url] into [target] in 1 MB pieces (HTTP Range requests). YouTube throttles a
     * single whole-file request to roughly playback speed, but serves each small piece at full
     * speed. A piece that fails is retried a few times, so a short network drop doesn't lose
     * the whole download.
     */
    private fun downloadInChunks(url: String, target: File, onTotalKnown: (Long) -> Unit = {}, onProgress: (Float) -> Unit) {
        // Continues after the bytes already in [target] (a paused download), or starts it
        FileOutputStream(target, true).use { out ->
            var position = target.length()
            var total = -1L
            while (total < 0 || position < total) {
                val end = position + CHUNK_SIZE - 1
                var attempt = 0
                while (true) {
                    try {
                        val received = try {
                            downloadChunk(url, position, end, out) { chunkTotal ->
                                if (total < 0) onTotalKnown(chunkTotal)
                                total = chunkTotal
                            }
                        } catch (e: RangeIgnored) {
                            // The server sends only whole files: drop what we had and take it in one go
                            out.channel.truncate(0)
                            position = 0
                            downloadChunk(url, 0, end, out) { chunkTotal -> total = chunkTotal }
                        }
                        if (received < 0) {
                            // The server ignored Range and sent the whole file in one go
                            onProgress(1f)
                            return
                        }
                        if (received == 0L) throw IOException("Empty response at byte $position")
                        position += received
                        break
                    } catch (e: IOException) {
                        if (++attempt >= CHUNK_RETRIES) throw e
                        Thread.sleep(1000L * attempt)
                    }
                }
                if (total > 0) onProgress((position.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Fetches bytes [start]..[end] and appends them to [out]. Returns the number of bytes written,
     * or -1 if the server answered with the whole file (200 instead of 206), which it also wrote.
     */
    private fun downloadChunk(
        url: String,
        start: Long,
        end: Long,
        out: OutputStream,
        onTotalKnown: (Long) -> Unit
    ): Long {
        val request = OkHttpRequest.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Range", "bytes=$start-$end")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body
            return when (response.code) {
                206 -> {
                    val total = parseContentRangeTotal(response.header("Content-Range"))
                        ?: throw IOException("Missing Content-Range")
                    onTotalKnown(total)
                    // Read the whole piece first: a piece that fails halfway writes nothing,
                    // so retrying it can't duplicate bytes in the file
                    val bytes = body.bytes()
                    out.write(bytes)
                    bytes.size.toLong()
                }
                // Asked to continue mid-file but got the whole file: only usable from the start
                200 -> if (start > 0) throw RangeIgnored() else {
                    try {
                        body.byteStream().use { input -> copyWithProgress(input, out, body.contentLength()) {} }
                    } catch (e: IOException) {
                        // Partly written already, so this one can't be retried piece-wise
                        throw IllegalStateException("Download interrupted", e)
                    }
                    -1L
                }
                // Asking past the end (file size was an exact multiple of the chunk size)
                416 -> 0L.also { onTotalKnown(start) }
                else -> throw IOException("Failed to download audio stream (HTTP ${response.code})")
            }
        }
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

/** The server answered a mid-file Range request with the whole file (see downloadInChunks). */
private class RangeIgnored : RuntimeException()

private const val CHUNK_SIZE = 1024L * 1024L
private const val CHUNK_RETRIES = 3

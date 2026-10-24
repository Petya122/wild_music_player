package com.wildmusicplayer.data

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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
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
    val isUploaderVerified: Boolean = false
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

    suspend fun search(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
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
                var cleanUrl = trimmed
                if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                    cleanUrl = "https://$cleanUrl"
                }
                // Normalize YouTube Music to standard YouTube for stream extraction
                cleanUrl = cleanUrl.replace("music.youtube.com", "www.youtube.com")

                val streamInfo = StreamInfo.getInfo(cleanUrl)
                return@withContext listOf(
                    YouTubeTrack(
                        id = streamInfo.url ?: cleanUrl,
                        title = streamInfo.name ?: "Unknown Title",
                        uploaderName = streamInfo.uploaderName ?: "Unknown Artist",
                        durationSeconds = streamInfo.duration,
                        thumbnailUrl = streamInfo.thumbnails?.firstOrNull()?.url,
                        webUrl = streamInfo.url ?: cleanUrl,
                        viewCount = streamInfo.viewCount,
                        likeCount = streamInfo.likeCount,
                        isUploaderVerified = streamInfo.isUploaderVerified
                    )
                )
            }

            val searchExtractor = ServiceList.YouTube.getSearchExtractor(trimmed)
            searchExtractor.fetchPage()

            val page = searchExtractor.initialPage
            val items = page.items

            items.filterIsInstance<StreamInfoItem>().map { item ->
                YouTubeTrack(
                    id = item.url,
                    title = item.name ?: "Unknown Title",
                    uploaderName = item.uploaderName ?: "Unknown Artist",
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url,
                    webUrl = item.url,
                    viewCount = item.viewCount,
                    likeCount = -1L,
                    isUploaderVerified = item.isUploaderVerified
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchTrackDetails(track: YouTubeTrack): YouTubeTrack = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            var targetUrl = track.webUrl
            if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                targetUrl = "https://$targetUrl"
            }
            targetUrl = targetUrl.replace("music.youtube.com", "www.youtube.com")
            val streamInfo = StreamInfo.getInfo(targetUrl)
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
        try {
            val streamInfo = StreamInfo.getInfo(track.webUrl)
            val audioStreams = streamInfo.audioStreams
            if (audioStreams.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No audio streams found for this track."))
            }

            // Prefer M4A / AAC format with highest bitrate
            val bestAudioStream = audioStreams
                .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true }
                .maxByOrNull { it.averageBitrate }
                ?: audioStreams.maxByOrNull { it.averageBitrate }
                ?: audioStreams.first()

            val streamUrl = bestAudioStream.content
                ?: return@withContext Result.failure(Exception("Audio stream URL is unavailable."))

            val cleanTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val fileName = "$cleanTitle.m4a"

            val request = OkHttpRequest.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download audio stream (HTTP ${response.code})"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            var savedPath: String? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ARTIST, track.uploaderName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/WildMusic")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
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

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                savedPath = "${Environment.DIRECTORY_MUSIC}/WildMusic/$fileName"
            } else {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "WildMusic")
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                }
                val targetFile = File(musicDir, fileName)

                FileOutputStream(targetFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
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

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )

                savedPath = targetFile.absolutePath
            }

            onProgress(1f)
            Result.success(savedPath ?: fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getStreamUrl(track: YouTubeTrack): String? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val streamInfo = StreamInfo.getInfo(track.webUrl)
            val audioStreams = streamInfo.audioStreams
            val bestAudioStream = audioStreams
                ?.filter { it.format?.name?.contains("m4a", ignoreCase = true) == true }
                ?.maxByOrNull { it.averageBitrate }
                ?: audioStreams?.maxByOrNull { it.averageBitrate }
                ?: audioStreams?.firstOrNull()
            bestAudioStream?.content
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

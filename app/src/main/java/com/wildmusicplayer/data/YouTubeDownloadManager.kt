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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class YouTubeTrack(
    val id: String,
    val title: String,
    val uploaderName: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val webUrl: String
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

class YouTubeDownloadManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private val isInitialized = AtomicBoolean(false)
    }

    init {
        ensureInitialized()
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

                        val body = dataToSend?.let {
                            it.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())
                        }

                        val builder = OkHttpRequest.Builder()
                            .url(url)
                            .method(httpMethod, body)

                        for ((key, valueList) in headers) {
                            for (value in valueList) {
                                builder.addHeader(key, value)
                            }
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
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                    webUrl = item.url
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
}

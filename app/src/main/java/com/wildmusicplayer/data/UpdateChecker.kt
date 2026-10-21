package com.wildmusicplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed interface UpdateResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult
    object UpToDate : UpdateResult
    data class Error(val message: String) : UpdateResult
}

class UpdateChecker(private val context: Context) {

    private val apiUrl = "https://api.github.com/repos/Petya122/wild_music_player/releases/latest"

    suspend fun checkForUpdates(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "WildMusicPlayer")
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                return@withContext UpdateResult.UpToDate // No releases published yet
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateResult.Error("Server returned code: $responseCode")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "").trim()
            val releaseNotes = json.optString("body", "Bug fixes and improvements.")
            val assets = json.optJSONArray("assets")

            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val remoteClean = tagName.removePrefix("v").removePrefix("V")
            val currentClean = currentVersion.removePrefix("v").removePrefix("V")

            if (isNewerVersion(remoteClean, currentClean) && !apkUrl.isNullOrBlank()) {
                UpdateResult.UpdateAvailable(
                    UpdateInfo(
                        latestVersion = tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = releaseNotes
                    )
                )
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.localizedMessage ?: "Failed to check for updates")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadUpdateSilently(
        apkUrl: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val destinationFile = File(updatesDir, fileName)

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        var connection: HttpURLConnection? = null
        try {
            var currentUrl = apkUrl
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "WildMusicPlayer")
                    instanceFollowRedirects = true
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == 307 || code == 308) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        connection.disconnect()
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                break
            }

            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val totalBytes = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            onProgress((totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            destinationFile.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun triggerApkInstall(apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
        var act: android.content.Context? = context
        while (act is android.content.ContextWrapper) {
            if (act is android.app.Activity) {
                act.finishAffinity()
                break
            }
            act = act.baseContext
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

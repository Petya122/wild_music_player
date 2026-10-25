package com.wildmusicplayer.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
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

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wild_music_player_prefs", Context.MODE_PRIVATE)

    var autoPromptInstall: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PROMPT_INSTALL, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_PROMPT_INSTALL, value).apply()
        }

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

    /**
     * Checks if a downloaded update APK already exists on disk and is ready to install.
     */
    fun getDownloadedUpdateFile(tagName: String): File? {
        val cleanTag = tagName.trim()
        val updatesDir = File(context.cacheDir, "updates")
        val candidate = File(updatesDir, "wild_music_player-$cleanTag.apk")
        return if (candidate.exists() && candidate.isFile && candidate.length() > 1024 * 1024) {
            candidate
        } else {
            null
        }
    }

    /**
     * Deletes a downloaded update APK from disk.
     */
    fun deleteDownloadedUpdate(tagName: String): Boolean {
        val cleanTag = tagName.trim()
        val updatesDir = File(context.cacheDir, "updates")
        val candidate = File(updatesDir, "wild_music_player-$cleanTag.apk")
        return if (candidate.exists()) candidate.delete() else false
    }

    /**
     * Thoroughly sweeps all APK files across internal cache, files, and legacy external download dirs.
     * Optionally preserves a currently pending APK if preserveFile is provided.
     * Returns total bytes freed.
     */
    suspend fun cleanOldApks(preserveFile: File? = null): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L

        fun sweepFile(file: File) {
            try {
                if (file.isFile && file.extension.equals("apk", ignoreCase = true)) {
                    val shouldPreserve = preserveFile != null &&
                            try { file.canonicalPath == preserveFile.canonicalPath } catch (_: Exception) { file.absolutePath == preserveFile.absolutePath }
                    if (!shouldPreserve) {
                        val len = file.length()
                        if (file.delete()) {
                            freedBytes += len
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun sweepDir(dir: File?, recursive: Boolean = true) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory && recursive) {
                    sweepDir(child, recursive = true)
                } else if (child.isFile) {
                    sweepFile(child)
                }
            }
        }

        try {
            // 1. Legacy external files download folder (e.g. Android/data/com.wildmusicplayer/files/Download/)
            val legacyDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            sweepDir(legacyDownloads, recursive = false)

            // 2. Entire external files root (for any stray apks)
            val externalFiles = context.getExternalFilesDir(null)
            sweepDir(externalFiles, recursive = true)

            // 3. Cache updates directory
            val updatesDir = File(context.cacheDir, "updates")
            sweepDir(updatesDir, recursive = false)

            // 4. Loose files in filesDir & cacheDir
            sweepDir(context.filesDir, recursive = true)
            sweepDir(context.cacheDir, recursive = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        freedBytes
    }

    /**
     * Computes the total byte size of context.cacheDir and legacy downloads recursively.
     */
    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        var total = getDirSize(context.cacheDir)
        val legacyDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (legacyDownloads != null && legacyDownloads.exists()) {
            total += getDirSize(legacyDownloads)
        }
        total
    }

    /**
     * Purges all files and subdirectories inside context.cacheDir and legacy APK downloads.
     */
    suspend fun clearAppCache(): Boolean = withContext(Dispatchers.IO) {
        var success = true
        try {
            val cache = context.cacheDir
            if (cache.exists()) {
                cache.listFiles()?.forEach { child ->
                    child.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        }
        try {
            val legacyDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (legacyDownloads != null && legacyDownloads.exists()) {
                legacyDownloads.listFiles()?.forEach { child ->
                    if (child.isFile && child.extension.equals("apk", ignoreCase = true)) {
                        child.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        success
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) {
                getDirSize(child)
            } else {
                child.length()
            }
        }
        return total
    }

    companion object {
        const val KEY_AUTO_PROMPT_INSTALL = "key_auto_prompt_install"

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 KB"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(java.util.Locale.US, "%.2f GB", gb)
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

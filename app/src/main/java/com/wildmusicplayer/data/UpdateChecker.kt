package com.wildmusicplayer.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
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

    fun downloadAndInstallUpdate(apkUrl: String, fileName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destinationFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Downloading Wild Music Player Update")
            setDescription("Fetching latest release from GitHub...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    triggerApkInstall(destinationFile)
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun triggerApkInstall(apkFile: File) {
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

package com.wildmusicplayer.data

import android.content.Context
import com.wildmusicplayer.BuildConfig
import com.wildmusicplayer.lyrics.HttpGet
import com.wildmusicplayer.lyrics.HttpResult
import com.wildmusicplayer.lyrics.LyricsRepository
import com.wildmusicplayer.platform.AndroidFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Volatile
private var lyricsInstance: LyricsRepository? = null

/** The app-wide lyrics source: saved in filesDir/lyrics, Offline Mode from [NetworkPolicyManager]. */
fun lyricsRepository(context: Context): LyricsRepository =
    lyricsInstance ?: synchronized(OkHttpGet) {
        lyricsInstance ?: run {
            val appContext = context.applicationContext
            val dir = File(appContext.filesDir, "lyrics").apply { mkdirs() }
            val network = NetworkPolicyManager.getInstance(appContext)
            LyricsRepository(
                cache = AndroidFileStore(dir),
                http = OkHttpGet,
                isOffline = { network.isOfflineOnlyMode },
                now = System::currentTimeMillis,
                localFiles = LyricsFolder.getInstance(appContext)
            ).also { lyricsInstance = it }
        }
    }

private object OkHttpGet : HttpGet {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // LRCLIB asks apps to say who they are
    private val userAgent = "WildMusicPlayer/${BuildConfig.VERSION_NAME} (https://github.com/Petya122/wild_music_player)"

    override suspend fun get(url: String): HttpResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { HttpResult(it.code, it.body?.string().orEmpty()) }
        } catch (e: IOException) {
            null
        }
    }
}

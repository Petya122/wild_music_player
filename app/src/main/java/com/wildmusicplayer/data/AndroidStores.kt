package com.wildmusicplayer.data

import android.content.Context
import com.wildmusicplayer.platform.AndroidFileStore
import com.wildmusicplayer.platform.SharedPreferencesStore
import com.wildmusicplayer.service.QueueStore

// App-wide instances of the shared stores, backed by the same files and SharedPreferences as before v0.8.5

private object Instances {
    @Volatile var downloadIndex: DownloadIndex? = null
    @Volatile var loudnessStore: LoudnessStore? = null
    @Volatile var playlistRepository: PlaylistRepository? = null
    @Volatile var appSettings: AppSettings? = null
}

fun DownloadIndex.Companion.getInstance(context: Context): DownloadIndex =
    Instances.downloadIndex ?: synchronized(Instances) {
        Instances.downloadIndex ?: DownloadIndex(AndroidFileStore(context)).also { Instances.downloadIndex = it }
    }

fun LoudnessStore.Companion.getInstance(context: Context): LoudnessStore =
    Instances.loudnessStore ?: synchronized(Instances) {
        Instances.loudnessStore ?: LoudnessStore(AndroidFileStore(context)).also { Instances.loudnessStore = it }
    }

fun PlaylistRepository.Companion.getInstance(context: Context): PlaylistRepository =
    Instances.playlistRepository ?: synchronized(Instances) {
        Instances.playlistRepository
            ?: PlaylistRepository(SharedPreferencesStore(context.applicationContext, "wmp_playlists_prefs"))
                .also { Instances.playlistRepository = it }
    }

fun AppSettings.Companion.getInstance(context: Context): AppSettings =
    Instances.appSettings ?: synchronized(Instances) {
        Instances.appSettings
            ?: AppSettings(SharedPreferencesStore(context.applicationContext, AppSettings.PREFS_NAME))
                .also { Instances.appSettings = it }
    }

fun ResumeSettings.Companion.forContext(context: Context): ResumeSettings =
    ResumeSettings(SharedPreferencesStore(context.applicationContext, AppSettings.PREFS_NAME))

fun QueueStore.Companion.forContext(context: Context): QueueStore = QueueStore(AndroidFileStore(context))

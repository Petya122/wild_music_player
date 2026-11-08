package com.wildmusicplayer.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wildmusicplayer.BuildConfig
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.AudioRepository
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.DownloadPrecondition
import com.wildmusicplayer.data.DownloadQueue
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.YouTubePlaylistImporter
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.data.toYouTubeTrack
import java.util.concurrent.TimeUnit

/**
 * Keeps playlists up to date while the app is closed (GitHub version): every 12 hours, on Wi-Fi and
 * with enough battery, imported YouTube playlists get their new songs, and "Keep downloaded" playlists
 * download what isn't on the phone yet. The downloads run right here, not in the download service:
 * Android doesn't let a background job start a foreground service. What doesn't fit in one run (about
 * 8 minutes) continues next time.
 */
class PlaylistSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!BuildConfig.YOUTUBE) return Result.success()
        val context = applicationContext
        val network = NetworkPolicyManager.getInstance(context)
        if (network.isOfflineOnlyMode) return Result.success()
        val started = SystemClock.elapsedRealtime()

        val playlists = PlaylistRepository.getInstance(context)
        val youTube = YouTubeDownloadManager(context)
        val importer = YouTubePlaylistImporter(youTube, playlists, network)

        // 1. New songs from YouTube (a failure just waits for the next run)
        for (playlist in playlists.customPlaylists.value.filter { it.source != null }) {
            if (isStopped) return Result.success()
            importer.update(playlist)
                .onSuccess { Log.i(TAG, "\"${playlist.name}\": $it new songs from YouTube") }
                .onFailure { Log.i(TAG, "\"${playlist.name}\": update failed (${it.message})") }
        }

        // 2. Downloads for "Keep downloaded" playlists, as far as the download settings allow
        val keep = playlists.customPlaylists.value.filter { it.keepDownloaded && it.onlineTracks.isNotEmpty() }
        if (keep.isEmpty()) return Result.success()
        // The app is downloading itself right now: leave it to the download service
        if (DownloadQueue.items.value.isNotEmpty()) {
            Log.i(TAG, "The app is downloading right now; downloads wait for the next run")
            return Result.success()
        }
        val precondition = network.checkDownloadPrecondition()
        if (precondition != DownloadPrecondition.Allowed) {
            Log.i(TAG, "Downloads not allowed on this network right now ($precondition)")
            return Result.success()
        }

        val settings = AppSettings.getInstance(context)
        val onPhone = AudioRepository(context)
            .getLocalSongs(settings.filterShortAudio.value, settings.filterRecordings.value)
            .mapTo(mutableSetOf()) { it.id }
        val downloads = DownloadIndex.getInstance(context)
        val missing = keep.flatMap { it.onlineTracks }
            .distinctBy { it.webUrl.ifBlank { it.id } }
            .filter { track -> downloads.downloadedIdFor(track.webUrl.ifBlank { track.id }) !in onPhone }

        Log.i(TAG, "${missing.size} songs to download")
        var saved = 0
        for (track in missing) {
            if (isStopped || SystemClock.elapsedRealtime() - started > TIME_BUDGET_MS) break
            // downloadTrack() saves the song to Music/WildMusic and remembers it in the download index
            youTube.downloadTrack(track.toYouTubeTrack()) { }
                .onSuccess { saved++ }
                .onFailure { Log.i(TAG, "\"${track.title}\" failed: ${it.message}") }
        }
        Log.i(TAG, "Downloaded $saved of ${missing.size}")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "playlist-sync"
        private const val TAG = "PlaylistSync"
        // A background job may run about 10 minutes; leave room to finish the song in progress
        private const val TIME_BUDGET_MS = 8 * 60 * 1000L

        /** Runs the check every 12 hours while some playlist needs it; otherwise stops it. */
        fun schedule(context: Context, playlists: List<Playlist>) {
            val workManager = WorkManager.getInstance(context)
            val needed = BuildConfig.YOUTUBE && playlists.any { it.source != null || it.keepDownloaded }
            if (!needed) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

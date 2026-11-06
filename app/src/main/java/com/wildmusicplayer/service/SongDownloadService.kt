package com.wildmusicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wildmusicplayer.R
import com.wildmusicplayer.data.DownloadQueue
import com.wildmusicplayer.data.DownloadState
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.YouTubeTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads the songs in [DownloadQueue], one at a time, in the foreground (with a progress notification)
 * so it keeps going with the app closed. Stops itself when the queue is empty or paused (Offline Mode).
 */
class SongDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "song_downloads_channel"
        const val NOTIFICATION_ID = 2001

        private val flowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Progress 0..1 of every song waiting or downloading, by track ID (failed ones aren't in it). */
        val activeDownloads: StateFlow<Map<String, Float>> = DownloadQueue.items
            .map { items -> items.filter { it.state != DownloadState.FAILED }.associate { it.track.id to it.progress } }
            .stateIn(flowScope, SharingStarted.Eagerly, emptyMap())

        /** Songs downloaded since the app started. */
        val completedTrackIds: StateFlow<Set<String>> = DownloadQueue.completed

        /** Adds [track] to the download queue and makes sure it's being worked through. */
        fun startDownload(context: Context, track: YouTubeTrack) {
            DownloadQueue.add(track)
            startIfNeeded(context)
        }

        /** Starts the service when songs wait and nothing pauses the queue (e.g. after going online again). */
        fun startIfNeeded(context: Context) {
            if (!DownloadQueue.canStart) return
            val intent = Intent(context, SongDownloadService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // Not allowed from the background right now: the queue waits for the next start
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var downloadManager: YouTubeDownloadManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isDownloading = false

    override fun onCreate() {
        super.onCreate()
        downloadManager = YouTubeDownloadManager(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WildMusicPlayer:SongDownloadWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android wants the notification right away, even if there turns out to be nothing to do
        val first = DownloadQueue.items.value.firstOrNull { it.state != DownloadState.FAILED }
        promoteToForeground(first?.track?.title ?: "Downloads", 0)
        processNext()
        return START_NOT_STICKY
    }

    private fun processNext() {
        if (isDownloading) return

        val next = DownloadQueue.takeNext()
        if (next == null) {
            finishService()
            return
        }

        isDownloading = true
        wakeLock?.acquire(10 * 60 * 1000L) // 10 min safety timeout
        val track = next.track

        serviceScope.launch {
            var lastUpdateMs = 0L
            // A pause keeps the bytes so far (it continues there); a cancel or failure doesn't
            val keepPartial = { DownloadQueue.isPaused && DownloadQueue.items.value.any { it.track.id == track.id } }
            val result = downloadManager.downloadTrack(track, keepPartial) { progress ->
                // Paused (offline) or removed from the queue: stop while the bytes are still coming in
                // (later the file is being saved, so let that finish)
                if (progress < 0.9f && DownloadQueue.shouldStop(track.id)) throw CancellationException("Download stopped")
                DownloadQueue.setProgress(track.id, progress)

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs >= 600L || progress >= 0.99f) {
                    lastUpdateMs = now
                    updateNotificationProgress(track.title, (progress * 100).toInt())
                }
            }

            // downloadTrack() already registers the file with MediaStore
            DownloadQueue.finished(
                track.id,
                saved = result.isSuccess,
                error = result.exceptionOrNull()?.message
            )

            // Back on the main thread (like onStartCommand) so the isDownloading check-then-set can't race
            withContext(Dispatchers.Main) {
                isDownloading = false
                processNext()
            }
        }
    }

    private fun promoteToForeground(title: String, progress: Int) {
        val notification = buildProgressNotification(title, progress)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationProgress(title: String, progress: Int) {
        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(title, progress))
    }

    private fun buildProgressNotification(title: String, progress: Int): android.app.Notification {
        val waiting = DownloadQueue.items.value.count { it.state == DownloadState.WAITING }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (waiting > 0) "Downloading song · $waiting more waiting" else "Downloading song")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun finishService() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Song Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background song downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        serviceScope.cancel()
        DownloadQueue.interrupted()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.wildmusicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wildmusicplayer.R
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.YouTubeTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class SongDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "song_downloads_channel"
        const val NOTIFICATION_ID = 2001

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_TRACK_UPLOADER = "extra_track_uploader"
        const val EXTRA_TRACK_URL = "extra_track_url"
        const val EXTRA_TRACK_DURATION = "extra_track_duration"
        const val EXTRA_TRACK_THUMBNAIL = "extra_track_thumbnail"

        private val _activeDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
        val activeDownloads = _activeDownloads.asStateFlow()

        private val _completedTrackIds = MutableStateFlow<Set<String>>(emptySet())
        val completedTrackIds = _completedTrackIds.asStateFlow()

        fun startDownload(context: Context, track: YouTubeTrack) {
            val intent = Intent(context, SongDownloadService::class.java).apply {
                putExtra(EXTRA_TRACK_ID, track.id)
                putExtra(EXTRA_TRACK_TITLE, track.title)
                putExtra(EXTRA_TRACK_UPLOADER, track.uploaderName)
                putExtra(EXTRA_TRACK_URL, track.webUrl)
                putExtra(EXTRA_TRACK_DURATION, track.durationSeconds)
                putExtra(EXTRA_TRACK_THUMBNAIL, track.thumbnailUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var downloadManager: YouTubeDownloadManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val downloadQueue = ConcurrentLinkedQueue<YouTubeTrack>()
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
        intent?.let {
            val trackId = it.getStringExtra(EXTRA_TRACK_ID)
            val title = it.getStringExtra(EXTRA_TRACK_TITLE) ?: "Unknown Track"
            val uploader = it.getStringExtra(EXTRA_TRACK_UPLOADER) ?: "Unknown Artist"
            val url = it.getStringExtra(EXTRA_TRACK_URL) ?: ""
            val duration = it.getLongExtra(EXTRA_TRACK_DURATION, 0L)
            val thumbnail = it.getStringExtra(EXTRA_TRACK_THUMBNAIL) ?: ""

            if (!trackId.isNullOrBlank() && url.isNotBlank()) {
                val track = YouTubeTrack(
                    id = trackId,
                    title = title,
                    uploaderName = uploader,
                    durationSeconds = duration,
                    thumbnailUrl = thumbnail,
                    webUrl = url
                )
                enqueueTrack(track)
            }
        }

        return START_NOT_STICKY
    }

    private fun enqueueTrack(track: YouTubeTrack) {
        downloadQueue.offer(track)
        updateActiveDownloads(track.id, 0f)
        promoteToForeground(track.title, 0)
        processNext()
    }

    private fun processNext() {
        if (isDownloading) return

        val nextTrack = downloadQueue.poll()
        if (nextTrack == null) {
            finishService()
            return
        }

        isDownloading = true
        wakeLock?.acquire(10 * 60 * 1000L) // 10 min safety timeout

        serviceScope.launch {
            var lastUpdateMs = 0L
            val result = downloadManager.downloadTrack(nextTrack) { progress ->
                updateActiveDownloads(nextTrack.id, progress)

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs >= 600L || progress >= 0.99f) {
                    lastUpdateMs = now
                    updateNotificationProgress(nextTrack.title, (progress * 100).toInt())
                }
            }

            removeActiveDownload(nextTrack.id)

            if (result.isSuccess) {
                addCompletedTrack(nextTrack.id)
                // Scan downloaded file into MediaStore
                val path = result.getOrNull()
                if (!path.isNullOrBlank()) {
                    MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(path),
                        arrayOf("audio/mp4", "audio/m4a"),
                        null
                    )
                }
            }

            isDownloading = false
            processNext()
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

    private fun buildProgressNotification(title: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Song")
            .setContentText(title)
            .setSmallIcon(R.drawable.wmp_logo)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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

    private fun updateActiveDownloads(trackId: String, progress: Float) {
        val map = _activeDownloads.value.toMutableMap()
        map[trackId] = progress
        _activeDownloads.value = map
    }

    private fun removeActiveDownload(trackId: String) {
        val map = _activeDownloads.value.toMutableMap()
        map.remove(trackId)
        _activeDownloads.value = map
    }

    private fun addCompletedTrack(trackId: String) {
        _completedTrackIds.value = _completedTrackIds.value + trackId
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

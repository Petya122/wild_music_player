package com.wildmusicplayer.service

import android.content.Context
import com.wildmusicplayer.data.DownloadPrecondition
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.YouTubeTrack
import com.wildmusicplayer.platform.DownloadBlock
import com.wildmusicplayer.platform.TrackDownloader
import kotlinx.coroutines.flow.StateFlow

/** Downloads playlist tracks with [SongDownloadService], within the Offline Mode and network rules. */
class AndroidTrackDownloader(private val context: Context) : TrackDownloader {

    override val activeDownloads: StateFlow<Map<String, Float>> = SongDownloadService.activeDownloads

    override fun blockedReason(): DownloadBlock? =
        when (NetworkPolicyManager.getInstance(context).checkDownloadPrecondition()) {
            DownloadPrecondition.BlockedOfflineMode -> DownloadBlock.OFFLINE_MODE
            DownloadPrecondition.BlockedNoNetwork -> DownloadBlock.NO_NETWORK
            DownloadPrecondition.BlockedMetered -> DownloadBlock.METERED
            else -> null
        }

    override fun start(tracks: List<OnlineTrack>) {
        tracks.forEach { track ->
            SongDownloadService.startDownload(
                context,
                YouTubeTrack(
                    id = track.id,
                    title = track.title,
                    uploaderName = track.artist,
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl,
                    webUrl = track.webUrl
                )
            )
        }
    }
}

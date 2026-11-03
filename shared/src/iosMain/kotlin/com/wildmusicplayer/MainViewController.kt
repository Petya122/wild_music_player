package com.wildmusicplayer

import androidx.compose.ui.window.ComposeUIViewController
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.LoudnessStore
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.platform.IosAudioEffects
import com.wildmusicplayer.platform.IosFileStore
import com.wildmusicplayer.platform.IosKeyValueStore
import com.wildmusicplayer.platform.IosMusicLibrary
import com.wildmusicplayer.platform.IosPlayerController
import com.wildmusicplayer.service.QueueStore
import com.wildmusicplayer.ui.LibraryViewModel
import com.wildmusicplayer.ui.WildMusicApp
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController

/** The app's single instances, created once when iOS first asks for the screen. */
private object IosApp {
    private val files = IosFileStore()
    private val audioEffects = IosAudioEffects(IosKeyValueStore("audio"), LoudnessStore(files))
    private val player = IosPlayerController(QueueStore(files), audioEffects)

    val effects get() = audioEffects

    val library = LibraryViewModel(
        library = IosMusicLibrary(),
        player = player,
        playlists = PlaylistRepository(IosKeyValueStore("playlists")),
        downloadIndex = DownloadIndex(files),
        settings = AppSettings(IosKeyValueStore("settings"))
    ).apply {
        // iOS needs no permission for the app's own Documents folder
        onPermissionResult(true)
        // Songs copied in with the Files app show up when returning to the app
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> reload() }
    }
}

/** Called from Swift (iosApp/WildMusicPlayer/iOSApp.swift) to show the app. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    WildMusicApp(
        library = IosApp.library,
        audioEffects = IosApp.effects,
        emptyLibraryHint = "Open the Files app, go to On My iPhone › Wild Music, and copy your songs there " +
            "(MP3, M4A, AAC, WAV, AIFF or FLAC). They appear here when you come back to the app."
    )
}

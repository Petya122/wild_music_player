package com.wildmusicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wildmusicplayer.lyrics.LyricsRepository
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.platform.AudioEffects
import com.wildmusicplayer.platform.SleepTimer

/** Shows a short message (a toast on Android). Provided by the app. */
val LocalShowMessage = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The player's volume and effects controls. Provided by each app. */
val LocalAudioEffects = staticCompositionLocalOf<AudioEffects> { error("LocalAudioEffects not provided") }

/** Handles the system Back button/gesture while [enabled]. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** "Play next" and "Add to queue" for song menus; null hides those menu items. Provided by each app. */
class QueueActions(val playNext: (Song) -> Unit, val addToQueue: (Song) -> Unit)

val LocalQueueActions = staticCompositionLocalOf<QueueActions?> { null }

/** Shows [message] with an Undo button that runs the given action. Provided by each app. */
val LocalShowUndo = staticCompositionLocalOf<(message: String, onUndo: () -> Unit) -> Unit> { { _, _ -> } }

/** The sleep timer; null hides its button. Provided by each app. */
val LocalSleepTimer = staticCompositionLocalOf<SleepTimer?> { null }

/** Where Now Playing gets lyrics; null hides the Lyrics button. Provided by each app. */
val LocalLyrics = staticCompositionLocalOf<LyricsRepository?> { null }

/** Finds the main color of a song's cover (ARGB), for tinting Now Playing; null = no tint. Provided by each app. */
fun interface ArtworkColors {
    suspend fun colorOf(artworkUri: String): Int?
}

val LocalArtworkColors = staticCompositionLocalOf<ArtworkColors?> { null }

/** File details for the Song info sheet; null hides "Song info" in song menus. Provided by each app. */
val LocalSongInfo = staticCompositionLocalOf<com.wildmusicplayer.platform.SongInfoSource?> { null }

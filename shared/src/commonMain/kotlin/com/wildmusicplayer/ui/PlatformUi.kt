package com.wildmusicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wildmusicplayer.platform.AudioEffects

/** Shows a short message: a toast on Android, a snackbar on iOS. Provided by each app. */
val LocalShowMessage = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The player's volume and effects controls. Provided by each app. */
val LocalAudioEffects = staticCompositionLocalOf<AudioEffects> { error("LocalAudioEffects not provided") }

/** Handles the system Back button/gesture while [enabled]. iOS has no Back button, so there it does nothing. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

package com.wildmusicplayer.ui.components

import androidx.compose.foundation.background
import com.wildmusicplayer.ui.LocalArtworkColors
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.wildmusicplayer.lyrics.LyricsRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.platform.RepeatMode
import com.wildmusicplayer.platform.SleepTimer
import com.wildmusicplayer.platform.formatSleepRemaining
import com.wildmusicplayer.lyrics.LyricsResult
import com.wildmusicplayer.ui.LocalAudioEffects
import com.wildmusicplayer.ui.LocalLyrics
import com.wildmusicplayer.ui.LocalSleepTimer
import com.wildmusicplayer.model.Song

/**
 * Full-screen Now Playing screen with album art, interactive scrub seek bar,
 * large playback controls, and car-synchronized Repeat and Shuffle toggles.
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean = false,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onStop: () -> Unit,
    onToggleFavorite: (Long) -> Unit = {},
    onShowQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val audioEffectsManager = LocalAudioEffects.current
    val sleepTimer = LocalSleepTimer.current
    val lyricsRepository = LocalLyrics.current
    var showLyrics by remember { mutableStateOf(false) }
    // Looked up only while the lyrics are showing; null while loading
    var lyrics by remember(song.mediaId) { mutableStateOf<LyricsResult?>(null) }
    LaunchedEffect(song.mediaId, showLyrics) {
        lyrics = null
        if (showLyrics && lyricsRepository != null) lyrics = lyricsRepository.lyricsFor(song)
    }
    val lyricsScope = rememberCoroutineScope()
    // The user's timing fix: shown at once, saved with the song's lyrics
    val onLyricsTimingChange: ((Long) -> Unit)? = lyricsRepository?.let { repository ->
        { offsetMs ->
            val clamped = offsetMs.coerceIn(-LyricsRepository.MAX_OFFSET_MS, LyricsRepository.MAX_OFFSET_MS)
            (lyrics as? LyricsResult.Synced)?.let { lyrics = it.copy(offsetMs = clamped) }
            lyricsScope.launch { repository.setTimingOffset(song, clamped) }
        }
    }
    var showSoundSheet by remember { mutableStateOf(false) }

    val volume by audioEffectsManager.volume.collectAsState()
    val isMuted by audioEffectsManager.isMuted.collectAsState()
    val boostDb by audioEffectsManager.volumeBoostDb.collectAsState()
    val isNormEnabled by audioEffectsManager.isNormalizationEnabled.collectAsState()

    val currentEffectiveVol = if (isMuted) 0f else volume
    val volumePercent = (currentEffectiveVol * 100).toInt()
    val volumeIcon = when {
        isMuted -> Icons.AutoMirrored.Filled.VolumeOff
        currentEffectiveVol <= 0.01f -> Icons.AutoMirrored.Filled.VolumeMute
        currentEffectiveVol < 0.40f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val progressFraction = if (duration > 0) {
        if (isDraggingSlider) dragSliderValue else (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // The cover's main color fades down into the background; it follows the song smoothly
    val artworkColors = LocalArtworkColors.current
    val coverColor by produceState<Color?>(null, song.albumArtUri) {
        value = song.albumArtUri?.let { uri -> artworkColors?.colorOf(uri)?.let { Color(it) } }
    }
    val background = MaterialTheme.colorScheme.background
    val tint by animateColorAsState(
        targetValue = coverColor?.let { lerp(background, it, COVER_TINT_STRENGTH) } ?: background,
        animationSpec = tween(600)
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to tint, 0.75f to background))
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Modern Drag Handle Pill Indicator
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (sleepTimer != null) SleepTimerButton(sleepTimer)
                }

                HeaderTitle(sleepTimer)

                Row {
                    if (onShowQueue != null) {
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Queue",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Stop Playback Button
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Stop Playback",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Album Artwork: swipe it left/right for the next/previous song
            SwipeToSkip(
                onNext = onSkipNext,
                onPrevious = onSkipPrevious,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyrics) {
                        LyricsView(
                            lyrics = lyrics,
                            currentPosition = currentPosition,
                            isPlaying = isPlaying,
                            onSeekTo = onSeekTo,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            onTimingChange = onLyricsTimingChange
                        )
                    } else {
                        SongThumbnail(
                            albumArtUri = song.albumArtUri,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track Details with Favorite Heart Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Downloads use the title as their album ("single"); repeating it adds nothing
                    if (song.album.isNotBlank() && song.album != "Unknown Album" && !song.album.equals(song.title, ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (lyricsRepository != null) {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = if (showLyrics) "Show cover" else "Show lyrics",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                IconButton(onClick = { onToggleFavorite(song.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seek Bar Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progressFraction,
                    onValueChange = { newValue ->
                        isDraggingSlider = true
                        dragSliderValue = newValue
                    },
                    onValueChangeFinished = {
                        val seekTargetMs = (dragSliderValue * duration).toLong()
                        onSeekTo(seekTargetMs)
                        isDraggingSlider = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = if (isDraggingSlider) {
                        (dragSliderValue * duration).toLong()
                    } else currentPosition

                    Text(
                        text = formatTime(displayedPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Sound & Volume Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = { showSoundSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = "Sound Settings",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMuted) "Muted" else "$volumePercent%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (boostDb > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "+${boostDb}dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (isNormEnabled) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "⚖️",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Fast Mute / Unmute Button
                FilledTonalButton(
                    onClick = { audioEffectsManager.toggleMute() },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = if (isMuted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeMute,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMuted) "Unmute" else "Mute",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Shuffle Button (Synced with Nissan 'MIX 2' button)
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (isShuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Song",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Play / Pause Primary Action Button
                FloatingActionButton(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Song",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Repeat Button (Synced with Nissan 'RPT 1' button)
                IconButton(onClick = onToggleRepeat) {
                    val (icon, tint) = when (repeatMode) {
                        RepeatMode.ONE -> Pair(Icons.Default.RepeatOne, MaterialTheme.colorScheme.primary)
                        RepeatMode.ALL -> Pair(Icons.Default.Repeat, MaterialTheme.colorScheme.primary)
                        else -> Pair(Icons.Default.Repeat, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Toggle Repeat",
                        tint = tint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }

    if (showSoundSheet) {
        SoundSettingsSheet(
            audioEffectsManager = audioEffectsManager,
            onDismiss = { showSoundSheet = false }
        )
    }
}

/** The moon button: its menu starts, changes or turns off the sleep timer. Lit while the timer runs. */
@Composable
private fun SleepTimerButton(sleepTimer: SleepTimer) {
    val state by sleepTimer.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Sleep timer",
                modifier = Modifier.size(24.dp),
                tint = if (state.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes minutes") },
                    onClick = {
                        sleepTimer.start(minutes)
                        showMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("After this song") },
                onClick = {
                    sleepTimer.stopAfterCurrentSong()
                    showMenu = false
                }
            )
            if (state.isActive) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Turn off timer", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        sleepTimer.cancel()
                        showMenu = false
                    }
                )
            }
        }
    }
}

/** "NOW PLAYING", or the sleep timer's countdown while it runs (it fits the same space). */
@Composable
private fun HeaderTitle(sleepTimer: SleepTimer?) {
    val state = sleepTimer?.state?.collectAsState()?.value
    Text(
        text = when {
            state == null || !state.isActive -> "NOW PLAYING"
            state.atEndOfSong -> "LAST SONG"
            else -> "SLEEP ${formatSleepRemaining(state.remainingMs)}"
        },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
}

// How much of the cover color shows at the top of Now Playing (0 = none, 1 = full)
private const val COVER_TINT_STRENGTH = 0.45f

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

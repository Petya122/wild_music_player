package com.wildmusicplayer.ui.components

import com.wildmusicplayer.lyrics.LyricsSource
import androidx.compose.foundation.clickable
import com.wildmusicplayer.ui.LocalShowMessage
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.lyrics.LyricLine
import com.wildmusicplayer.lyrics.LyricsResult
import com.wildmusicplayer.lyrics.currentLineIndex
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * The lyrics in place of the cover. Synced lyrics follow the song with the current line lit and kept
 * in view; tapping a line jumps there. Plain lyrics just scroll. [lyrics] null = still loading.
 *
 * Timing fixes ([onTimingChange] gets the new offset): the -/+ buttons move every line half a second,
 * and long-pressing a line when its words start moves the lyrics so that line starts right now.
 */
@Composable
fun LyricsView(
    lyrics: LyricsResult?,
    currentPosition: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onTimingChange: ((offsetMs: Long) -> Unit)? = null,
    // The user's own .lrc file for this song: load one, or go back to the found lyrics
    onLoadOwnFile: (() -> Unit)? = null,
    onRemoveOwnFile: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        when (lyrics) {
            null -> CircularProgressIndicator()
            is LyricsResult.Synced ->
                SyncedLyrics(lyrics.lines, lyrics.offsetMs, currentPosition, isPlaying, onSeekTo, onTimingChange)
            is LyricsResult.Plain -> Text(
                text = lyrics.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            )
            LyricsResult.Instrumental -> Message("♪ Instrumental ♪")
            LyricsResult.NotFound -> Message("No lyrics found for this song")
            LyricsResult.Offline -> Message("Lyrics for this song aren't saved yet.\nTurn off Offline Mode to look them up.")
            LyricsResult.NoOwnFile -> Message("No lyrics file for this song yet.\nLoad a .lrc file below, or pick your lyrics folder in Settings.")
            LyricsResult.Failed -> Message("Couldn't reach the lyrics service.\nCheck your connection and try again.")
        }
    }
    if (lyrics != null) LyricsSourceBar(lyrics, onLoadOwnFile, onRemoveOwnFile)
    }
}

/** Where the lyrics come from, and loading / removing the user's own .lrc file. */
@Composable
private fun LyricsSourceBar(lyrics: LyricsResult, onLoadOwnFile: (() -> Unit)?, onRemoveOwnFile: (() -> Unit)?) {
    val source = when (lyrics) {
        is LyricsResult.Synced -> lyrics.source
        is LyricsResult.Plain -> lyrics.source
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            source == LyricsSource.OWN_FILE -> {
                Text("Your lyrics file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                if (onRemoveOwnFile != null) TextButton(onClick = onRemoveOwnFile) { Text("Remove") }
            }
            source == LyricsSource.FOLDER ->
                Text(
                    "From your lyrics folder",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            onLoadOwnFile != null -> TextButton(onClick = onLoadOwnFile) {
                Text("Use my own .lrc file\u2026", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    offsetMs: Long,
    currentPosition: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onTimingChange: ((Long) -> Unit)?
) {
    val showMessage = LocalShowMessage.current
    // The player reports its position twice a second; count between reports so lines change on time
    var position by remember { mutableLongStateOf(currentPosition) }
    LaunchedEffect(currentPosition, isPlaying) {
        position = currentPosition
        if (!isPlaying) return@LaunchedEffect
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            delay(100)
            position = currentPosition + start.elapsedNow().inWholeMilliseconds
        }
    }

    // A positive offset shows the lines later: compare them with an earlier moment of the song
    val current = currentLineIndex(lines, position - offsetMs)
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onTimingChange != null) TimingBar(offsetMs, onTimingChange)
        LyricLines(lines, current, offsetMs, position, listState, onSeekTo, onTimingChange, showMessage)
    }
}

@Composable
private fun ColumnScope.LyricLines(
    lines: List<LyricLine>,
    current: Int,
    offsetMs: Long,
    position: Long,
    listState: LazyListState,
    onSeekTo: (Long) -> Unit,
    onTimingChange: ((Long) -> Unit)?,
    showMessage: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == current
            Text(
                text = line.text.ifBlank { "♪" },
                style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    index < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onSeekTo(line.timeMs + offsetMs) },
                        // Long-press when the words start: this line, and every other, moves to fit
                        onLongClick = onTimingChange?.let { change ->
                            {
                                change(timingOffsetFor(line, position))
                                showMessage("Lyrics timing adjusted to this line")
                            }
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
    }
}

/** "[−0.5 s]   +1.5 s · Reset   [+0.5 s]", with a hint about long-pressing a line. */
@Composable
private fun TimingBar(offsetMs: Long, onTimingChange: (Long) -> Unit) {
    // The two step buttons keep their size; the middle takes what's left and never wraps
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimingStepButton("−0.5 s") { onTimingChange(offsetMs - TIMING_STEP_MS) }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (offsetMs == 0L) {
                Text(
                    text = "Timing as found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                TextButton(onClick = { onTimingChange(0L) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        text = "${formatOffset(offsetMs)} · Reset",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        TimingStepButton("+0.5 s") { onTimingChange(offsetMs + TIMING_STEP_MS) }
    }
    Text(
        text = "Long-press a line when you hear it start to line them all up",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

@Composable
private fun TimingStepButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

private const val TIMING_STEP_MS = 500L

/** The offset that makes [line] start at [positionMs], in whole tenths of a second. */
internal fun timingOffsetFor(line: LyricLine, positionMs: Long): Long = ((positionMs - line.timeMs) / 100) * 100

/** 1500 -> "+1.5 s", -500 -> "\u22120.5 s". */
internal fun formatOffset(offsetMs: Long): String {
    val tenths = kotlin.math.abs(offsetMs) / 100
    return (if (offsetMs < 0) "\u2212" else "+") + "${tenths / 10}.${tenths % 10} s"
}

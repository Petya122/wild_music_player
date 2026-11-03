package com.wildmusicplayer.ui.components

import androidx.compose.foundation.clickable
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
 */
@Composable
fun LyricsView(
    lyrics: LyricsResult?,
    currentPosition: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (lyrics) {
            null -> CircularProgressIndicator()
            is LyricsResult.Synced -> SyncedLyrics(lyrics.lines, currentPosition, isPlaying, onSeekTo)
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
            LyricsResult.Failed -> Message("Couldn't reach the lyrics service.\nCheck your connection and try again.")
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
    currentPosition: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit
) {
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

    val current = currentLineIndex(lines, position)
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
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
                    .clickable { onSeekTo(line.timeMs) }
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
    }
}

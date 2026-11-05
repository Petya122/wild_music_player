package com.wildmusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.platform.QueueItem
import com.wildmusicplayer.ui.screens.formatSongDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The play queue: the playing song and what comes after it. Tap a song to play it, ✕ to remove it,
 * drag the handle to reorder (only with shuffle off, when the list order is the play order).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    currentPosition: Int,
    isShuffleEnabled: Boolean,
    onPlayItem: (position: Int) -> Unit,
    onRemoveItem: (position: Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // A local copy follows the finger while dragging; the player only hears about the move when it's dropped
    var items by remember { mutableStateOf(queue) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(queue) { if (dragFrom == null) items = queue }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LaunchedEffect(Unit) {
        if (currentPosition > 0) listState.scrollToItem(currentPosition - 1)
    }

    val upNext = (queue.size - currentPosition - 1).coerceAtLeast(0)
    val remainingMs = queue.drop(currentPosition + 1).sumOf { it.song.duration }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = buildString {
                        append(if (upNext == 1) "1 song up next" else "$upNext songs up next")
                        if (remainingMs > 0) append(" • ${formatTotal(remainingMs)}")
                        if (isShuffleEnabled) append(" • turn off shuffle to reorder")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(items, key = { _, item -> item.playerIndex }) { position, item ->
                    ReorderableItem(reorderState, key = item.playerIndex) { isDragging ->
                        val isCurrent = item.playerIndex == queue.getOrNull(currentPosition)?.playerIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isDragging -> MaterialTheme.colorScheme.surfaceVariant
                                        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onPlayItem(position) }
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongThumbnail(albumArtUri = item.song.albumArtUri, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Now playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
                                )
                            } else if (item.song.duration > 0) {
                                Text(
                                    text = formatSongDuration(item.song.duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            IconButton(onClick = { onRemoveItem(position) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isShuffleEnabled) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { dragFrom = position },
                                        onDragStopped = {
                                            val from = dragFrom
                                            val to = items.indexOfFirst { it.playerIndex == item.playerIndex }
                                            dragFrom = null
                                            if (from != null && to >= 0 && from != to) onMoveItem(from, to)
                                        }
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTotal(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min left" else "$minutes min left"
}

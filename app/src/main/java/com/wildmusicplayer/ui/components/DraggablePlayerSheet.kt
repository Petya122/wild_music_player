package com.wildmusicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * YouTube Music / Spotify-style interactive draggable player sheet.
 * Tracks user's finger 1:1 during dragging up or down, and smoothly
 * snaps to expanded or collapsed positions using spring physics.
 */
@Composable
fun DraggablePlayerSheet(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeightDp = 72.dp + navBarBottomPadding
    val peekHeightPx = with(density) { peekHeightDp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val maxOffset = (screenHeightPx - peekHeightPx).coerceAtLeast(0f)

        val offsetY = remember { Animatable(if (isExpanded) 0f else maxOffset) }

        // Sync if changed via external events (like back button or song click)
        LaunchedEffect(isExpanded, maxOffset) {
            val target = if (isExpanded) 0f else maxOffset
            if (offsetY.value != target) {
                offsetY.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }

        val velocityTracker = remember { VelocityTracker() }

        // 0f = fully collapsed (miniplayer), 1f = fully expanded (full player)
        val progress = if (maxOffset > 0f) {
            (1f - (offsetY.value / maxOffset)).coerceIn(0f, 1f)
        } else 0f

        var dragStartOffsetY by remember { mutableFloatStateOf(0f) }

        val dragModifier = Modifier.pointerInput(maxOffset) {
            detectVerticalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    dragStartOffsetY = offsetY.value
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch {
                        val newOffset = (offsetY.value + dragAmount).coerceIn(0f, maxOffset)
                        offsetY.snapTo(newOffset)
                    }
                },
                onDragEnd = {
                    val velocity = velocityTracker.calculateVelocity().y
                    scope.launch {
                        val deltaY = offsetY.value - dragStartOffsetY
                        val isStartedFromCollapsed = dragStartOffsetY > maxOffset * 0.6f
                        val isStartedFromExpanded = dragStartOffsetY < maxOffset * 0.4f
                        val thresholdPx = with(density) { 36.dp.toPx() }

                        val target = when {
                            // Responsive flick gestures (up or down)
                            velocity < -350f -> 0f              // Flick up -> expand
                            velocity > 350f -> maxOffset        // Flick down -> collapse
                            // Started from miniplayer: any clear upward drag expands
                            isStartedFromCollapsed -> {
                                if (deltaY < -thresholdPx) 0f else maxOffset
                            }
                            // Started from full player: any clear downward drag collapses
                            isStartedFromExpanded -> {
                                if (deltaY > thresholdPx) maxOffset else 0f
                            }
                            // Mid-screen fallback
                            offsetY.value > maxOffset * 0.5f -> maxOffset
                            else -> 0f
                        }
                        val willBeExpanded = (target == 0f)
                        onExpandedChange(willBeExpanded)
                        offsetY.animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .then(dragModifier)
        ) {
            // Full Screen Player
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((progress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    }
            ) {
                if (progress > 0.05f) {
                    NowPlayingScreen(
                        song = song,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        onTogglePlay = onTogglePlay,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeekTo = onSeekTo,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onCollapse = {
                            onExpandedChange(false)
                            scope.launch {
                                offsetY.animateTo(
                                    maxOffset,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    )
                }
            }

            // Mini Player Bar (Visible when collapsed / collapsing)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = (1f - progress * 2.5f).coerceIn(0f, 1f)
                    }
            ) {
                if (progress < 0.6f) {
                    MiniPlayerBar(
                        song = song,
                        isPlaying = isPlaying,
                        onExpand = {
                            onExpandedChange(true)
                            scope.launch {
                                offsetY.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        },
                        onTogglePlay = onTogglePlay,
                        onSkipNext = onSkipNext,
                        onSkipPrev = onSkipPrevious,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }
        }
    }
}

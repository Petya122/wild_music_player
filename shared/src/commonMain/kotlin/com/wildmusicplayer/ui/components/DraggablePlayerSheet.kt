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
import androidx.compose.runtime.mutableStateOf
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
    onStop: () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bottomBarHeight: androidx.compose.ui.unit.Dp = 80.dp,
    isFavorite: Boolean = false,
    onToggleFavorite: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeightDp = 72.dp + bottomBarHeight + navBarBottomPadding
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
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 1000f
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
        var accumulatedDragY by remember { mutableFloatStateOf(0f) }
        var hasCommittedGesture by remember { mutableStateOf(false) }

        val dragModifier = Modifier.pointerInput(maxOffset, isExpanded) {
            detectVerticalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    dragStartOffsetY = offsetY.value
                    accumulatedDragY = 0f
                    hasCommittedGesture = false
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    accumulatedDragY += dragAmount

                    val triggerThresholdPx = with(density) { 10.dp.toPx() }

                    if (!hasCommittedGesture) {
                        if (!isExpanded && accumulatedDragY < -triggerThresholdPx) {
                            // Instant detection: swiped up on miniplayer -> bring up full player
                            hasCommittedGesture = true
                            onExpandedChange(true)
                            scope.launch {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 800f
                                    )
                                )
                            }
                        } else if (isExpanded && accumulatedDragY > triggerThresholdPx) {
                            // Instant detection: swiped down on full player -> collapse to miniplayer
                            hasCommittedGesture = true
                            onExpandedChange(false)
                            scope.launch {
                                offsetY.animateTo(
                                    targetValue = maxOffset,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 800f
                                    )
                                )
                            }
                        } else {
                            // Follow finger slightly before threshold is reached
                            scope.launch {
                                val newOffset = (offsetY.value + dragAmount).coerceIn(0f, maxOffset)
                                offsetY.snapTo(newOffset)
                            }
                        }
                    }
                },
                onDragEnd = {
                    if (!hasCommittedGesture) {
                        val velocity = velocityTracker.calculateVelocity().y
                        scope.launch {
                            val target = when {
                                !isExpanded && (velocity < -30f || accumulatedDragY < 0f) -> 0f
                                isExpanded && (velocity > 30f || accumulatedDragY > 0f) -> maxOffset
                                isExpanded -> 0f
                                else -> maxOffset
                            }
                            val willBeExpanded = (target == 0f)
                            onExpandedChange(willBeExpanded)
                            offsetY.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 800f
                                )
                            )
                        }
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
        ) {
            // Full Screen Player
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isExpanded || progress > 0.05f) dragModifier else Modifier)
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
                        },
                        onStop = onStop,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // Mini Player Bar (Visible when collapsed / collapsing)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(if (!isExpanded || progress < 0.6f) dragModifier else Modifier)
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
                        onStop = onStop
                    )
                }
            }
        }
    }
}

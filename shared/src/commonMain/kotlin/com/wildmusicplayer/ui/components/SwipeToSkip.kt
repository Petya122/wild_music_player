package com.wildmusicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe left for the next song, right for the previous one. The content follows the finger; past a
 * quarter of its width (or on a quick flick) it slides out, the song changes, and it slides back in from
 * the other side. Vertical drags are left alone, so the player sheet's up/down swipe still works.
 */
@Composable
fun SwipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val velocityTracker = remember { VelocityTracker() }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x
                        val next = offsetX.value < -width * COMMIT_FRACTION || velocity < -FLICK_VELOCITY
                        val previous = offsetX.value > width * COMMIT_FRACTION || velocity > FLICK_VELOCITY
                        scope.launch {
                            if (next || previous) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val out = if (next) -width.toFloat() else width.toFloat()
                                offsetX.animateTo(out, tween(OUT_MS))
                                if (next) onNext() else onPrevious()
                                offsetX.snapTo(-out)
                            }
                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
                )
            }
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (abs(offsetX.value) / width).coerceIn(0f, 1f) * 0.7f
            }
    ) {
        content()
    }
}

private const val COMMIT_FRACTION = 0.25f
private const val FLICK_VELOCITY = 1200f
private const val OUT_MS = 140

package com.wildmusicplayer.ui.components

import kotlin.math.abs
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.platform.AudioEffects
import com.wildmusicplayer.platform.EqualizerState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettingsSheet(
    audioEffectsManager: AudioEffects,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val volume by audioEffectsManager.volume.collectAsState()
    val isMuted by audioEffectsManager.isMuted.collectAsState()
    val boostDb by audioEffectsManager.volumeBoostDb.collectAsState()
    val isNormEnabled by audioEffectsManager.isNormalizationEnabled.collectAsState()
    val equalizer by audioEffectsManager.equalizer.collectAsState()

    val currentEffectiveVol = if (isMuted) 0f else volume
    val volumePercent = (currentEffectiveVol * 100).toInt()

    val volumeIcon = when {
        isMuted -> Icons.AutoMirrored.Filled.VolumeOff
        currentEffectiveVol <= 0.01f -> Icons.AutoMirrored.Filled.VolumeMute
        currentEffectiveVol < 0.40f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sound & Volume",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Player volume, presets & loudness boost",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Volume Indicator & Mute Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = volumeIcon,
                        contentDescription = null,
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMuted) "Muted" else "$volumePercent%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledTonalButton(
                    onClick = { audioEffectsManager.toggleMute() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isMuted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = if (isMuted) "Unmute" else "Mute",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Smooth Volume Slider
            Slider(
                value = currentEffectiveVol,
                onValueChange = { audioEffectsManager.setVolume(it) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Pre-set Volume Snap Points
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.00f to "100%")
                presets.forEach { (targetVol, label) ->
                    val isSelected = !isMuted && abs(volume - targetVol) < 0.03f
                    FilterChip(
                        selected = isSelected,
                        onClick = { audioEffectsManager.setVolume(targetVol) },
                        label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Decibel (dB) Boost Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Decibel Boost (Loudness Gain)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hardware DSP gain beyond 100% for quiet recordings & car audio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // dB Boost Pre-sets
            DbBoostSelector(
                selectedDb = boostDb,
                onSelect = { audioEffectsManager.setVolumeBoostDb(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Normalization Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Audio Normalization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Measures each song and evens out the volume between tracks. Online streams play as they are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Switch(
                    checked = isNormEnabled,
                    onCheckedChange = { audioEffectsManager.setNormalizationEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            EqualizerSection(equalizer, audioEffectsManager)
        }
    }
}

/** On/off, the phone's presets as chips, and a slider per band (which turns the curve into "Custom"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerSection(equalizer: EqualizerState?, effects: AudioEffects) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (equalizer == null) {
                    "Not available on this phone"
                } else {
                    "Pick a preset or shape the sound yourself. Saved for next time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = equalizer?.isEnabled == true,
            enabled = equalizer != null,
            onCheckedChange = { effects.setEqualizerEnabled(it) }
        )
    }
    if (equalizer == null || !equalizer.isEnabled) return

    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "Custom" first: it lights up when a slider moves, so it must always be in view
        FilterChip(selected = equalizer.presetIndex == -1, onClick = {}, label = { Text("Custom") })
        equalizer.presets.forEachIndexed { index, name ->
            FilterChip(
                selected = equalizer.presetIndex == index,
                onClick = { effects.useEqualizerPreset(index) },
                label = { Text(name) }
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    equalizer.bandFrequenciesHz.forEachIndexed { band, hz ->
        val level = equalizer.bandLevelsMb.getOrElse(band) { 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatFrequency(hz),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp)
            )
            Slider(
                value = level.toFloat(),
                onValueChange = { effects.setEqualizerBandLevel(band, it.roundToInt()) },
                valueRange = equalizer.minLevelMb.toFloat()..equalizer.maxLevelMb.toFloat(),
                modifier = Modifier.weight(1f),
                track = { CenteredTrack(level.toFloat(), equalizer.minLevelMb.toFloat()..equalizer.maxLevelMb.toFloat()) }
            )
            Text(
                text = formatDb(level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp)
            )
        }
    }
}

/** A band's track, filled from 0 dB (the middle, marked with a tick) up or down to its level. */
@Composable
private fun CenteredTrack(value: Float, range: ClosedFloatingPointRange<Float>) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
        fun xOf(v: Float) = size.width * ((v.coerceIn(range.start, range.endInclusive) - range.start) / span)
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(color = inactive, cornerRadius = corner)
        val zero = xOf(0f)
        val at = xOf(value)
        drawRoundRect(
            color = active,
            topLeft = Offset(minOf(zero, at), 0f),
            size = Size(abs(at - zero), size.height),
            cornerRadius = corner
        )
        val tick = 2.dp.toPx()
        drawRect(
            color = active.copy(alpha = 0.6f),
            topLeft = Offset(zero - tick / 2, -tick),
            size = Size(tick, size.height + tick * 2)
        )
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) {
        val tenths = (hz + 50) / 100
        if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
    } else {
        "$hz Hz"
    }

private fun formatDb(levelMb: Int): String {
    val db = (levelMb / 100f).roundToInt()
    return if (db > 0) "+$db dB" else "$db dB"
}

/**
 * Off / +3 / +6 / +9 / +12 dB boost picker, shared by this sheet and Settings.
 * Compact buttons instead of chips, so all five labels fit on one line even in narrow cards.
 */
@Composable
fun DbBoostSelector(
    selectedDb: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(0 to "Off", 3 to "+3", 6 to "+6", 9 to "+9", 12 to "+12")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (db, label) ->
            val selected = selectedDb == db
            Surface(
                onClick = { onSelect(db) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

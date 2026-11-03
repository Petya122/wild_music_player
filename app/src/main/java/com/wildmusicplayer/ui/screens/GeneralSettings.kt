package com.wildmusicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.AudioEffectsManager
import com.wildmusicplayer.data.LoudnessStore
import com.wildmusicplayer.data.ResumeSettings
import com.wildmusicplayer.data.forContext
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.ui.components.DbBoostSelector
import com.wildmusicplayer.ui.theme.ThemeMode
import kotlin.math.abs

// Settings sections: theme, screen, car, library filters and sound

@Composable
internal fun AppearanceSection(currentTheme: ThemeMode, onThemeSelected: (ThemeMode) -> Unit) {
    SettingsCard(icon = Icons.Default.Palette, title = "Appearance & Theme") {
        ThemeMode.entries.forEachIndexed { index, mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeSelected(mode) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (mode == currentTheme),
                    onClick = { onThemeSelected(mode) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (mode == currentTheme) FontWeight.SemiBold else FontWeight.Normal
                    )
                    val subtitle = when (mode) {
                        ThemeMode.SYSTEM -> "Matches your Android system dark/light mode"
                        ThemeMode.LIGHT -> "Clean light theme with vibrant accents"
                        ThemeMode.DARK -> "Battery-friendly pitch dark AMOLED mode"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (index < ThemeMode.entries.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
internal fun DisplaySection(keepScreenOn: Boolean, onKeepScreenOnToggled: (Boolean) -> Unit) {
    SettingsCard(icon = Icons.Default.Smartphone, title = "Display & Screen") {
        SettingsSwitchRow(
            title = "Keep Screen On",
            description = "Prevent the screen from dimming or turning off while using the app",
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnToggled
        )
    }
}

@Composable
internal fun CarSection() {
    val appContext = LocalContext.current.applicationContext
    val resumeSettings = remember { ResumeSettings.forContext(appContext) }
    var resumeFromPlayButton by remember { mutableStateOf(resumeSettings.resumeFromPlayButton) }
    var resumeOnBluetooth by remember { mutableStateOf(resumeSettings.resumeOnBluetoothReconnect) }

    SettingsCard(icon = Icons.Default.DirectionsCar, title = "Car & Bluetooth") {
        SettingsSwitchRow(
            title = "Resume with Play Button",
            description = "Pressing Play on your car radio or headset continues where you left off, even if the app was closed",
            checked = resumeFromPlayButton,
            onCheckedChange = { enabled ->
                resumeFromPlayButton = enabled
                resumeSettings.resumeFromPlayButton = enabled
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSwitchRow(
            title = "Resume When Bluetooth Reconnects",
            description = "If music paused because Bluetooth disconnected, it starts again when you reconnect within 2 hours (while the app is still running)",
            checked = resumeOnBluetooth,
            onCheckedChange = { enabled ->
                resumeOnBluetooth = enabled
                resumeSettings.resumeOnBluetoothReconnect = enabled
            }
        )
    }
}

@Composable
internal fun LibrarySection(
    filterShortAudio: Boolean,
    onFilterShortAudioToggled: (Boolean) -> Unit,
    filterRecordings: Boolean,
    onFilterRecordingsToggled: (Boolean) -> Unit
) {
    SettingsCard(icon = Icons.Default.Audiotrack, title = "Audio Library & Filtering") {
        SettingsSwitchRow(
            title = "Filter Short Audio Clips (< 30s)",
            description = "Hides notifications, ringtones, UI sounds, and short audio snippets",
            checked = filterShortAudio,
            onCheckedChange = onFilterShortAudioToggled
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = "Hide Voice & Call Recordings",
            description = "Hides voice memos, phone call records, and messenger audio notes",
            checked = filterRecordings,
            onCheckedChange = onFilterRecordingsToggled
        )
    }
}

@Composable
internal fun SoundSection() {
    val context = LocalContext.current
    val audioEffectsManager = remember { AudioEffectsManager.getInstance(context) }
    val volume by audioEffectsManager.volume.collectAsState()
    val isMuted by audioEffectsManager.isMuted.collectAsState()
    val boostDb by audioEffectsManager.volumeBoostDb.collectAsState()
    val isNormEnabled by audioEffectsManager.isNormalizationEnabled.collectAsState()
    val loudnessProgress by remember { LoudnessStore.getInstance(context) }.progress.collectAsState()

    val currentEffectiveVol = if (isMuted) 0f else volume
    val volumePercent = (currentEffectiveVol * 100).toInt()

    SettingsCard(icon = Icons.Default.GraphicEq, title = "Audio & Sound Effects") {
        SettingsSwitchRow(
            title = "Audio Normalization",
            description = "Measures how loud each song is and evens out the volume between tracks (to −14 LUFS, the usual streaming level). Online streams play as they are.",
            checked = isNormEnabled,
            onCheckedChange = { audioEffectsManager.setNormalizationEnabled(it) }
        ) {
            if (isNormEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        loudnessProgress.total == 0 && loudnessProgress.running -> "Measuring songs…"
                        loudnessProgress.total == 0 -> "Songs are measured in the background (not while the battery is low)"
                        loudnessProgress.running ->
                            "Measuring songs: ${loudnessProgress.measured} of ${loudnessProgress.total}"
                        loudnessProgress.measured >= loudnessProgress.total ->
                            "All ${loudnessProgress.total} songs measured"
                        else -> "${loudnessProgress.measured} of ${loudnessProgress.total} songs measured"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SettingsDivider()

        // Volume Boost (Decibel Gain)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Decibel Boost (Loudness Gain)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Hardware DSP gain beyond 100% for quiet recordings & low car Bluetooth signals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            DbBoostSelector(
                selectedDb = boostDb,
                onSelect = { audioEffectsManager.setVolumeBoostDb(it) }
            )
        }

        SettingsDivider()

        // In-Player Volume & Mute Row
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Player Output Volume",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isMuted) "Muted" else "Level: $volumePercent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = { audioEffectsManager.toggleMute() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isMuted) "Unmute" else "Mute")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
        }
    }
}

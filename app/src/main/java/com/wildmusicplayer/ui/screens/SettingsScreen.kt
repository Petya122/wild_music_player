package com.wildmusicplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.DownloadQueue
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.ui.theme.AccentColor
import com.wildmusicplayer.ui.theme.ThemeMode

/** The pages of Settings: the main list shows one row for each, which opens it. */
enum class SettingsPage(val title: String, val summary: String, val icon: ImageVector) {
    APPEARANCE("Appearance", "Theme, accent color, keep screen on", Icons.Default.Palette),
    SOUND("Playback & Sound", "Normalization, decibel boost, player volume", Icons.Default.GraphicEq),
    CAR("Car & Bluetooth", "Resume with Play, reconnecting, sound per device", Icons.Default.DirectionsCar),
    LIBRARY("Library & Lyrics", "Short clips, recordings, lyrics folder, fixing tags", Icons.Default.Audiotrack),
    DOWNLOADS("Downloads & Network", "Offline Mode, mobile data, download queue", Icons.Default.CloudDownload),
    BACKUP("Backup & Restore", "Playlists and favorites to a file and back", Icons.Default.Backup),
    UPDATES("Updates & Storage", "New versions, what's new, cache", Icons.Default.SystemUpdate)
}

/**
 * Settings: a short list of pages ([SettingsPage]), then Permissions, About and Exit. [page] is the page
 * open (null = the list); the top bar's back arrow and the Back key close it first (MainActivity).
 */
@Composable
fun SettingsScreen(
    page: SettingsPage?,
    onOpenPage: (SettingsPage) -> Unit,
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggled: (Boolean) -> Unit,
    filterShortAudio: Boolean,
    onFilterShortAudioToggled: (Boolean) -> Unit,
    filterRecordings: Boolean,
    onFilterRecordingsToggled: (Boolean) -> Unit,
    onShowWhatsNew: () -> Unit,
    onShowAbout: () -> Unit,
    onShowDownloads: () -> Unit,
    onExitApp: () -> Unit,
    bottomPadding: Dp,
    librarySongs: List<Song>,
    isOfflineOnlyMode: Boolean = false,
    onOfflineOnlyModeToggled: (Boolean) -> Unit = {},
    downloadManager: YouTubeDownloadManager? = null,
    onLibraryChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // A fresh scroll position for each page
    key(page) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(if (page == null) 10.dp else 16.dp)
        ) {
            when (page) {
                null -> {
                    SettingsPage.entries.forEach { entry ->
                        SettingsLinkCard(icon = entry.icon, title = entry.title, subtitle = entry.summary, onClick = { onOpenPage(entry) })
                    }
                    PermissionsCard()
                    SettingsLinkCard(
                        icon = Icons.Default.Info,
                        title = "About & Credits",
                        subtitle = "Wild Music Player • Developed by Petya122",
                        onClick = onShowAbout
                    )
                    Spacer(Modifier.height(6.dp))
                    ExitCard(onExitApp)
                }
                SettingsPage.APPEARANCE -> {
                    AppearanceSection(currentTheme, onThemeSelected, currentAccent, onAccentSelected)
                    DisplaySection(keepScreenOn, onKeepScreenOnToggled)
                }
                SettingsPage.SOUND -> SoundSection()
                SettingsPage.CAR -> CarSection()
                SettingsPage.LIBRARY -> {
                    LibrarySection(filterShortAudio, onFilterShortAudioToggled, filterRecordings, onFilterRecordingsToggled)
                    LyricsSection()
                    if (downloadManager != null) TagRepairCard(downloadManager, isOfflineOnlyMode, onLibraryChanged)
                }
                SettingsPage.DOWNLOADS -> {
                    val queued by DownloadQueue.items.collectAsState()
                    SettingsLinkCard(
                        icon = Icons.Default.Download,
                        title = "Download Queue",
                        subtitle = when (queued.size) {
                            0 -> "Nothing downloading"
                            1 -> "1 song"
                            else -> "${queued.size} songs"
                        },
                        onClick = onShowDownloads
                    )
                    DownloadsSection(isOfflineOnlyMode, onOfflineOnlyModeToggled)
                }
                SettingsPage.BACKUP -> BackupSection(librarySongs)
                SettingsPage.UPDATES -> UpdateAndStorageSections(onShowWhatsNew)
            }
        }
    }
}

@Composable
private fun ExitCard(onExitApp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Button(
                onClick = onExitApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Exit Application"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Exit Application",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

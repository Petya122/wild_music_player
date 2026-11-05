package com.wildmusicplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.ui.theme.ThemeMode
import com.wildmusicplayer.ui.theme.AccentColor

/** The Settings screen: one card per section, each in its own file (GeneralSettings, UpdateSettings, …). */
@Composable
fun SettingsScreen(
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
    onExitApp: () -> Unit,
    bottomPadding: Dp,
    librarySongs: List<Song>,
    isOfflineOnlyMode: Boolean = false,
    onOfflineOnlyModeToggled: (Boolean) -> Unit = {},
    downloadManager: YouTubeDownloadManager? = null,
    onLibraryChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppearanceSection(currentTheme, onThemeSelected, currentAccent, onAccentSelected)
        DisplaySection(keepScreenOn, onKeepScreenOnToggled)
        CarSection()
        LibrarySection(filterShortAudio, onFilterShortAudioToggled, filterRecordings, onFilterRecordingsToggled)
        SoundSection()
        BackupSection(librarySongs)
        UpdateAndStorageSections(onShowWhatsNew)
        DownloadsSection(isOfflineOnlyMode, onOfflineOnlyModeToggled)
        PermissionsAndRepairCards(downloadManager, isOfflineOnlyMode, onLibraryChanged)

        SettingsLinkCard(
            icon = Icons.Default.Info,
            title = "About & Credits",
            subtitle = "Wild Music Player • Developed by Petya122",
            onClick = onShowAbout
        )

        ExitCard(onExitApp)
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

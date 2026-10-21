package com.wildmusicplayer.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SONGS("Songs", Icons.Default.MusicNote),
    SEARCH("Search", Icons.Default.Search),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    DOWNLOADER("Download", Icons.Default.CloudDownload)
}

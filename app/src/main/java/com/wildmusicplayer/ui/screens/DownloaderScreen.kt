package com.wildmusicplayer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.wildmusicplayer.data.DownloadNetworkMode
import com.wildmusicplayer.data.DownloadPrecondition
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.service.SongDownloadService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.YouTubeTrack
import com.wildmusicplayer.service.MusicControllerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DownloaderSortOrder(val label: String, val emoji: String) {
    ORIGINAL("Original / Best", "🌟"),
    VIEWS("Most Viewed", "👁"),
    DEFAULT("Default", "⚡")
}

private fun calculateOriginalScore(track: YouTubeTrack, query: String): Long {
    var score = track.viewCount.coerceAtLeast(0) / 1000

    val titleLower = track.title.lowercase()
    val uploaderLower = track.uploaderName.lowercase()
    val queryLower = query.lowercase().trim()

    // Title match bonus
    if (queryLower.isNotBlank() && titleLower.contains(queryLower)) {
        score += 300_000
    }

    // Strong signals of official/original releases
    if (track.isUploaderVerified) {
        score += 500_000
    }
    if (uploaderLower.endsWith(" - topic") || uploaderLower.contains("vevo") || uploaderLower.contains("official")) {
        score += 400_000
    }
    if (titleLower.contains("official video") || titleLower.contains("official music video") || titleLower.contains("official audio")) {
        score += 250_000
    }
    if (titleLower.contains("original mix") || titleLower.contains("radio edit")) {
        score += 150_000
    }

    // Penalize derivative/loop/reaction content
    if (titleLower.contains("nightcore") || titleLower.contains("8d audio") || titleLower.contains("bass boosted") ||
        titleLower.contains("1 hour") || titleLower.contains("10 hour") || titleLower.contains("10 hours") ||
        titleLower.contains("reaction") || titleLower.contains("earrape")) {
        score -= 2_000_000
    }

    // Normal song duration (1.5 to 7 minutes)
    if (track.durationSeconds in 90..450) {
        score += 100_000
    } else if (track.durationSeconds > 900) {
        score -= 500_000
    }

    return score
}

@Composable
fun DownloaderScreen(
    downloadManager: YouTubeDownloadManager,
    controllerManager: MusicControllerManager,
    bottomPadding: Dp,
    onDownloadComplete: () -> Unit,
    isOfflineOnlyMode: Boolean = false,
    onSwitchToOnline: () -> Unit = {},
    playlistRepository: PlaylistRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<YouTubeTrack>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var activeSortOrder by remember { mutableStateOf(DownloaderSortOrder.ORIGINAL) }

    var searchHistory by remember { mutableStateOf(downloadManager.getSearchHistory()) }

    // Track download state: trackId -> progress (0.0 to 1.0)
    val activeDownloads = remember { mutableStateMapOf<String, Float>() }
    val completedDownloads = remember { mutableStateMapOf<String, Boolean>() }

    val serviceActiveDownloads by SongDownloadService.activeDownloads.collectAsState()
    val serviceCompletedTracks by SongDownloadService.completedTrackIds.collectAsState()

    val networkPolicyManager = remember { NetworkPolicyManager(context) }
    var trackPendingMobileConfirmation by remember { mutableStateOf<YouTubeTrack?>(null) }
    var trackForPlaylistDialog by remember { mutableStateOf<YouTubeTrack?>(null) }

    // State for interactive song card action dialog
    var selectedTrackForAction by remember { mutableStateOf<YouTubeTrack?>(null) }
    var isPreparingStream by remember { mutableStateOf(false) }

    // Sorted results based on activeSortOrder
    val sortedResults = remember(searchResults, activeSortOrder, searchQuery) {
        when (activeSortOrder) {
            DownloaderSortOrder.ORIGINAL -> {
                searchResults.sortedByDescending { calculateOriginalScore(it, searchQuery) }
            }
            DownloaderSortOrder.VIEWS -> {
                searchResults.sortedByDescending { it.viewCount }
            }
            DownloaderSortOrder.DEFAULT -> {
                searchResults
            }
        }
    }

    // Direct search execution
    val executeSearch = { query: String ->
        if (isOfflineOnlyMode) {
            Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to search.", Toast.LENGTH_SHORT).show()
        } else {
            val trimmed = query.trim()
            if (trimmed.isNotBlank()) {
                focusManager.clearFocus()
                isSearching = true
                searchError = null
                downloadManager.addSearchHistory(trimmed)
                searchHistory = downloadManager.getSearchHistory()

                scope.launch {
                    try {
                        val results = downloadManager.search(trimmed)
                        searchResults = results
                        if (results.isEmpty()) {
                            searchError = "No songs found for '$trimmed'. Try another search term or paste a link."
                        }
                    } catch (e: Exception) {
                        searchError = "Search failed: ${e.localizedMessage ?: "Unknown error"}"
                    } finally {
                        isSearching = false
                    }
                }
            }
        }
    }

    // Dynamic debounced search & instant clearing
    LaunchedEffect(searchQuery, isOfflineOnlyMode) {
        if (isOfflineOnlyMode) {
            return@LaunchedEffect
        }
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            searchError = null
            isSearching = false
            return@LaunchedEffect
        }

        // Wait 600ms after last keystroke to avoid UI lag while typing
        delay(600)
        isSearching = true
        searchError = null
        try {
            val results = downloadManager.search(trimmed)
            searchResults = results
            if (results.isEmpty()) {
                searchError = "No songs found for '$trimmed'. Try another search term or paste a link."
            } else {
                downloadManager.addSearchHistory(trimmed)
                searchHistory = downloadManager.getSearchHistory()
            }
        } catch (e: Exception) {
            searchError = "Search failed: ${e.localizedMessage ?: "Unknown error"}"
        } finally {
            isSearching = false
        }
    }

    val performDownload: (YouTubeTrack) -> Unit = { track ->
        if (networkPolicyManager.backgroundDownloads) {
            SongDownloadService.startDownload(context, track)
            Toast.makeText(context, "Downloading in background: ${track.title}", Toast.LENGTH_SHORT).show()
        } else {
            val progress = activeDownloads[track.id]
            if (progress == null) {
                activeDownloads[track.id] = 0f
                scope.launch {
                    val result = downloadManager.downloadTrack(track) { p ->
                        activeDownloads[track.id] = p
                    }
                    activeDownloads.remove(track.id)
                    if (result.isSuccess) {
                        completedDownloads[track.id] = true
                        Toast.makeText(
                            context,
                            "Downloaded: ${track.title}",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDownloadComplete()
                    } else {
                        Toast.makeText(
                            context,
                            "Download failed: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    val triggerDownload: (YouTubeTrack) -> Unit = { track ->
        when (networkPolicyManager.checkDownloadPrecondition()) {
            is DownloadPrecondition.Allowed -> {
                performDownload(track)
            }
            is DownloadPrecondition.PromptMobileData -> {
                trackPendingMobileConfirmation = track
            }
            is DownloadPrecondition.BlockedNoNetwork -> {
                Toast.makeText(context, "No internet connection available.", Toast.LENGTH_SHORT).show()
            }
            is DownloadPrecondition.BlockedMetered -> {
                Toast.makeText(context, "Downloads blocked on metered network (check Settings).", Toast.LENGTH_SHORT).show()
            }
            is DownloadPrecondition.BlockedOfflineMode -> {
                Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to download.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding)
    ) {
        if (isOfflineOnlyMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Only Mode Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Online searching and stream previews are paused to save mobile data and battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSwitchToOnline,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Go Online", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text(if (isOfflineOnlyMode) "Offline Mode active (tap Go Online to search)" else "Search song or paste YouTube link...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        searchResults = emptyList()
                        searchError = null
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) })
        )

        // Sort Bar when search results are available
        if (searchResults.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sort:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DownloaderSortOrder.entries.forEach { order ->
                    FilterChip(
                        selected = activeSortOrder == order,
                        onClick = { activeSortOrder = order },
                        label = {
                            Text(
                                text = "${order.emoji} ${order.label}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching songs...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (searchError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = searchError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (searchResults.isEmpty()) {
            // Empty state placeholder with Recent Searches history
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = {
                                    downloadManager.clearSearchHistory()
                                    searchHistory = emptyList()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            searchHistory.forEach { historyItem ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        searchQuery = historyItem
                                        executeSearch(historyItem)
                                    },
                                    label = {
                                        Text(
                                            text = historyItem,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    downloadManager.removeSearchHistory(historyItem)
                                                    searchHistory = downloadManager.getSearchHistory()
                                                },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Save Music for Offline Play",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Search songs, artists, or paste a link from YouTube / YouTube Music. Tracks are saved directly to your phone's Music folder in high quality (.m4a) for 100% offline playback anytime.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // Results list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedResults, key = { it.id }) { track ->
                    val progress = activeDownloads[track.id] ?: serviceActiveDownloads[track.id]
                    val isDownloaded = completedDownloads[track.id] == true || serviceCompletedTracks.contains(track.id)

                    YouTubeTrackItem(
                        track = track,
                        downloadProgress = progress,
                        isDownloaded = isDownloaded,
                        onCardClick = {
                            selectedTrackForAction = track
                        },
                        onDownloadClick = {
                            triggerDownload(track)
                        }
                    )
                }
            }
        }
    }

    // Track Action Dialog when song card is clicked
    selectedTrackForAction?.let { track ->
        val progress = activeDownloads[track.id] ?: serviceActiveDownloads[track.id]
        val isDownloaded = completedDownloads[track.id] == true || serviceCompletedTracks.contains(track.id)

        TrackActionDialog(
            track = track,
            downloadManager = downloadManager,
            isDownloaded = isDownloaded,
            isDownloading = progress != null,
            isPreparingStream = isPreparingStream,
            onDismiss = {
                if (!isPreparingStream) {
                    selectedTrackForAction = null
                }
            },
            onPlayOnline = {
                if (isOfflineOnlyMode) {
                    Toast.makeText(context, "Offline Only Mode is active. Switch to Online Mode to stream.", Toast.LENGTH_SHORT).show()
                } else {
                    isPreparingStream = true
                    scope.launch {
                        val streamUrl = downloadManager.getStreamUrl(track)
                        isPreparingStream = false
                        if (!streamUrl.isNullOrBlank()) {
                            controllerManager.playOnlineStream(
                                title = track.title,
                                artist = track.uploaderName,
                                streamUrl = streamUrl,
                                artworkUrl = track.thumbnailUrl
                            )
                            selectedTrackForAction = null
                            Toast.makeText(context, "Streaming: ${track.title}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Unable to extract audio stream for preview", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDownload = {
                triggerDownload(track)
                selectedTrackForAction = null
            },
            onAddToPlaylist = {
                trackForPlaylistDialog = track
                selectedTrackForAction = null
            },
            onShare = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.webUrl}")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                selectedTrackForAction = null
            },
            onOpenInBrowser = {
                try {
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(track.webUrl))
                    context.startActivity(viewIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                selectedTrackForAction = null
            }
        )
    }

    // Add to Playlist Dialog
    trackForPlaylistDialog?.let { track ->
        if (playlistRepository != null) {
            AddToPlaylistDialog(
                track = track,
                playlistRepository = playlistRepository,
                onDismiss = { trackForPlaylistDialog = null }
            )
        }
    }

    // Confirmation dialog before downloading on mobile data or metered hotspot
    if (trackPendingMobileConfirmation != null) {
        val track = trackPendingMobileConfirmation!!
        AlertDialog(
            onDismissRequest = { trackPendingMobileConfirmation = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Download on Mobile Data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You are currently connected to mobile network.\n\nDownloading \"${track.title}\" will consume cellular data (~5–15 MB).\n\nWould you like to proceed?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = track
                        trackPendingMobileConfirmation = null
                        performDownload(t)
                    }
                ) {
                    Text("Download Once")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val t = track
                            networkPolicyManager.downloadNetworkMode = DownloadNetworkMode.ALWAYS_ALLOW
                            trackPendingMobileConfirmation = null
                            performDownload(t)
                            Toast.makeText(context, "Network mode set to Always Allow in Settings", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Always Allow")
                    }
                    TextButton(
                        onClick = { trackPendingMobileConfirmation = null }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun TrackActionDialog(
    track: YouTubeTrack,
    downloadManager: YouTubeDownloadManager,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPreparingStream: Boolean,
    onDismiss: () -> Unit,
    onPlayOnline: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    var detailedTrack by remember(track.id) { mutableStateOf(track) }

    LaunchedEffect(track.id) {
        if (track.likeCount < 0) {
            val updated = downloadManager.fetchTrackDetails(track)
            detailedTrack = updated
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // High-resolution Thumbnail with Duration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!detailedTrack.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(detailedTrack.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (detailedTrack.durationSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = detailedTrack.formattedDuration,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title & Channel
                Text(
                    text = detailedTrack.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = detailedTrack.uploaderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detailedTrack.isUploaderVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Channel",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Stats badges: Views & Likes
                if (detailedTrack.formattedViews != null || detailedTrack.formattedLikes != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        detailedTrack.formattedViews?.let { views ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = views,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        detailedTrack.formattedLikes?.let { likes ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = likes,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action: Play / Preview Online
                Button(
                    onClick = onPlayOnline,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isPreparingStream
                ) {
                    if (isPreparingStream) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extracting Stream...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play / Preview Online")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action: Download
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isDownloading
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            isDownloading -> "Downloading..."
                            isDownloaded -> "Downloaded (Tap to save again)"
                            else -> "Download Offline (.m4a)"
                        }
                    )
                }

                // Action: Add to Playlist
                OutlinedButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Playlist")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action: Share & Open in Browser
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    OutlinedButton(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun YouTubeTrackItem(
    track: YouTubeTrack,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    onCardClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Duration pill
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!track.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Duration badge in bottom end
                if (track.durationSeconds > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = track.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata Title & Uploader
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isUploaderVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Channel",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Views and Likes Stats Row
                if (track.formattedViews != null || track.formattedLikes != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        track.formattedViews?.let { views ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = views,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }

                        track.formattedLikes?.let { likes ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = likes,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                if (downloadProgress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action: Download Button or Status
            when {
                downloadProgress != null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isDownloaded -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                else -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Song",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: YouTubeTrack,
    playlistRepository: PlaylistRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customPlaylists by playlistRepository.customPlaylists.collectAsState()
    var showCreateField by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "Add to Playlist", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (showCreateField) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Playlist name...") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCreateField = false
                            newPlaylistName = ""
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val trimmed = newPlaylistName.trim()
                                if (trimmed.isNotBlank()) {
                                    val onlineTrack = OnlineTrack(
                                        id = track.id,
                                        title = track.title,
                                        artist = track.uploaderName,
                                        durationSeconds = track.durationSeconds,
                                        thumbnailUrl = track.thumbnailUrl,
                                        webUrl = track.webUrl
                                    )
                                    playlistRepository.createPlaylist(
                                        name = trimmed,
                                        onlineTracks = listOf(onlineTrack)
                                    )
                                    Toast.makeText(context, "Created '$trimmed' with track", Toast.LENGTH_SHORT).show()
                                    showCreateField = false
                                    newPlaylistName = ""
                                }
                            },
                            enabled = newPlaylistName.trim().isNotBlank()
                        ) {
                            Text("Create & Add")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    OutlinedButton(
                        onClick = { showCreateField = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Playlist")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (customPlaylists.isEmpty() && !showCreateField) {
                    Text(
                        text = "No custom playlists yet. Create one above!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(customPlaylists, key = { it.id }) { playlist ->
                            val isInPlaylist = playlistRepository.isOnlineTrackInPlaylist(playlist.id, track.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val onlineTrack = OnlineTrack(
                                            id = track.id,
                                            title = track.title,
                                            artist = track.uploaderName,
                                            durationSeconds = track.durationSeconds,
                                            thumbnailUrl = track.thumbnailUrl,
                                            webUrl = track.webUrl
                                        )
                                        val added = playlistRepository.toggleOnlineTrackInPlaylist(playlist.id, onlineTrack)
                                        val msg = if (added) {
                                            "Added to '${playlist.name}'"
                                        } else {
                                            "Removed from '${playlist.name}'"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isInPlaylist) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.songIds.size + playlist.onlineTracks.size} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isInPlaylist) Icons.Default.CheckCircle else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isInPlaylist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

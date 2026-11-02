package com.wildmusicplayer.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wildmusicplayer.data.RepairCandidate
import com.wildmusicplayer.data.TagRepairManager
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.YouTubeTrack
import kotlinx.coroutines.launch

private sealed interface RepairState {
    data object Searching : RepairState
    data object NoMatch : RepairState
    data class Ready(val matches: List<YouTubeTrack>, val index: Int) : RepairState {
        val current: YouTubeTrack get() = matches[index]
    }
    data object Applying : RepairState
    data class Done(val match: YouTubeTrack) : RepairState
    data object Skipped : RepairState
    data object Failed : RepairState
}

/**
 * Full-screen tool that finds downloads without artist/cover, suggests the YouTube Music song
 * for each, and writes the tags into the file. [onDismiss] reports whether any file changed,
 * so the library can be reloaded.
 */
@Composable
fun TagRepairDialog(
    downloadManager: YouTubeDownloadManager,
    isOfflineOnlyMode: Boolean,
    onDismiss: (changed: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { TagRepairManager(context.applicationContext, downloadManager) }

    var candidates by remember { mutableStateOf<List<RepairCandidate>?>(null) }
    val states = remember { mutableStateMapOf<Long, RepairState>() }
    var anyChanged by remember { mutableStateOf(false) }
    var writeAllowed by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<List<Pair<RepairCandidate, YouTubeTrack>>>(emptyList()) }

    val runApply: (List<Pair<RepairCandidate, YouTubeTrack>>) -> Unit = { items ->
        items.forEach { (candidate, _) -> states[candidate.id] = RepairState.Applying }
        scope.launch {
            for ((candidate, match) in items) {
                val ok = manager.applyMatch(candidate, match)
                states[candidate.id] = if (ok) RepairState.Done(match) else RepairState.Failed
                if (ok) anyChanged = true
            }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            writeAllowed = true
            runApply(pendingApply)
        } else {
            Toast.makeText(context, "Permission is needed to update these songs.", Toast.LENGTH_SHORT).show()
        }
        pendingApply = emptyList()
    }

    // Ask once for edit access to files the app didn't create itself (e.g. after a reinstall)
    val requestApply: (List<Pair<RepairCandidate, YouTubeTrack>>) -> Unit = { items ->
        val sender = if (writeAllowed) null else manager.writePermissionRequest(candidates.orEmpty())
        if (sender != null) {
            pendingApply = items
            writePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            writeAllowed = true
            runApply(items)
        }
    }

    LaunchedEffect(Unit) {
        val found = manager.findUntaggedDownloads()
        candidates = found
        if (isOfflineOnlyMode) return@LaunchedEffect
        found.forEach { states[it.id] = RepairState.Searching }
        // One lookup at a time keeps it gentle on the network
        for (candidate in found) {
            val matches = try {
                manager.findMatches(candidate)
            } catch (e: Exception) {
                emptyList()
            }
            if (states[candidate.id] == RepairState.Searching) {
                states[candidate.id] = if (matches.isEmpty()) RepairState.NoMatch else RepairState.Ready(matches, 0)
            }
        }
    }

    Dialog(
        onDismissRequest = { onDismiss(anyChanged) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDismiss(anyChanged) }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        text = "Fix Song Tags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    val readyItems = candidates.orEmpty().mapNotNull { candidate ->
                        (states[candidate.id] as? RepairState.Ready)?.let { candidate to it.current }
                    }
                    TextButton(
                        onClick = { requestApply(readyItems) },
                        enabled = readyItems.isNotEmpty()
                    ) {
                        Text("Accept All")
                    }
                }

                Text(
                    text = "Songs downloaded before v0.6.0 have no artist or cover. Check each match from YouTube Music, then tap Use this. The song stays in your playlists and favorites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val list = candidates
                when {
                    list == null -> CenteredMessage(loading = true, text = "Looking for downloads…")
                    list.isEmpty() -> CenteredMessage(text = "All your downloads already have their artist and cover. 🎉")
                    isOfflineOnlyMode -> CenteredMessage(
                        text = "${list.size} songs need tags. Switch to Online Mode to look them up on YouTube Music."
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list, key = { it.id }) { candidate ->
                            RepairRow(
                                candidate = candidate,
                                state = states[candidate.id] ?: RepairState.Searching,
                                onUse = { match -> requestApply(listOf(candidate to match)) },
                                onNextMatch = {
                                    (states[candidate.id] as? RepairState.Ready)?.let { ready ->
                                        states[candidate.id] = ready.copy(index = (ready.index + 1) % ready.matches.size)
                                    }
                                },
                                onSkip = { states[candidate.id] = RepairState.Skipped }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairRow(
    candidate: RepairCandidate,
    state: RepairState,
    onUse: (YouTubeTrack) -> Unit,
    onNextMatch: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                RepairState.Searching -> StatusLine(loading = true, text = "Searching YouTube Music…")
                RepairState.NoMatch -> StatusLine(text = "No match found on YouTube Music.")
                RepairState.Applying -> StatusLine(loading = true, text = "Saving tags…")
                RepairState.Skipped -> StatusLine(text = "Skipped")
                RepairState.Failed -> StatusLine(text = "Couldn't update this file.")
                is RepairState.Done -> StatusLine(
                    text = "✓ Fixed: ${state.match.uploaderName} – ${state.match.title}",
                    color = MaterialTheme.colorScheme.primary
                )
                is RepairState.Ready -> {
                    val match = state.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = match.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = match.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${match.uploaderName} • ${match.formattedDuration}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onUse(match) }) { Text("Use this") }
                        if (state.matches.size > 1) {
                            OutlinedButton(onClick = onNextMatch) {
                                Text("Next (${state.index + 1}/${state.matches.size})")
                            }
                        }
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, loading: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CenteredMessage(text: String, loading: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.wildmusicplayer.ui.screens

import com.wildmusicplayer.ui.components.SongThumbnail
import com.wildmusicplayer.ui.PlatformBackHandler
import com.wildmusicplayer.data.artistsOf
import com.wildmusicplayer.data.albumsOf
import com.wildmusicplayer.data.SongGroup
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.model.Song

enum class SearchFilter(val label: String) {
    ALL("All"),
    TITLE("Song Title"),
    ARTIST("Artist"),
    ALBUM("Album")
}

@Composable
fun SearchScreen(
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    currentPlayingSongId: Long?,
    bottomPadding: Dp,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteSong: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    // Plays a whole artist or album found by the search
    onPlayGroup: (List<Song>, shuffle: Boolean) -> Unit = { list, _ -> list.firstOrNull()?.let { onSongClick(it, list) } }
) {
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var openGroup by remember { mutableStateOf<SongGroup?>(null) }

    // Artists and albums whose name matches, shown above the songs
    val matchingArtists = remember(songs, query, activeFilter) {
        if (query.isBlank() || activeFilter !in setOf(SearchFilter.ALL, SearchFilter.ARTIST)) emptyList()
        else artistsOf(songs).filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val matchingAlbums = remember(songs, query, activeFilter) {
        if (query.isBlank() || activeFilter !in setOf(SearchFilter.ALL, SearchFilter.ALBUM)) emptyList()
        else albumsOf(songs).filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    // Back from an artist or album returns to the results
    PlatformBackHandler(enabled = openGroup != null) { openGroup = null }
    openGroup?.let { group ->
        SongGroupDetail(
            group = group,
            favoriteSongIds = favoriteSongIds,
            currentPlayingSongId = currentPlayingSongId,
            bottomPadding = bottomPadding,
            onBack = { openGroup = null },
            onSongClick = { song -> onSongClick(song, group.songs) },
            onPlay = { shuffle -> onPlayGroup(group.songs, shuffle) },
            onToggleFavorite = onToggleFavorite,
            onDeleteSong = onDeleteSong,
            onAddToPlaylist = onAddToPlaylist
        )
        return
    }

    val filteredSongs = remember(songs, query, activeFilter) {
        if (query.isBlank()) {
            emptyList()
        } else {
            songs.filter { song ->
                when (activeFilter) {
                    SearchFilter.ALL ->
                        song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
                    SearchFilter.TITLE ->
                        song.title.contains(query, ignoreCase = true)
                    SearchFilter.ARTIST ->
                        song.artist.contains(query, ignoreCase = true)
                    SearchFilter.ALBUM ->
                        song.album.contains(query, ignoreCase = true)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Search text field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Filter chips row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchFilter.entries) { filter ->
                FilterChip(
                    selected = (filter == activeFilter),
                    onClick = { activeFilter = filter },
                    label = { Text(filter.label) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Search Results List
        if (query.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Type to search your music library",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Search across ${songs.size} local tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else if (filteredSongs.isEmpty() && matchingArtists.isEmpty() && matchingAlbums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tracks found matching \"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                if (matchingArtists.isNotEmpty()) {
                    item(key = "artists") {
                        GroupRow("Artists", matchingArtists, round = true) { openGroup = it }
                    }
                }
                if (matchingAlbums.isNotEmpty()) {
                    item(key = "albums") {
                        GroupRow("Albums", matchingAlbums, round = false) { openGroup = it }
                    }
                }
                if (filteredSongs.isNotEmpty()) item(key = "songs-header") {
                    Text(
                        text = "Found ${filteredSongs.size} matching ${if (filteredSongs.size == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                items(filteredSongs, key = { it.id }) { song ->
                    val isCurrent = song.id == currentPlayingSongId
                    val isFavorite = favoriteSongIds.contains(song.id)

                    SongRowItem(
                        song = song,
                        isCurrent = isCurrent,
                        isFavorite = isFavorite,
                        onClick = { onSongClick(song, filteredSongs) },
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onDelete = onDeleteSong?.let { delete -> { delete(song) } },
                        onAddToPlaylist = onAddToPlaylist?.let { add -> { add(song) } }
                    )
                }
            }
        }
    }
}

/** A titled, sideways-scrolling row of artists (round pictures) or albums (covers). */
@Composable
private fun GroupRow(title: String, groups: List<SongGroup>, round: Boolean, onOpen: (SongGroup) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$title (${groups.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(groups, key = { it.key }) { group ->
                Column(
                    modifier = Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(group) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SongThumbnail(
                        albumArtUri = group.artUri,
                        modifier = Modifier.size(100.dp),
                        shape = if (round) CircleShape else RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (round) "${group.songs.size} ${if (group.songs.size == 1) "song" else "songs"}" else group.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

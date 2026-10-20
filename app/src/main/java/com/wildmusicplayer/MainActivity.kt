package com.wildmusicplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wildmusicplayer.BuildConfig
import com.wildmusicplayer.data.AudioRepository
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.service.MusicControllerManager
import com.wildmusicplayer.ui.components.DraggablePlayerSheet
import com.wildmusicplayer.ui.components.SettingsDialog
import com.wildmusicplayer.ui.components.SongThumbnail
import com.wildmusicplayer.ui.components.SplashScreen
import com.wildmusicplayer.ui.components.WhatsNewDialog
import com.wildmusicplayer.ui.theme.ThemeMode
import com.wildmusicplayer.ui.theme.WildMusicPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "wild_music_player_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_LAST_SEEN_VERSION = "last_seen_version"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

class MainActivity : ComponentActivity() {

    private lateinit var controllerManager: MusicControllerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controllerManager = MusicControllerManager(applicationContext)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialTheme = try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        setContent {
            var currentTheme by remember { mutableStateOf(initialTheme) }

            WildMusicPlayerTheme(themeMode = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerApp(
                        controllerManager = controllerManager,
                        currentTheme = currentTheme,
                        onThemeChanged = { newTheme ->
                            currentTheme = newTheme
                            prefs.edit().putString(KEY_THEME_MODE, newTheme.name).apply()
                        },
                        onExitApp = {
                            controllerManager.stopAndShutdown()
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerManager.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(
    controllerManager: MusicControllerManager,
    currentTheme: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRepository = remember { AudioRepository(context) }

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSplashVisible by remember { mutableStateOf(true) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var keepScreenOn by remember {
        mutableStateOf(prefs.getBoolean(KEY_KEEP_SCREEN_ON, false))
    }

    val activity = context as? ComponentActivity
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            scope.launch {
                songs = audioRepository.getLocalSongs()
            }
        }
    }

    LaunchedEffect(Unit) {
        val lastSeenVersion = prefs.getString(KEY_LAST_SEEN_VERSION, null)
        val currentVersion = BuildConfig.VERSION_NAME
        if (lastSeenVersion != null && lastSeenVersion != currentVersion) {
            showWhatsNewDialog = true
        }
        prefs.edit().putString(KEY_LAST_SEEN_VERSION, currentVersion).apply()

        val granted = ContextCompat.checkSelfPermission(
            context,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted) {
            songs = audioRepository.getLocalSongs()
        } else {
            permissionLauncher.launch(requiredPermission)
        }

        // Keep branded splash screen visible for a minimum of 1.8 seconds
        delay(1800)
        isSplashVisible = false
    }

    val currentSong by controllerManager.currentSong.collectAsState()
    val isPlaying by controllerManager.isPlaying.collectAsState()
    val currentPosition by controllerManager.currentPosition.collectAsState()
    val duration by controllerManager.duration.collectAsState()
    val isShuffleEnabled by controllerManager.isShuffleEnabled.collectAsState()
    val repeatMode by controllerManager.repeatMode.collectAsState()

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    // Collapse player on back press if expanded
    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Wild Music Player",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!hasPermission) {
                    PermissionRequestView(onRequestPermission = {
                        permissionLauncher.launch(requiredPermission)
                    })
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search songs, artists, albums...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear Search"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (songs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No songs found on device storage.\nTransfer some MP3s to get started!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (filteredSongs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No songs match \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            SongListView(
                                songs = filteredSongs,
                                currentPlayingSongId = currentSong?.id,
                                bottomPadding = if (currentSong != null) 90.dp + bottomNavPadding else bottomNavPadding,
                                onSongClick = { selectedSong ->
                                    controllerManager.playSong(selectedSong, songs)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Interactive 1:1 Draggable Player Sheet (YouTube Music style)
        if (currentSong != null) {
            DraggablePlayerSheet(
                song = currentSong!!,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                onTogglePlay = { controllerManager.togglePlayPause() },
                onSkipNext = { controllerManager.skipToNext() },
                onSkipPrevious = { controllerManager.skipToPrevious() },
                onSeekTo = { controllerManager.seekTo(it) },
                onToggleShuffle = { controllerManager.toggleShuffle() },
                onToggleRepeat = { controllerManager.toggleRepeatMode() },
                onStop = {
                    isPlayerExpanded = false
                    controllerManager.stopPlayback()
                },
                isExpanded = isPlayerExpanded,
                onExpandedChange = { isPlayerExpanded = it }
            )
        }

        // Settings Dialog Modal
        if (showSettingsDialog) {
            SettingsDialog(
                currentTheme = currentTheme,
                onThemeSelected = { newTheme ->
                    onThemeChanged(newTheme)
                },
                keepScreenOn = keepScreenOn,
                onKeepScreenOnToggled = { enabled ->
                    keepScreenOn = enabled
                    prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
                },
                onExitApp = {
                    showSettingsDialog = false
                    onExitApp()
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // What's New Dialog Modal after app updates
        if (showWhatsNewDialog) {
            WhatsNewDialog(
                versionName = BuildConfig.VERSION_NAME,
                onDismiss = { showWhatsNewDialog = false }
            )
        }

        // Full-screen Animated Splash Screen with smooth fade-out
        AnimatedVisibility(
            visible = isSplashVisible,
            exit = fadeOut(animationSpec = tween(600))
        ) {
            SplashScreen()
        }
    }
}

@Composable
fun SongListView(
    songs: List<Song>,
    currentPlayingSongId: Long?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSongClick: (Song) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "${songs.size} tracks available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(songs, key = { it.id }) { song ->
            SongListItem(
                song = song,
                isCurrent = song.id == currentPlayingSongId,
                onClick = { onSongClick(song) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongThumbnail(
            albumArtUri = song.albumArtUri,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wild Music Player needs permission to read music files from your device to play offline songs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(text = "Grant Permission")
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

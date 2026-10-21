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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.service.MusicControllerManager
import com.wildmusicplayer.ui.components.AboutDialog
import com.wildmusicplayer.ui.components.DraggablePlayerSheet
import com.wildmusicplayer.ui.components.SongThumbnail
import com.wildmusicplayer.ui.components.SplashScreen
import com.wildmusicplayer.ui.components.WhatsNewDialog
import com.wildmusicplayer.ui.screens.DownloaderScreen
import com.wildmusicplayer.ui.screens.HomeScreen
import com.wildmusicplayer.ui.screens.NavigationTab
import com.wildmusicplayer.ui.screens.PlaylistsScreen
import com.wildmusicplayer.ui.screens.SearchScreen
import com.wildmusicplayer.ui.screens.SettingsScreen
import com.wildmusicplayer.ui.screens.SongsScreen
import com.wildmusicplayer.ui.theme.ThemeMode
import com.wildmusicplayer.ui.theme.WildMusicPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "wild_music_player_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_LAST_SEEN_VERSION = "last_seen_version"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
private const val KEY_FILTER_SHORT_AUDIO = "key_filter_short_audio"
private const val KEY_FILTER_RECORDINGS = "key_filter_recordings"

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
    val downloadManager = remember { YouTubeDownloadManager(context) }

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isSplashVisible by remember { mutableStateOf(true) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var keepScreenOn by remember {
        mutableStateOf(prefs.getBoolean(KEY_KEEP_SCREEN_ON, false))
    }
    var filterShortAudio by remember {
        mutableStateOf(prefs.getBoolean(KEY_FILTER_SHORT_AUDIO, true))
    }
    var filterRecordings by remember {
        mutableStateOf(prefs.getBoolean(KEY_FILTER_RECORDINGS, true))
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
                songs = audioRepository.getLocalSongs(filterShortAudio, filterRecordings)
            }
        }
    }

    LaunchedEffect(filterShortAudio, filterRecordings) {
        if (hasPermission) {
            songs = audioRepository.getLocalSongs(filterShortAudio, filterRecordings)
        }
    }

    LaunchedEffect(Unit) {
        val lastSeenVersion = prefs.getString(KEY_LAST_SEEN_VERSION, null)
        val currentVersion = BuildConfig.VERSION_NAME
        val isNewVersion = lastSeenVersion != null && lastSeenVersion != currentVersion
        prefs.edit().putString(KEY_LAST_SEEN_VERSION, currentVersion).apply()

        val granted = ContextCompat.checkSelfPermission(
            context,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted) {
            songs = audioRepository.getLocalSongs(filterShortAudio, filterRecordings)
        } else {
            permissionLauncher.launch(requiredPermission)
        }

        // Keep branded splash screen visible for a minimum of 1.8 seconds
        delay(1800)
        isSplashVisible = false

        // Wait for splash screen fade-out animation to complete before popping up the update log dialog
        if (isNewVersion) {
            delay(600)
            showWhatsNewDialog = true
        }
    }

    val playlistRepository = remember { PlaylistRepository(context) }
    val favoriteSongIds by playlistRepository.favoriteSongIds.collectAsState()
    val recentlyPlayedIds by playlistRepository.recentlyPlayedIds.collectAsState()
    val customPlaylists by playlistRepository.customPlaylists.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }

    val recentlyPlayedSongs = remember(songs, recentlyPlayedIds) {
        recentlyPlayedIds.mapNotNull { id -> songs.find { it.id == id } }
    }
    val favoriteSongs = remember(songs, favoriteSongIds) {
        songs.filter { favoriteSongIds.contains(it.id) }
    }

    val playSong: (Song, List<Song>) -> Unit = { song, playlist ->
        playlistRepository.addRecentlyPlayed(song.id)
        controllerManager.playSong(song, playlist)
    }

    val shuffleAll: () -> Unit = {
        if (songs.isNotEmpty()) {
            val shuffled = songs.shuffled()
            playlistRepository.addRecentlyPlayed(shuffled.first().id)
            controllerManager.playSong(shuffled.first(), shuffled)
        }
    }

    val playPlaylist: (List<Song>, Boolean) -> Unit = { list, shuffle ->
        if (list.isNotEmpty()) {
            val targetList = if (shuffle) list.shuffled() else list
            playlistRepository.addRecentlyPlayed(targetList.first().id)
            controllerManager.playSong(targetList.first(), targetList)
        }
    }

    val currentSong by controllerManager.currentSong.collectAsState()
    val isPlaying by controllerManager.isPlaying.collectAsState()
    val currentPosition by controllerManager.currentPosition.collectAsState()
    val duration by controllerManager.duration.collectAsState()
    val isShuffleEnabled by controllerManager.isShuffleEnabled.collectAsState()
    val repeatMode by controllerManager.repeatMode.collectAsState()

    // Collapse player on back press if expanded, return from settings, or return to Home tab
    BackHandler(enabled = isPlayerExpanded || isSettingsOpen || currentTab != NavigationTab.HOME) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (currentTab != NavigationTab.HOME) {
            currentTab = NavigationTab.HOME
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isSettingsOpen) {
                                "Settings"
                            } else {
                                when (currentTab) {
                                    NavigationTab.HOME -> "Wild Music Player"
                                    NavigationTab.SONGS -> "Songs"
                                    NavigationTab.SEARCH -> "Search"
                                    NavigationTab.PLAYLISTS -> "Playlists"
                                    NavigationTab.DOWNLOADER -> "Downloader"
                                }
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (isSettingsOpen) {
                            IconButton(onClick = { isSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSettingsOpen) {
                            IconButton(onClick = { showAboutDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "About & Credits",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = { isSettingsOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (hasPermission) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp
                    ) {
                        NavigationTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = (!isSettingsOpen && currentTab == tab),
                                onClick = {
                                    isSettingsOpen = false
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (!isSettingsOpen && currentTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
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
                    val contentBottomPadding = if (currentSong != null) 86.dp else 16.dp

                    if (isSettingsOpen) {
                        SettingsScreen(
                            currentTheme = currentTheme,
                            onThemeSelected = { newTheme ->
                                onThemeChanged(newTheme)
                            },
                            keepScreenOn = keepScreenOn,
                            onKeepScreenOnToggled = { enabled ->
                                keepScreenOn = enabled
                                prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
                            },
                            filterShortAudio = filterShortAudio,
                            onFilterShortAudioToggled = { enabled ->
                                filterShortAudio = enabled
                                prefs.edit().putBoolean(KEY_FILTER_SHORT_AUDIO, enabled).apply()
                            },
                            filterRecordings = filterRecordings,
                            onFilterRecordingsToggled = { enabled ->
                                filterRecordings = enabled
                                prefs.edit().putBoolean(KEY_FILTER_RECORDINGS, enabled).apply()
                            },
                            onShowWhatsNew = {
                                showWhatsNewDialog = true
                            },
                            onShowAbout = {
                                showAboutDialog = true
                            },
                            onExitApp = {
                                isSettingsOpen = false
                                onExitApp()
                            },
                            bottomPadding = contentBottomPadding
                        )
                    } else {
                        when (currentTab) {
                        NavigationTab.HOME -> {
                            HomeScreen(
                                allSongs = songs,
                                recentlyPlayedSongs = recentlyPlayedSongs,
                                favoriteSongs = favoriteSongs,
                                currentPlayingSongId = currentSong?.id,
                                bottomPadding = contentBottomPadding,
                                onSongClick = playSong,
                                onShuffleAll = shuffleAll,
                                onNavigateToSongs = { currentTab = NavigationTab.SONGS },
                                onNavigateToPlaylists = { currentTab = NavigationTab.PLAYLISTS },
                                onRemoveRecentSong = { songId ->
                                    playlistRepository.removeRecentlyPlayed(songId)
                                },
                                onClearRecentSongs = {
                                    playlistRepository.clearRecentlyPlayed()
                                }
                            )
                        }
                        NavigationTab.SONGS -> {
                            SongsScreen(
                                songs = songs,
                                favoriteSongIds = favoriteSongIds,
                                currentPlayingSongId = currentSong?.id,
                                bottomPadding = contentBottomPadding,
                                onSongClick = { selectedSong -> playSong(selectedSong, songs) },
                                onShuffleAll = shuffleAll,
                                onToggleFavorite = { playlistRepository.toggleFavorite(it) }
                            )
                        }
                        NavigationTab.SEARCH -> {
                            SearchScreen(
                                songs = songs,
                                favoriteSongIds = favoriteSongIds,
                                currentPlayingSongId = currentSong?.id,
                                bottomPadding = contentBottomPadding,
                                onSongClick = playSong,
                                onToggleFavorite = { playlistRepository.toggleFavorite(it) }
                            )
                        }
                        NavigationTab.PLAYLISTS -> {
                            PlaylistsScreen(
                                allSongs = songs,
                                favoriteSongIds = favoriteSongIds,
                                customPlaylists = customPlaylists,
                                currentPlayingSongId = currentSong?.id,
                                bottomPadding = contentBottomPadding,
                                onSongClick = playSong,
                                onPlayPlaylist = playPlaylist,
                                onToggleFavorite = { playlistRepository.toggleFavorite(it) },
                                onCreatePlaylist = { name, songIds -> playlistRepository.createPlaylist(name, songIds) },
                                onDeletePlaylist = { playlistRepository.deletePlaylist(it) }
                            )
                        }
                        NavigationTab.DOWNLOADER -> {
                            DownloaderScreen(
                                downloadManager = downloadManager,
                                bottomPadding = contentBottomPadding,
                                onDownloadComplete = {
                                    scope.launch {
                                        songs = audioRepository.getLocalSongs(filterShortAudio, filterRecordings)
                                    }
                                }
                            )
                        }
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
                isFavorite = favoriteSongIds.contains(currentSong!!.id),
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
                onToggleFavorite = { playlistRepository.toggleFavorite(it) },
                bottomBarHeight = 80.dp,
                isExpanded = isPlayerExpanded,
                onExpandedChange = { isPlayerExpanded = it }
            )
        }

        // About & Credits Dialog Modal
        if (showAboutDialog) {
            AboutDialog(
                onDismiss = { showAboutDialog = false }
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

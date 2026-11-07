package com.wildmusicplayer

import com.wildmusicplayer.ui.screens.SettingsPage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import com.wildmusicplayer.data.DownloadPrecondition
import com.wildmusicplayer.ui.components.DownloadQueueSheet
import com.wildmusicplayer.data.DownloadState
import com.wildmusicplayer.data.DownloadPause
import com.wildmusicplayer.data.DownloadQueue
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.MutableState
import com.wildmusicplayer.ui.LocalEditSong
import com.wildmusicplayer.ui.components.SongEdit
import com.wildmusicplayer.ui.components.EditSongDialog
import com.wildmusicplayer.data.SongEditor
import com.wildmusicplayer.data.PlayTarget
import android.content.Intent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.wildmusicplayer.ui.LocalPickTextFile
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.wildmusicplayer.ui.components.QueueSheet
import com.wildmusicplayer.ui.QueueActions
import com.wildmusicplayer.ui.LocalQueueActions
import com.wildmusicplayer.ui.LocalSleepTimer
import com.wildmusicplayer.ui.screens.rememberPlaylistExporter
import com.wildmusicplayer.ui.LocalLyrics
import com.wildmusicplayer.ui.LocalArtworkColors
import com.wildmusicplayer.ui.LocalSongInfo
import com.wildmusicplayer.data.AndroidSongInfo
import com.wildmusicplayer.ui.AndroidArtworkColors
import com.wildmusicplayer.data.lyricsRepository
import com.wildmusicplayer.service.SleepTimerManager
import com.wildmusicplayer.service.AndroidTrackDownloader
import com.wildmusicplayer.ui.LocalShowMessage
import com.wildmusicplayer.ui.LocalAudioEffects
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.CompositionLocalProvider
import com.wildmusicplayer.ui.LibraryViewModel
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.data.AppSettings
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.activity.viewModels
import android.view.WindowManager
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import com.wildmusicplayer.widget.PlayerWidget
import com.wildmusicplayer.widget.PlaylistWidget
import com.wildmusicplayer.ui.theme.AccentColor
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.ColorScheme
import com.wildmusicplayer.ui.LocalShowUndo
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.SideEffect
import androidx.activity.result.IntentSenderRequest
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
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.wildmusicplayer.data.AudioEffectsManager
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.SongDeleter
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.OnlineTrack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.wildmusicplayer.data.UpdateChecker
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.service.MusicControllerManager
import com.wildmusicplayer.service.LoudnessAnalysisWorker
import com.wildmusicplayer.service.SongDownloadService
import com.wildmusicplayer.ui.components.AboutDialog
import com.wildmusicplayer.ui.components.AddToPlaylistDialog
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

class MainActivity : ComponentActivity() {

    // Survives rotation; owns the connection to the playback service and releases it when cleared
    private val libraryViewModel: LibraryViewModel by viewModels {
        viewModelFactory {
            initializer {
                LibraryViewModel(
                    library = AudioRepository(applicationContext),
                    player = MusicControllerManager(applicationContext),
                    playlists = PlaylistRepository.getInstance(applicationContext),
                    downloadIndex = DownloadIndex.getInstance(applicationContext),
                    settings = AppSettings.getInstance(applicationContext)
                )
            }
        }
    }

    // The song whose details are being edited (opened from any song's menu)
    private val songToEdit = mutableStateOf<Song?>(null)

    // What an app icon shortcut asked to play (Shuffle all, Favorites, a playlist), until it's started
    private val shortcutTarget = mutableStateOf<String?>(null)

    // "Background Downloading" off: the download queue only runs while the app is on screen
    override fun onStart() {
        super.onStart()
        if (DownloadQueue.resume(DownloadPause.APP_IN_BACKGROUND)) SongDownloadService.startIfNeeded(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !NetworkPolicyManager.getInstance(this).backgroundDownloads) {
            DownloadQueue.pause(DownloadPause.APP_IN_BACKGROUND)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppShortcuts.targetOf(intent)?.let { shortcutTarget.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not again after a rotation: it already started then
        if (savedInstanceState == null) shortcutTarget.value = AppShortcuts.targetOf(intent)
        val controllerManager = libraryViewModel.player as MusicControllerManager
        val settings = libraryViewModel.settings

        setContent {
            val themeName by settings.themeMode.collectAsState()
            val currentTheme = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
            val accentName by settings.accentColor.collectAsState()
            val accent = AccentColor.entries.firstOrNull { it.name == accentName } ?: AccentColor.BLUE
            val themeContext = LocalContext.current
            // Wallpaper colors exist from Android 12 on
            val wallpaperScheme: ((Boolean) -> ColorScheme)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                { dark -> if (dark) dynamicDarkColorScheme(themeContext) else dynamicLightColorScheme(themeContext) }
            } else null

            val appContext = LocalContext.current.applicationContext
            // "Use my own .lrc file": Android's file picker, then the file's text (at most 512 KB)
            var onTextPicked by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val textScope = rememberCoroutineScope()
            val pickTextFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val deliver = onTextPicked
                onTextPicked = null
                if (uri == null || deliver == null) return@rememberLauncherForActivityResult
                textScope.launch {
                    val text = withContext(Dispatchers.IO) {
                        runCatching {
                            appContext.contentResolver.openInputStream(uri)?.use { input ->
                                // Bounded: picking a big file by mistake mustn't fill the memory
                                val buffer = ByteArray(512 * 1024)
                                var total = 0
                                while (total < buffer.size) {
                                    val read = input.read(buffer, total, buffer.size - total)
                                    if (read < 0) break
                                    total += read
                                }
                                buffer.decodeToString(0, total).removePrefix("\uFEFF")
                            }
                        }.getOrNull()
                    }
                    if (text.isNullOrBlank()) {
                        Toast.makeText(appContext, "Couldn't read that file", Toast.LENGTH_SHORT).show()
                    } else {
                        deliver(text)
                        Toast.makeText(appContext, "Lyrics file loaded for this song", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val undoHost = remember { SnackbarHostState() }
            val undoScope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalShowUndo provides { message, onUndo ->
                    undoScope.launch {
                        undoHost.currentSnackbarData?.dismiss()
                        // Long enough to react, also while driving
                        val result = undoHost.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) onUndo()
                    }
                },
                LocalShowMessage provides { message -> Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() },
                LocalAudioEffects provides AudioEffectsManager.getInstance(appContext),
                LocalQueueActions provides QueueActions(controllerManager::playNext, controllerManager::addToQueue),
                LocalSleepTimer provides SleepTimerManager,
                LocalLyrics provides lyricsRepository(appContext),
                LocalArtworkColors provides remember { AndroidArtworkColors(appContext) },
                LocalSongInfo provides remember { AndroidSongInfo(appContext, libraryViewModel.playlists) },
                LocalEditSong provides { song -> songToEdit.value = song },
                LocalPickTextFile provides { onPicked ->
                    onTextPicked = onPicked
                    pickTextFile.launch(arrayOf("*/*"))
                }
            ) {
            WildMusicPlayerTheme(themeMode = currentTheme, accent = accent, wallpaperScheme = wallpaperScheme) {
                // Status bar icons follow the app's own theme: dark icons on the light theme, even when
                // the phone itself is in dark mode (they were white on white before)
                val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerApp(
                        library = libraryViewModel,
                        controllerManager = controllerManager,
                        currentTheme = currentTheme,
                        currentAccent = accent,
                        onThemeChanged = { newTheme -> settings.setThemeMode(newTheme.name) },
                        undoHost = undoHost,
                        shortcutTarget = shortcutTarget.value,
                        editSongState = songToEdit,
                        onShortcutHandled = { shortcutTarget.value = null },
                        onExitApp = {
                            controllerManager.stopAndShutdown()
                            finishAffinity()
                        }
                    )
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(
    library: LibraryViewModel,
    controllerManager: MusicControllerManager,
    currentTheme: ThemeMode,
    currentAccent: AccentColor,
    onThemeChanged: (ThemeMode) -> Unit,
    undoHost: SnackbarHostState,
    shortcutTarget: String? = null,
    onShortcutHandled: () -> Unit = {},
    editSongState: MutableState<Song?> = remember { mutableStateOf(null) },
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { YouTubeDownloadManager(context) }
    val updateChecker = remember { UpdateChecker.getInstance(context) }
    val networkPolicyManager = remember { NetworkPolicyManager.getInstance(context) }
    val trackDownloader = remember { AndroidTrackDownloader(context.applicationContext) }

    val isOfflineOnlyMode by networkPolicyManager.offlineOnlyMode.collectAsState()
    val setOfflineMode: (Boolean) -> Unit = { enabled ->
        val waiting = DownloadQueue.items.value.count { it.state != DownloadState.FAILED }
        networkPolicyManager.isOfflineOnlyMode = enabled
        val downloads = when {
            waiting == 0 -> ""
            enabled -> if (waiting == 1) " · 1 download paused" else " · $waiting downloads paused"
            else -> if (waiting == 1) " · 1 download continues" else " · $waiting downloads continue"
        }
        val msg = if (enabled) {
            "Offline Mode: streaming stopped, no internet use$downloads"
        } else {
            "Online Mode: search and streaming are back$downloads"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    val songs by library.songs.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val hasPermission by library.hasPermission.collectAsState()
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    // The Settings page open (null = the list of pages)
    var settingsPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // Back from a Settings page goes to the list; from the list, out of Settings
    val closeSettingsLevel = {
        if (settingsPage != null) settingsPage = null else isSettingsOpen = false
    }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    val downloadQueue by DownloadQueue.items.collectAsState()
    var isSplashVisible by rememberSaveable { mutableStateOf(true) }
    var showWhatsNewDialog by rememberSaveable { mutableStateOf(false) }

    val settings = library.settings
    val keepScreenOn by settings.keepScreenOn.collectAsState()
    val filterShortAudio by settings.filterShortAudio.collectAsState()
    val filterRecordings by settings.filterRecordings.collectAsState()

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
        library.onPermissionResult(isGranted)
    }

    LaunchedEffect(filterShortAudio, filterRecordings) {
        library.reload()
    }

    LaunchedEffect(Unit) {
        val isNewVersion = settings.markVersionSeen(BuildConfig.VERSION_NAME)

        // Automatically clean up old update APKs:
        // If a new version just booted, wipe everything (both old versions and the installer APK).
        // Otherwise, sweep legacy files/Download/ while preserving any on-hold pending update.
        val pendingUpdate = if (!isNewVersion) {
            val updatesDir = File(context.cacheDir, "updates")
            updatesDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        } else {
            null
        }
        updateChecker.cleanOldApks(preserveFile = pendingUpdate)

        val granted = ContextCompat.checkSelfPermission(
            context,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            library.onPermissionResult(true)
        } else {
            permissionLauncher.launch(requiredPermission)
        }

        // Keep branded splash screen visible for a minimum of 1.8 seconds
        delay(1800)
        isSplashVisible = false

        // Wait for splash screen fade-out animation to complete before popping up the update log dialog.
        // The log tells the GitHub version's story (YouTube features), so the Play version doesn't show it.
        if (isNewVersion && BuildConfig.YOUTUBE) {
            delay(600)
            showWhatsNewDialog = true
        }
    }

    val playlistRepository = library.playlists
    val exportPlaylist = rememberPlaylistExporter(songs)
    val playCounts by playlistRepository.playCounts.collectAsState()
    val favoriteSongIds by playlistRepository.favoriteSongIds.collectAsState()
    val customPlaylists by playlistRepository.customPlaylists.collectAsState()
    val completedTrackIds by SongDownloadService.completedTrackIds.collectAsState()

    // Normalization on: whenever the library reloads, measure any songs that are new since the last run
    LaunchedEffect(songs) {
        if (songs.isNotEmpty() && AudioEffectsManager.getInstance(context).isNormalizationEnabled.value) {
            LoudnessAnalysisWorker.enqueue(context)
        }
    }

    // A background download finished: pick up the file and stop streaming queued copies of it
    LaunchedEffect(completedTrackIds) {
        if (completedTrackIds.isNotEmpty()) library.onDownloadsCompleted()
    }

    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }

    val recentlyPlayedSongs by library.recentlyPlayedSongs.collectAsState()
    val favoriteSongs by library.favoriteSongs.collectAsState()

    // An app icon shortcut: play it once the library has loaded, and show the player
    LaunchedEffect(shortcutTarget, songs) {
        val key = shortcutTarget ?: return@LaunchedEffect
        if (songs.isEmpty()) return@LaunchedEffect
        onShortcutHandled()
        val target = PlayTarget.fromKey(key) ?: return@LaunchedEffect
        val toPlay = target.songs(
            songs, favoriteSongIds, playCounts, customPlaylists,
            downloadedIdFor = { DownloadIndex.getInstance(context).downloadedIdFor(it) },
            offline = isOfflineOnlyMode
        )
        if (toPlay.isEmpty()) {
            val gone = target is PlayTarget.CustomPlaylist && customPlaylists.none { it.id == target.id }
            if (gone) AppShortcuts.forgetPlaylist(context)
            Toast.makeText(
                context,
                if (gone) "That playlist doesn't exist anymore" else "Nothing to play in ${target.label(customPlaylists)} yet",
                Toast.LENGTH_SHORT
            ).show()
            return@LaunchedEffect
        }
        library.playPlaylist(toPlay, shuffle = target == PlayTarget.ShuffleAll)
        isPlayerExpanded = true
    }

    val toggleFavorite: (Long) -> Unit = { songId ->
        if (!library.toggleFavorite(songId)) {
            Toast.makeText(context, "Download this track to add it to Favorites", Toast.LENGTH_SHORT).show()
        }
    }

    val playSong: (Song, List<Song>) -> Unit = library::playSong
    val shuffleAll: () -> Unit = library::shuffleAll
    val playPlaylist: (List<Song>, Boolean) -> Unit = library::playPlaylist

    // ---- Editing a song's details (title, artist, album, cover) ----
    val songEditor = remember { SongEditor(context) }
    var songToEdit by editSongState
    var editAwaitingPermission by remember { mutableStateOf<SongEdit?>(null) }
    val editPermissionLauncherRef = remember { mutableStateOf<ActivityResultLauncher<IntentSenderRequest>?>(null) }

    fun saveEdit(edit: SongEdit) {
        scope.launch {
            when (val result = songEditor.save(edit.song, edit.title, edit.artist, edit.album, edit.coverJpeg)) {
                SongEditor.Result.Saved -> {
                    Toast.makeText(context, "Saved \"${edit.title}\"", Toast.LENGTH_SHORT).show()
                    // Give MediaStore a moment to read the file again
                    delay(1500)
                    library.reload()
                }
                is SongEditor.Result.NeedsPermission -> {
                    editAwaitingPermission = edit
                    editPermissionLauncherRef.value?.launch(IntentSenderRequest.Builder(result.intentSender).build())
                }
                SongEditor.Result.Failed ->
                    Toast.makeText(context, "Couldn't change this file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val editPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val edit = editAwaitingPermission
        editAwaitingPermission = null
        if (edit != null && result.resultCode == android.app.Activity.RESULT_OK) saveEdit(edit)
    }
    editPermissionLauncherRef.value = editPermissionLauncher

    // Android 11+ asks first for files the app didn't create
    val requestSaveEdit: (SongEdit) -> Unit = { edit ->
        songToEdit = null
        val request = songEditor.writeRequest(edit.song)
        if (request != null) {
            editAwaitingPermission = edit
            editPermissionLauncher.launch(IntentSenderRequest.Builder(request).build())
        } else {
            saveEdit(edit)
        }
    }

    // ---- Deleting songs from the phone ----
    val songDeleter = remember { SongDeleter(context) }
    var songAwaitingConfirm by remember { mutableStateOf<Song?>(null) } // Android 10 and older: app's own dialog
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var songAwaitingSystem by remember { mutableStateOf<Song?>(null) } // waiting on Android's dialog

    val afterSongDeleted: (Song) -> Unit = { song ->
        library.onSongDeleted(song)
        Toast.makeText(context, "Deleted \"${song.title}\"", Toast.LENGTH_SHORT).show()
    }

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = songAwaitingSystem
        songAwaitingSystem = null
        if (song == null || result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            afterSongDeleted(song) // Android's dialog already deleted it
        } else if (songDeleter.deleteDirectly(song) == SongDeleter.Result.Deleted) {
            afterSongDeleted(song) // Android 10: allowed now, so try again
        } else {
            Toast.makeText(context, "Couldn't delete \"${song.title}\"", Toast.LENGTH_SHORT).show()
        }
    }

    val launchSystemDelete: (Song, android.content.IntentSender) -> Unit = { song, sender ->
        songAwaitingSystem = song
        deletePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    val requestDeleteSong: (Song) -> Unit = { song ->
        if (song.id > 0) {
            val systemRequest = songDeleter.systemDeleteRequest(listOf(song))
            if (systemRequest != null) launchSystemDelete(song, systemRequest) else songAwaitingConfirm = song
        }
    }

    val currentSong by controllerManager.currentSong.collectAsState()
    val isPlaying by controllerManager.isPlaying.collectAsState()
    val currentPosition by controllerManager.currentPosition.collectAsState()
    val duration by controllerManager.duration.collectAsState()
    val isShuffleEnabled by controllerManager.isShuffleEnabled.collectAsState()
    val repeatMode by controllerManager.repeatMode.collectAsState()

    // Return from settings, or return to Home tab. Screens with their own detail views (playlists)
    // register a later BackHandler, which wins, so Back closes the detail view first.
    BackHandler(enabled = isSettingsOpen || currentTab != NavigationTab.HOME) {
        if (isSettingsOpen) {
            closeSettingsLevel()
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
                                settingsPage?.title ?: "Settings"
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
                            IconButton(onClick = closeSettingsLevel) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        // The download queue, while there's something in it (the GitHub version only)
                        if (BuildConfig.YOUTUBE && downloadQueue.isNotEmpty()) {
                            IconButton(onClick = { showDownloads = true }) {
                                BadgedBox(badge = { Badge { Text("${downloadQueue.size}") } }) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Downloads",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        // Quick Mode Toggle Pill (Online vs Offline); the Play version is always offline
                        if (BuildConfig.YOUTUBE) Surface(
                            onClick = { setOfflineMode(!isOfflineOnlyMode) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isOfflineOnlyMode) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isOfflineOnlyMode) Icons.Default.CloudOff else Icons.Default.Cloud,
                                    contentDescription = if (isOfflineOnlyMode) "Offline Mode" else "Online Mode",
                                    tint = if (isOfflineOnlyMode) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isOfflineOnlyMode) "Offline" else "Online",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOfflineOnlyMode) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }

                        if (isSettingsOpen) {
                            IconButton(onClick = { showAboutDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "About & Credits",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                settingsPage = null
                                isSettingsOpen = true
                            }) {
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
                        // No Download tab in the Google Play version
                        NavigationTab.entries.filter { BuildConfig.YOUTUBE || it != NavigationTab.DOWNLOADER }.forEach { tab ->
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
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
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
                            page = settingsPage,
                            onOpenPage = { settingsPage = it },
                            onShowDownloads = { showDownloads = true },
                            currentTheme = currentTheme,
                            currentAccent = currentAccent,
                            onAccentSelected = {
                                library.settings.setAccentColor(it.name)
                                PlayerWidget.renderAll(context)
                                PlaylistWidget.renderAll(context)
                            },
                            onThemeSelected = { newTheme ->
                                onThemeChanged(newTheme)
                            },
                            keepScreenOn = keepScreenOn,
                            onKeepScreenOnToggled = settings::setKeepScreenOn,
                            filterShortAudio = filterShortAudio,
                            onFilterShortAudioToggled = settings::setFilterShortAudio,
                            filterRecordings = filterRecordings,
                            onFilterRecordingsToggled = settings::setFilterRecordings,
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
                            bottomPadding = contentBottomPadding,
                            librarySongs = songs,
                            isOfflineOnlyMode = isOfflineOnlyMode,
                            onOfflineOnlyModeToggled = setOfflineMode,
                            downloadManager = downloadManager,
                            onLibraryChanged = {
                                scope.launch {
                                    // Give MediaStore a moment to re-read the rewritten files
                                    delay(1500)
                                    library.reload()
                                }
                            }
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
                                },
                                playCounts = playCounts,
                                onPlayList = playPlaylist
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
                                onToggleFavorite = { playlistRepository.toggleFavorite(it) },
                                onDeleteSong = requestDeleteSong,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onPlayInList = playSong,
                                onPlayGroup = playPlaylist
                            )
                        }
                        NavigationTab.SEARCH -> {
                            SearchScreen(
                                songs = songs,
                                favoriteSongIds = favoriteSongIds,
                                currentPlayingSongId = currentSong?.id,
                                bottomPadding = contentBottomPadding,
                                onSongClick = playSong,
                                onToggleFavorite = { playlistRepository.toggleFavorite(it) },
                                onDeleteSong = requestDeleteSong,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onPlayGroup = playPlaylist
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
                                onToggleFavorite = toggleFavorite,
                                onCreatePlaylist = { name, songIds -> playlistRepository.createPlaylist(name, songIds) },
                                onDeletePlaylist = { id ->
                                    playlistRepository.deletePlaylist(id)
                                    AppShortcuts.onPlaylistChanged(context, id, renamed = null)
                                },
                                downloadIndex = library.downloadIndex,
                                isOfflineOnlyMode = isOfflineOnlyMode,
                                showOnlineFilters = BuildConfig.YOUTUBE,
                                downloader = if (BuildConfig.YOUTUBE) trackDownloader else null,
                                onRemoveOnlineTrack = { playlistId, trackId ->
                                    playlistRepository.removeOnlineTrackFromPlaylist(playlistId, trackId)
                                },
                                onDeleteSong = requestDeleteSong,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onRemoveSongFromPlaylist = { playlistId, songId -> playlistRepository.removeSongFromPlaylist(playlistId, songId) },
                                onExportPlaylist = exportPlaylist,
                                onRenamePlaylist = { id, name ->
                                    playlistRepository.renamePlaylist(id, name)
                                    customPlaylists.firstOrNull { it.id == id }?.let { old ->
                                        AppShortcuts.onPlaylistChanged(context, id, renamed = old.copy(name = name))
                                    }
                                    PlaylistWidget.renderAll(context)
                                },
                                playCounts = playCounts,
                                onRestoreSongToPlaylist = playlistRepository::insertSongInPlaylist,
                                onRestoreOnlineTrack = playlistRepository::insertOnlineTrackInPlaylist,
                                onReorderPlaylist = playlistRepository::reorderPlaylist,
                                onPlaylistPlayed = { AppShortcuts.rememberPlaylist(context, it) }
                            )
                        }
                        NavigationTab.DOWNLOADER -> if (BuildConfig.YOUTUBE) {
                            DownloaderScreen(
                                downloadManager = downloadManager,
                                controllerManager = controllerManager,
                                bottomPadding = contentBottomPadding,
                                isOfflineOnlyMode = isOfflineOnlyMode,
                                onSwitchToOnline = { setOfflineMode(false) },
                                playlistRepository = playlistRepository,
                                librarySongIds = remember(songs) { songs.mapTo(HashSet()) { it.id } }
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
                onToggleFavorite = toggleFavorite,
                bottomBarHeight = 80.dp,
                isExpanded = isPlayerExpanded,
                onExpandedChange = { isPlayerExpanded = it },
                onShowQueue = { showQueue = true }
            )
        }

        // "Removed … Undo" messages, just above the mini player and the tabs
        SnackbarHost(
            hostState = undoHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (currentSong != null) 160.dp else 88.dp)
        )

        if (showQueue) {
            val queue by controllerManager.queue.collectAsState()
            val queuePosition by controllerManager.currentQueuePosition.collectAsState()
            QueueSheet(
                queue = queue,
                currentPosition = queuePosition,
                isShuffleEnabled = isShuffleEnabled,
                onPlayItem = controllerManager::skipToQueueItem,
                onRemoveItem = controllerManager::removeQueueItem,
                onMoveItem = controllerManager::moveQueueItem,
                onDismiss = { showQueue = false },
                onRestoreItem = controllerManager::restoreQueueItem
            )
        }

        // Registered each time the player expands, so it is the newest handler and always collapses first
        if (isPlayerExpanded) {
            BackHandler { isPlayerExpanded = false }
        }

        // "Edit details…" from a song's ⋮ menu
        songToEdit?.let { song ->
            EditSongDialog(
                song = song,
                format = songEditor.formatOf(song),
                onSave = requestSaveEdit,
                onDismiss = { songToEdit = null }
            )
        }

        // "Add to playlist…" from a song's ⋮ menu
        songForPlaylistPicker?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = customPlaylists,
                onToggle = { playlistId -> playlistRepository.toggleSongInPlaylist(playlistId, song.id) },
                onCreate = { name -> playlistRepository.createPlaylist(name, listOf(song.id)) },
                onDismiss = { songForPlaylistPicker = null }
            )
        }

        // Delete confirmation on Android 10 and older (newer versions show Android's own dialog)
        songAwaitingConfirm?.let { song ->
            AlertDialog(
                onDismissRequest = { songAwaitingConfirm = null },
                title = { Text("Delete song?") },
                text = { Text("\"${song.title}\" will be removed from your phone. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        songAwaitingConfirm = null
                        when (val result = songDeleter.deleteDirectly(song)) {
                            SongDeleter.Result.Deleted -> afterSongDeleted(song)
                            is SongDeleter.Result.NeedsPermission -> launchSystemDelete(song, result.intentSender)
                            SongDeleter.Result.Failed ->
                                Toast.makeText(context, "Couldn't delete \"${song.title}\"", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { songAwaitingConfirm = null }) { Text("Cancel") }
                }
            )
        }

        // The download queue (top bar button, Settings)
        if (BuildConfig.YOUTUBE && showDownloads) {
            DownloadQueueSheet(
                onRetry = { id ->
                    if (networkPolicyManager.checkDownloadPrecondition() is DownloadPrecondition.Allowed) {
                        DownloadQueue.retry(id)
                        SongDownloadService.startIfNeeded(context)
                    } else {
                        Toast.makeText(context, "Can't download right now: check Offline Mode and your connection", Toast.LENGTH_SHORT).show()
                    }
                },
                onGoOnline = { setOfflineMode(false) },
                onDismiss = { showDownloads = false }
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
                logo = painterResource(R.drawable.wmp_logo),
                onDismiss = { showWhatsNewDialog = false }
            )
        }

        // Full-screen Animated Splash Screen with smooth fade-out
        AnimatedVisibility(
            visible = isSplashVisible,
            exit = fadeOut(animationSpec = tween(600))
        ) {
            SplashScreen(logo = painterResource(R.drawable.wmp_logo))
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

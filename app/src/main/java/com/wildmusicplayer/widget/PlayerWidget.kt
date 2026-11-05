package com.wildmusicplayer.widget

import com.wildmusicplayer.data.PlaylistRepository
import android.view.View
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.compose.ui.graphics.toArgb
import androidx.media3.session.MediaButtonReceiver
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wildmusicplayer.MainActivity
import com.wildmusicplayer.R
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.service.PlaybackService
import com.wildmusicplayer.tile.PlayerTileService
import com.wildmusicplayer.ui.theme.AccentColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen player: cover, title, artist and previous / play-pause / next. PlaybackService
 * reports what's playing ([update]); the last state is remembered, so the widget keeps showing it after
 * the app closes or the phone restarts. Play/pause presses the media key on the app's MediaButtonReceiver,
 * so it works (and resumes the last queue) even when the app isn't running; Media3 ignores other keys
 * there, so previous/next come back here and go straight to the running player.
 * A 4x1 bar by default; stretched taller it switches to the layout with the big cover.
 */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> PlaybackService.skip(next = true)
            ACTION_PREVIOUS -> PlaybackService.skip(next = false)
            ACTION_SHUFFLE -> PlaybackService.toggleShuffle()
            ACTION_REPEAT -> PlaybackService.cycleRepeat()
            // Works with the app closed too: favorites are saved right away
            ACTION_FAVORITE -> {
                val songId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_SONG_ID, -1L)
                if (songId > 0) {
                    PlaylistRepository.getInstance(context).toggleFavorite(songId)
                    renderAll(context)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, manager, intArrayOf(appWidgetId))
    }

    companion object {
        private const val ACTION_NEXT = "com.wildmusicplayer.widget.NEXT"
        private const val ACTION_PREVIOUS = "com.wildmusicplayer.widget.PREVIOUS"
        private const val ACTION_SHUFFLE = "com.wildmusicplayer.widget.SHUFFLE"
        private const val ACTION_REPEAT = "com.wildmusicplayer.widget.REPEAT"
        private const val ACTION_FAVORITE = "com.wildmusicplayer.widget.FAVORITE"
        private const val KEY_SONG_ID = "song_id"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_POSITION = "position"
        private const val KEY_DURATION = "duration"
        private const val PREFS_NAME = "player_widget"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ARTWORK = "artwork"
        private const val KEY_PLAYING = "playing"
        private const val COVER_SIZE_PX = 256
        // Heights (dp) from which the big layout fits
        private const val LARGE_MIN_HEIGHT_DP = 130f

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // The cover of the song shown, kept in memory so play/pause updates don't reload it
        @Volatile private var coverUri: String? = null
        @Volatile private var coverBitmap: Bitmap? = null

        /**
         * Called by PlaybackService when the song, play state, shuffle, repeat or position changes; null [title] =
         * nothing loaded. [songId] is null for online streams (no heart then).
         */
        fun update(
            context: Context,
            title: String?,
            artist: String?,
            artworkUri: String?,
            isPlaying: Boolean,
            songId: Long? = null,
            shuffle: Boolean = false,
            repeatMode: Int = Player.REPEAT_MODE_OFF,
            positionMs: Long = 0L,
            durationMs: Long = 0L
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putString(KEY_ARTWORK, artworkUri)
                .putBoolean(KEY_PLAYING, isPlaying)
                .putLong(KEY_SONG_ID, songId ?: -1L)
                .putBoolean(KEY_SHUFFLE, shuffle)
                .putInt(KEY_REPEAT, repeatMode)
                .putLong(KEY_POSITION, positionMs)
                .putLong(KEY_DURATION, durationMs)
                .apply()
            renderAll(context)
            PlayerTileService.refresh(context)
        }

        /** The last song and play state reported by PlaybackService (also used by the Quick Settings tile). */
        fun nowPlaying(context: Context): NowPlaying {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return NowPlaying(
                title = prefs.getString(KEY_TITLE, null),
                artist = prefs.getString(KEY_ARTIST, null),
                isPlaying = prefs.getBoolean(KEY_PLAYING, false)
            )
        }

        /** The player stopped running: keep showing the song, with a play button. */
        fun markPaused(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_PLAYING, false).apply()
            renderAll(context)
            PlayerTileService.refresh(context)
        }

        /** True when at least one player widget is on the home screen (no need to update otherwise). */
        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PlayerWidget::class.java)).isNotEmpty()

        /** Draws every placed widget again, e.g. after the accent color changed. */
        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val songId = prefs.getLong(KEY_SONG_ID, -1L)
            val state = WidgetState(
                title = prefs.getString(KEY_TITLE, null),
                artist = prefs.getString(KEY_ARTIST, null),
                artworkUri = prefs.getString(KEY_ARTWORK, null),
                isPlaying = prefs.getBoolean(KEY_PLAYING, false),
                isFavorite = songId > 0 && songId in PlaylistRepository.getInstance(context).favoriteSongIds.value,
                canFavorite = songId > 0,
                shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
                repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF),
                positionMs = prefs.getLong(KEY_POSITION, 0L),
                durationMs = prefs.getLong(KEY_DURATION, 0L)
            )
            val cover = coverBitmap.takeIf { coverUri == state.artworkUri }
            for (id in ids) {
                manager.updateAppWidget(id, views(context, manager, id, state, cover))
            }
            // A new cover: load it, then draw again
            if (state.artworkUri != coverUri) loadCover(context.applicationContext, state.artworkUri)
        }

        private fun loadCover(context: Context, uri: String?) {
            coverUri = uri
            coverBitmap = null
            if (uri == null) return
            scope.launch {
                val bitmap = try {
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .size(COVER_SIZE_PX)
                        .allowHardware(false) // widgets can't show hardware bitmaps
                        .build()
                    (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                } catch (e: Exception) {
                    null
                }
                if (coverUri == uri && bitmap != null) {
                    coverBitmap = bitmap
                    renderAll(context)
                }
            }
        }

        private fun views(context: Context, manager: AppWidgetManager, id: Int, state: WidgetState, cover: Bitmap?): RemoteViews {
            val small = layout(context, R.layout.widget_player_small, state, cover, large = false)
            val large = layout(context, R.layout.widget_player_large, state, cover, large = true)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(mapOf(SizeF(180f, 48f) to small, SizeF(180f, LARGE_MIN_HEIGHT_DP) to large))
            } else {
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                if (height >= LARGE_MIN_HEIGHT_DP) large else small
            }
        }

        private fun layout(context: Context, layoutId: Int, state: WidgetState, cover: Bitmap?, large: Boolean): RemoteViews =
            RemoteViews(context.packageName, layoutId).apply {
                val accent = ColorStateList.valueOf(accentColor(context))
                // How far into the song: a thin line, hidden when nothing is loaded
                if (state.title != null && state.durationMs > 0) {
                    setViewVisibility(R.id.widget_progress, View.VISIBLE)
                    setProgressBar(R.id.widget_progress, 1000, (state.positionMs * 1000 / state.durationMs).toInt().coerceIn(0, 1000), false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setColorStateList(R.id.widget_progress, "setProgressTintList", accent)
                    }
                } else {
                    setViewVisibility(R.id.widget_progress, View.INVISIBLE)
                }
                if (large) {
                    setImageViewResource(R.id.widget_favorite, if (state.isFavorite) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
                    setViewVisibility(R.id.widget_favorite, if (state.canFavorite) View.VISIBLE else View.INVISIBLE)
                    // Shuffle off: dimmed. On (and a liked song's heart): the accent color, from Android 12
                    setInt(R.id.widget_shuffle, "setImageAlpha", if (state.shuffle) 255 else 110)
                    // Repeat: off (dimmed) -> all -> one (the "1" icon), like the app's button
                    val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                    setImageViewResource(
                        R.id.widget_repeat,
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat
                    )
                    setInt(R.id.widget_repeat, "setImageAlpha", if (repeatOn) 255 else 110)
                    setContentDescription(
                        R.id.widget_repeat,
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat this song"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (state.shuffle) setColorStateList(R.id.widget_shuffle, "setImageTintList", accent)
                        if (repeatOn) setColorStateList(R.id.widget_repeat, "setImageTintList", accent)
                        if (state.isFavorite) setColorStateList(R.id.widget_favorite, "setImageTintList", accent)
                    }
                    setOnClickPendingIntent(R.id.widget_shuffle, widgetAction(context, ACTION_SHUFFLE))
                    setOnClickPendingIntent(R.id.widget_repeat, widgetAction(context, ACTION_REPEAT))
                    setOnClickPendingIntent(R.id.widget_favorite, widgetAction(context, ACTION_FAVORITE))
                }
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_nothing_playing))
                setTextViewText(R.id.widget_artist, state.artist.orEmpty())
                if (cover != null) {
                    setImageViewBitmap(R.id.widget_cover, cover)
                    setViewPadding(R.id.widget_cover, 0, 0, 0, 0)
                } else {
                    // The music note, smaller inside its box
                    val padding = context.resources.getDimensionPixelSize(R.dimen.widget_placeholder_padding)
                    setImageViewResource(R.id.widget_cover, R.drawable.ic_widget_music)
                    setViewPadding(R.id.widget_cover, padding, padding, padding, padding)
                }
                setImageViewResource(R.id.widget_play, if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setColorStateList(R.id.widget_play, "setBackgroundTintList", ColorStateList.valueOf(accentColor(context)))
                }

                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                setOnClickPendingIntent(R.id.widget_cover, openApp(context))
                setOnClickPendingIntent(R.id.widget_previous, widgetAction(context, ACTION_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, widgetAction(context, ACTION_NEXT))
            }

        // The app's accent color, in the shade for the phone's light or dark mode (the widget follows the phone)
        private fun accentColor(context: Context): Int {
            val name = AppSettings.getInstance(context).accentColor.value
            val accent = AccentColor.entries.firstOrNull { it.name == name } ?: AccentColor.BLUE
            if (accent == AccentColor.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return context.getColor(android.R.color.system_accent1_500)
            }
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return (if (night) accent.dark else accent.light).toArgb()
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun widgetAction(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun mediaKey(context: Context, keyCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getBroadcast(context, keyCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    data class NowPlaying(val title: String?, val artist: String?, val isPlaying: Boolean)

    private data class WidgetState(
        val title: String?,
        val artist: String?,
        val artworkUri: String?,
        val isPlaying: Boolean,
        val isFavorite: Boolean = false,
        val canFavorite: Boolean = false,
        val shuffle: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )
}

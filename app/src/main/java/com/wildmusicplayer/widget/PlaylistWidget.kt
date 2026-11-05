package com.wildmusicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.wildmusicplayer.R
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.PlayTarget
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.service.PlaybackService
import com.wildmusicplayer.ui.theme.AccentColor

/**
 * A one-tap widget for a playlist, Favorites, Most Played, Recently Added or Shuffle all: picked when it's
 * placed ([PlaylistWidgetConfigActivity]), changeable later by long-pressing it (Android 12+). The tap asks
 * the playback service to play it, so it works with the app closed, like a car's Play button.
 */
class PlaylistWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove(targetKey(it)).remove(shuffleKey(it)) }
        prefs.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PLAY) return super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val target = PlayTarget.fromKey(prefs.getString(targetKey(widgetId), null)) ?: PlayTarget.ShuffleAll
        val shuffle = prefs.getBoolean(shuffleKey(widgetId), target == PlayTarget.ShuffleAll)
        play(context, target, shuffle, goAsync())
    }

    companion object {
        private const val PREFS_NAME = "playlist_widget"
        private const val ACTION_PLAY = "com.wildmusicplayer.widget.PLAY_TARGET"

        private fun targetKey(widgetId: Int) = "target_$widgetId"
        private fun shuffleKey(widgetId: Int) = "shuffle_$widgetId"

        /** Saves what [widgetId] plays (from the config screen) and draws it. */
        fun configure(context: Context, widgetId: Int, target: PlayTarget, shuffle: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(targetKey(widgetId), target.key)
                .putBoolean(shuffleKey(widgetId), shuffle)
                .apply()
            render(context, AppWidgetManager.getInstance(context), widgetId)
        }

        /** The saved choice for [widgetId] (for the config screen), or null when it's new. */
        fun savedChoice(context: Context, widgetId: Int): Pair<PlayTarget, Boolean>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val target = PlayTarget.fromKey(prefs.getString(targetKey(widgetId), null)) ?: return null
            return target to prefs.getBoolean(shuffleKey(widgetId), false)
        }

        /** Draws every placed playlist widget again (a playlist renamed, the accent color changed). */
        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, PlaylistWidget::class.java)).forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val target = PlayTarget.fromKey(prefs.getString(targetKey(widgetId), null)) ?: PlayTarget.ShuffleAll
            val shuffle = prefs.getBoolean(shuffleKey(widgetId), target == PlayTarget.ShuffleAll)
            val playlists = PlaylistRepository.getInstance(context).customPlaylists.value
            val views = RemoteViews(context.packageName, R.layout.widget_playlist).apply {
                setTextViewText(R.id.playlist_widget_title, target.label(playlists))
                setTextViewText(
                    R.id.playlist_widget_subtitle,
                    context.getString(if (shuffle) R.string.playlist_widget_tap_to_shuffle else R.string.playlist_widget_tap_to_play)
                )
                setImageViewResource(
                    R.id.playlist_widget_icon,
                    when (target) {
                        PlayTarget.ShuffleAll -> R.drawable.ic_widget_shuffle
                        PlayTarget.Favorites -> R.drawable.ic_widget_heart_filled
                        PlayTarget.MostPlayed, PlayTarget.RecentlyAdded -> R.drawable.ic_widget_music
                        is PlayTarget.CustomPlaylist -> R.drawable.ic_widget_playlist
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setColorStateList(R.id.playlist_widget_play, "setBackgroundTintList", ColorStateList.valueOf(accentColor(context)))
                }
                val tap = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    Intent(context, PlaylistWidget::class.java)
                        .setAction(ACTION_PLAY)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                setOnClickPendingIntent(R.id.playlist_widget_root, tap)
                setOnClickPendingIntent(R.id.playlist_widget_play, tap)
            }
            manager.updateAppWidget(widgetId, views)
        }

        /**
         * Asks the playback service (started by connecting to it, if it wasn't running) to play [target].
         * The service finds the songs itself; [pending] keeps this receiver alive meanwhile.
         */
        private fun play(context: Context, target: PlayTarget, shuffle: Boolean, pending: PendingResult) {
            val appContext = context.applicationContext
            val future = MediaController.Builder(appContext, SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)))
                .buildAsync()
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    pending.finish()
                    return@addListener
                }
                val args = Bundle().apply {
                    putString(PlaybackService.EXTRA_PLAY_TARGET, target.key)
                    putBoolean(PlaybackService.EXTRA_SHUFFLE, shuffle)
                }
                val result = controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_PLAY_TARGET, Bundle.EMPTY), args)
                result.addListener({
                    val code = runCatching { result.get().resultCode }.getOrDefault(SessionResult.RESULT_ERROR_UNKNOWN)
                    if (code == SessionResult.RESULT_ERROR_BAD_VALUE) {
                        Toast.makeText(appContext, "Nothing to play in ${target.label(PlaylistRepository.getInstance(appContext).customPlaylists.value)} yet", Toast.LENGTH_SHORT).show()
                    }
                    controller.release()
                    pending.finish()
                }, ContextCompat.getMainExecutor(appContext))
            }, ContextCompat.getMainExecutor(appContext))
        }

        // The app's accent color, in the shade for the phone's light or dark mode
        private fun accentColor(context: Context): Int {
            val name = AppSettings.getInstance(context).accentColor.value
            val accent = AccentColor.entries.firstOrNull { it.name == name } ?: AccentColor.BLUE
            if (accent == AccentColor.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return context.getColor(android.R.color.system_accent1_500)
            }
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return (if (night) accent.dark else accent.light).toArgb()
        }
    }
}

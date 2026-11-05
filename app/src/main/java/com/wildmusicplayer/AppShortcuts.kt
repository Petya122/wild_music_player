package com.wildmusicplayer

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.wildmusicplayer.data.PlayTarget
import com.wildmusicplayer.data.Playlist

/**
 * Long-press the app icon: "Shuffle all" and "Favorites" (res/xml/shortcuts.xml), plus the playlist played
 * last, kept up to date here. Each opens the app and starts playing (see [MainActivity]).
 */
object AppShortcuts {
    const val ACTION_PLAY = "com.wildmusicplayer.PLAY"
    const val EXTRA_PLAY_TARGET = "com.wildmusicplayer.PLAY_TARGET"
    private const val LAST_PLAYLIST_ID = "last_playlist"

    /** What a shortcut asked to play, or null for an ordinary app start. */
    fun targetOf(intent: Intent?): String? =
        intent?.takeIf { it.action == ACTION_PLAY }?.getStringExtra(EXTRA_PLAY_TARGET)

    /** A custom playlist was played: it becomes the third shortcut ("Car mix"), replacing the previous one. */
    fun rememberPlaylist(context: Context, playlist: Playlist) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_PLAY)
            .putExtra(EXTRA_PLAY_TARGET, PlayTarget.CustomPlaylist(playlist.id).key)
        val shortcut = ShortcutInfoCompat.Builder(context, LAST_PLAYLIST_ID)
            .setShortLabel(playlist.name.take(24).ifBlank { "Playlist" })
            .setLongLabel(playlist.name.ifBlank { "Playlist" })
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_playlist))
            .setIntent(intent)
            .build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: Exception) {
            // Some launchers limit shortcut updates: the old one just stays
        }
    }

    /** A playlist was renamed or deleted: update or remove its shortcut, if it's the one shown. */
    fun onPlaylistChanged(context: Context, playlistId: String, renamed: Playlist?) {
        val shown = try {
            ShortcutManagerCompat.getDynamicShortcuts(context).firstOrNull { it.id == LAST_PLAYLIST_ID }
        } catch (e: Exception) {
            null
        } ?: return
        if (shown.intent.getStringExtra(EXTRA_PLAY_TARGET) != PlayTarget.CustomPlaylist(playlistId).key) return
        if (renamed != null) rememberPlaylist(context, renamed) else forgetPlaylist(context)
    }

    /** The remembered playlist is gone: take its shortcut away. */
    fun forgetPlaylist(context: Context) {
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(LAST_PLAYLIST_ID))
        } catch (e: Exception) {
        }
    }
}

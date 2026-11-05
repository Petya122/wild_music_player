package com.wildmusicplayer.tile

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.wildmusicplayer.MainActivity
import com.wildmusicplayer.R
import com.wildmusicplayer.service.PlaybackService
import com.wildmusicplayer.widget.PlayerWidget

/**
 * The Quick Settings tile: tap to play / pause, lit while music plays, with the song's title and artist.
 * It shows what PlaybackService last reported (the same state as the home-screen widget).
 *
 * When it isn't playing (paused, or the app is closed) a tap presses Play like a headset would
 * ([PlaybackService.startFromOutside]): the last queue plays again, or everything shuffled when there's
 * none. (0.9.5 gave up when there was nothing to resume, which ended in "Wild Music isn't responding".)
 * The tile only lights up once music really plays.
 */
class PlayerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        listening = this
        showState(PlayerWidget.nowPlaying(this))
    }

    override fun onStopListening() {
        if (listening === this) listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val state = PlayerWidget.nowPlaying(this)
        if (PlaybackService.isPlaying()) {
            PlaybackService.pause()
            showState(state.copy(isPlaying = false))
            return
        }
        // Without access to the music there's nothing to play: the app asks for it
        if (!canReadMusic() || !PlaybackService.startFromOutside(this, PlaybackService.START_RESUME)) {
            openApp()
            return
        }
        // Lit only once it really plays (PlaybackService redraws the tile then)
        showStarting()
    }

    private fun canReadMusic(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** No permission to read the music yet: open the app, where the user can sort that out. */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.tile_nothing_to_play, Toast.LENGTH_LONG).show()
        }
    }

    private fun showStarting() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(R.string.tile_starting)
        tile.updateTile()
    }

    private fun showState(state: PlayerWidget.NowPlaying) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_music)
        tile.state = if (state.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = state.title ?: getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                state.title == null -> getString(R.string.tile_tap_to_play)
                state.isPlaying -> state.artist ?: getString(R.string.tile_label)
                else -> getString(R.string.tile_paused)
            }
        }
        tile.contentDescription = listOfNotNull(state.title, state.artist).joinToString(", ").ifEmpty { getString(R.string.tile_label) }
        tile.updateTile()
    }

    companion object {
        // The tile while Quick Settings shows it: Android only asks it to redraw when the panel opens
        @Volatile private var listening: PlayerTileService? = null

        /** Redraws the tile (if it's in Quick Settings) after the song or play state changed. */
        fun refresh(context: Context) {
            listening?.let { tile ->
                tile.showState(PlayerWidget.nowPlaying(tile))
                return
            }
            try {
                requestListeningState(context, ComponentName(context, PlayerTileService::class.java))
            } catch (e: Exception) {
                // Not added to Quick Settings, or the system refused: nothing to redraw
            }
        }
    }
}

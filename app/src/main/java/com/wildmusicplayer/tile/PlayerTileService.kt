package com.wildmusicplayer.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent
import androidx.media3.session.MediaButtonReceiver
import com.wildmusicplayer.R
import com.wildmusicplayer.service.PlaybackService
import com.wildmusicplayer.widget.PlayerWidget

/**
 * The Quick Settings tile: tap to play / pause, lit while music plays, with the song's title and artist.
 * It shows what PlaybackService last reported (the same state as the home-screen widget). When the player
 * isn't running, a tap presses Play like a headset would, which resumes the last queue.
 */
class PlayerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        showState(PlayerWidget.nowPlaying(this))
    }

    override fun onClick() {
        super.onClick()
        val state = PlayerWidget.nowPlaying(this)
        if (!PlaybackService.togglePlayPause()) {
            sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(this, MediaButtonReceiver::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            )
        }
        // Show the expected state at once; PlaybackService confirms it moments later
        showState(state.copy(isPlaying = !state.isPlaying))
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
        /** Asks the system to redraw the tile (if it's in Quick Settings) after the song or play state changed. */
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, PlayerTileService::class.java))
            } catch (e: Exception) {
                // Not added to Quick Settings, or the system refused: nothing to redraw
            }
        }
    }
}

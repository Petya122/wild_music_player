package com.wildmusicplayer.ui.components

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private class ChangelogEntry(val emoji: String, val title: String, val desc: String)

private class ReleaseNotes(val version: String, val entries: List<ChangelogEntry>)

// Newest first. Add the next release at the top; older notes stay below as history.
private val releaseHistory = listOf(
    ReleaseNotes(
        "0.9.1", listOf(
            ChangelogEntry("👆", "Swipe to Change Songs", "Swipe the cover in Now Playing, or the mini player, left for the next song and right for the previous one."),
            ChangelogEntry("🎨", "Themes", "Settings → Appearance: pick an accent color (Blue, Purple, Green, Teal, Orange, Red, Pink, or your wallpaper's colors on Android 12+), and the new Black (AMOLED) mode. Every combination works. Cards and buttons now follow the color you pick instead of a stray lilac."),
            ChangelogEntry("🌈", "Cover-Colored Player", "Now Playing takes on the main color of the song's cover and fades into the background."),
            ChangelogEntry("❤️", "Favorite from the Notification", "A heart button in the notification and on the lock screen adds the song to your favorites without opening the app."),
            ChangelogEntry("✏️", "Edit Playlists", "Inside a playlist: ✎ renames it, ⇅ shows drag handles to put the songs in your own order."),
            ChangelogEntry("↩️", "Undo", "Removed a song from the queue or a playlist by mistake? Tap Undo in the message that appears.")
        )
    ),
    ReleaseNotes(
        "0.9.0", listOf(
            ChangelogEntry("🚗", "Song Titles in the Car", "Found the real reason the car sometimes showed \"Not Provided\": the song details Android passes to Bluetooth didn't match, so it kept waiting before telling the car anything. Fixed. The short pause after connecting from 0.8.7 is gone too."),
            ChangelogEntry("🗑️", "Safer Playlist Deleting", "Deleting a playlist now asks first. The export button got a clearer share icon."),
            ChangelogEntry("🕘", "Recently Played Keeps Up", "Songs reached with Next or that play automatically now show up in Recently Played too, and the row scrolls back to the newest one."),
            ChangelogEntry("✨", "Polish", "The clock and battery icons are visible again in the light theme. Smaller fixes in the queue, the equalizer and the restore summary.")
        )
    ),
    ReleaseNotes(
        "0.8.9", listOf(
            ChangelogEntry("💾", "Back Up & Restore Playlists", "Settings → Backup & Restore saves all your playlists, favorites and recently played to a file. Restore it after a reinstall or on a new phone: songs are found again by their file, or by title, artist and length. Restoring only adds, it never deletes."),
            ChangelogEntry("📤", "Export as M3U8", "The new save button on each playlist writes an .m3u8 file that other music players can open."),
            ChangelogEntry("🧹", "Tidier Settings", "The Settings screen was reorganized behind the scenes. It looks the same.")
        )
    ),
    ReleaseNotes(
        "0.8.8", listOf(
            ChangelogEntry("🎤", "Lyrics", "Tap the lyrics button next to the heart in Now Playing. Lyrics come from LRCLIB and follow the song line by line where available; tap a line to jump there. Once looked up, they're saved and work in Offline Mode too.")
        )
    ),
    ReleaseNotes(
        "0.8.7", listOf(
            ChangelogEntry("🌙", "Sleep Timer", "Tap the moon in Now Playing: 15 to 90 minutes, or after this song. The music fades out over the last 10 seconds, then pauses, even with the screen off. The countdown shows at the top."),
            ChangelogEntry("🎚️", "Equalizer", "In the Sound sheet: your phone's presets (Pop, Rock, Bass…) or your own curve with a slider per band. Saved for next time."),
            ChangelogEntry("🚗", "Car Display Refresh", "A few seconds after the car connects, the app briefly re-sends the song and repeat setting, so the display shouldn't stay on \"Not Provided\" or lose RPT. You may hear a tiny gap when this happens.")
        )
    ),
    ReleaseNotes(
        "0.8.6", listOf(
            ChangelogEntry("📋", "See What's Next", "Tap the queue button in Now Playing to see the songs coming up. Tap one to jump to it, ✕ to remove it, or drag the handle to change the order."),
            ChangelogEntry("⏭️", "Play Next & Add to Queue", "Every song's ⋮ menu (also online tracks in playlists) can now play it right after the current song, or add it to the end of the queue. With shuffle on, too.")
        )
    ),
    ReleaseNotes(
        "0.8.5", listOf(
            ChangelogEntry("🧱", "Rebuilt Foundations", "Home, Songs, Search, Playlists, the player, and how your playlists, favorites, saved queue and settings are stored were all rebuilt on a cleaner base. Everything carries over and should look and work exactly as before."),
            ChangelogEntry("🔄", "Smoother Rotation", "Turning the phone no longer reconnects the player or reloads your library.")
        )
    ),
    ReleaseNotes(
        "0.8.4", listOf(
            ChangelogEntry("🍎", "Groundwork for iPhone", "The first step toward an iPhone version: song matching, title cleanup and loudness measuring now live in shared code that also builds for iOS. Nothing should look or work differently.")
        )
    ),
    ReleaseNotes(
        "0.8.3", listOf(
            ChangelogEntry("↩️", "Back Stays in Playlists", "Pressing Back inside a playlist now returns to your playlists instead of jumping to Home. With the player open, Back still closes the player first.")
        )
    ),
    ReleaseNotes(
        "0.8.2", listOf(
            ChangelogEntry("⚡", "Faster Downloads", "Songs now download in small pieces, which YouTube doesn't slow down, and a piece that fails is simply retried."),
            ChangelogEntry("➕", "Add Your Songs to Playlists", "Tap ⋮ on any song → Add to playlist… to put it in one or more playlists, or create a new one. Inside a playlist, ⋮ → Remove from this playlist."),
            ChangelogEntry("📶", "Downloaded Songs Stop Streaming", "Once a queued song finishes downloading, the queue plays the file instead of streaming it, also when the car resumes it."),
            ChangelogEntry("🔢", "Correct Playlist Counts", "Downloaded tracks count as local right away, and Sync All only downloads what isn't on your phone yet."),
            ChangelogEntry("✨", "Polish", "Sharper cover art, no repeated title under the artist, the keyboard closes when you scroll results, and \"1 track liked\".")
        )
    ),
    ReleaseNotes(
        "0.8.1", listOf(
            ChangelogEntry("🩹", "App Opens Again", "v0.8.0 crashed right at startup on every phone. Fixed, and the background song measuring for Audio Normalization now actually runs.")
        )
    ),
    ReleaseNotes(
        "0.8.0", listOf(
            ChangelogEntry("⚖️", "Real Loudness Normalization", "With Audio Normalization on, the app measures how loud each song on your phone is and evens them out: loud songs are turned down, quiet ones up, so the volume stays steady from track to track."),
            ChangelogEntry("📊", "Measured in the Background", "Songs are measured once, in the background (not while the battery is low). Settings → Audio Normalization shows how many are done. New downloads are measured automatically."),
            ChangelogEntry("🎚️", "Works With Your Boost", "Your volume and dB Boost still apply on top. Online streams play as they are.")
        )
    ),
    ReleaseNotes(
        "0.7.4", listOf(
            ChangelogEntry("📜", "Scrollable Update History", "This list now scrolls, and the notes of earlier versions stay below the newest one.")
        )
    ),
    ReleaseNotes(
        "0.7.3", listOf(
            ChangelogEntry("🗑️", "Delete Songs", "Tap ⋮ on any song in Songs, Search or a playlist and choose Delete from phone. It's also removed from your favorites and playlists."),
            ChangelogEntry("🎯", "Playlists Keep the Version You Picked", "An online playlist track only counts as downloaded when that exact video was downloaded. Another recording with the same title shows as \"similar on phone\" and can still be downloaded.")
        )
    ),
    ReleaseNotes(
        "0.7.2", listOf(
            ChangelogEntry("⬇️", "Download Progress in Playlists", "Downloading an online track from a playlist shows a progress bar and percentage right on the track."),
            ChangelogEntry("🔊", "dB Boost Buttons That Fit", "Off · +3 · +6 · +9 · +12 fit on one line in Settings and in the player.")
        )
    ),
    ReleaseNotes(
        "0.7.1", listOf(
            ChangelogEntry("🔎", "Search in Fix Song Tags", "Wrong or missing match? Tap \"Not right? Search…\" on any song and type the artist and title yourself, in Songs or Videos."),
            ChangelogEntry("🎤", "Artist-Aware Playlist Matching", "A remix with the same title no longer replaces the original in your playlists.")
        )
    ),
    ReleaseNotes(
        "0.7.0", listOf(
            ChangelogEntry("🚗", "Resume From Your Car", "Press Play on your car radio or headset (or switch the radio to Media) and the music continues where you stopped, even if the app was closed."),
            ChangelogEntry("🔵", "Resume When Bluetooth Reconnects", "If music paused because the car or headphones disconnected, it starts again when they reconnect within 2 hours."),
            ChangelogEntry("⏯️", "Pick Up Where You Left Off", "Opening the app brings back your last queue, paused at the same spot. Stop clears it; Exit keeps it for the car.")
        )
    ),
    ReleaseNotes(
        "0.6.0", listOf(
            ChangelogEntry("🏷️", "Artist & Title on Downloads", "Downloaded songs carry their real title, artist and album inside the file, so the app, lock screen and car radio show them properly."),
            ChangelogEntry("🖼️", "Cover Art on Downloads", "Every download gets its own square cover art, saved into the song file itself."),
            ChangelogEntry("🛠️", "Fix Older Downloads", "Settings → Fix Downloaded Song Tags adds the artist and cover to songs downloaded before this version.")
        )
    ),
    ReleaseNotes(
        "0.5.3", listOf(
            ChangelogEntry("🎵", "YouTube Music Song Search", "Search looks in YouTube Music's Songs by default, so you get the official track with its real artist."),
            ChangelogEntry("🌟", "Smarter Best Match", "Results are ranked by how well they match your words; sped up, cover, live and remix versions drop down unless you search for them.")
        )
    ),
    ReleaseNotes(
        "0.5.2", listOf(
            ChangelogEntry("🔁", "Hybrid Playlists Play Straight Through", "Play All, Shuffle and tapping any track queue the whole playlist, local and online songs together."),
            ChangelogEntry("📦", "Smaller, Faster App", "The app is optimized and shrunk at build time, so updates download faster.")
        )
    ),
    ReleaseNotes(
        "0.5.1", listOf(
            ChangelogEntry("🔊", "In-Player Volume & Presets", "Control volume from the player with Mute and 25 / 50 / 75 / 100% presets."),
            ChangelogEntry("🚀", "Decibel Boost", "Gain up to +12 dB beyond 100% for quiet recordings and low car Bluetooth volume."),
            ChangelogEntry("⚖️", "Audio Normalization", "Levels playback volume to avoid jarring jumps between tracks.")
        )
    ),
    ReleaseNotes(
        "0.5.0", listOf(
            ChangelogEntry("🌐", "Offline & Online Mode Toggle", "Switch between 100% offline playback and online streaming & search with one tap."),
            ChangelogEntry("🔀", "Hybrid Playlists", "Add YouTube songs to playlists alongside the music on your phone."),
            ChangelogEntry("⚡", "1-Tap Sync", "Download a playlist's online songs in the background for offline listening.")
        )
    ),
    ReleaseNotes(
        "0.4.6", listOf(
            ChangelogEntry("⏸️", "Update on Hold & Install Later", "Downloaded updates can be installed right away or kept for later."),
            ChangelogEntry("📥", "Background Song Downloads", "Songs keep downloading when the app is in the background or closed.")
        )
    )
)

@Composable
fun WhatsNewDialog(
    versionName: String,
    logo: Painter,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular Logo Badge
                Image(
                    painter = logo,
                    contentDescription = "Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Updated to v$versionName! 🎉",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "What's new, with earlier updates below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Only the notes scroll; the header and the button always stay on screen
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    releaseHistory.forEachIndexed { index, release ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                        VersionHeader(version = release.version, isLatest = index == 0)
                        release.entries.forEach { entry ->
                            ChangelogItem(emoji = entry.emoji, title = entry.title, desc = entry.desc)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Enjoy Listening!",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionHeader(version: String, isLatest: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "v$version",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isLatest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isLatest) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Latest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ChangelogItem(
    emoji: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp, top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

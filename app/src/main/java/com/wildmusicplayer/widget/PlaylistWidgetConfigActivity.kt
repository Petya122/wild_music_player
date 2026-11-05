package com.wildmusicplayer.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.PlayTarget
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.getInstance
import com.wildmusicplayer.ui.theme.AccentColor
import com.wildmusicplayer.ui.theme.ThemeMode
import com.wildmusicplayer.ui.theme.WildMusicPlayerTheme

/** Shown when the playlist widget is placed (or long-pressed to change it): what should one tap play? */
class PlaylistWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without choosing doesn't place the widget
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val settings = AppSettings.getInstance(this)
        val playlists = PlaylistRepository.getInstance(this)
        val saved = PlaylistWidget.savedChoice(this, widgetId)

        setContent {
            val themeName by settings.themeMode.collectAsState()
            val accentName by settings.accentColor.collectAsState()
            WildMusicPlayerTheme(
                themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM,
                accent = AccentColor.entries.firstOrNull { it.name == accentName } ?: AccentColor.BLUE
            ) {
                val customPlaylists by playlists.customPlaylists.collectAsState()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ChooseTarget(
                        choices = listOf(PlayTarget.ShuffleAll, PlayTarget.Favorites, PlayTarget.MostPlayed, PlayTarget.RecentlyAdded) +
                            customPlaylists.map { PlayTarget.CustomPlaylist(it.id) },
                        label = { it.label(customPlaylists) },
                        initial = saved,
                        onDone = { target, shuffle ->
                            PlaylistWidget.configure(this, widgetId, target, shuffle)
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseTarget(
    choices: List<PlayTarget>,
    label: (PlayTarget) -> String,
    initial: Pair<PlayTarget, Boolean>?,
    onDone: (PlayTarget, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var selected by remember { mutableStateOf(initial?.first?.takeIf { it in choices } ?: PlayTarget.ShuffleAll) }
    var shuffle by remember { mutableStateOf(initial?.second ?: true) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            text = "What should the widget play?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
        )
        Text(
            text = "One tap plays it, even when the app is closed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(choices, key = { it.key }) { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = target }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = target == selected, onClick = { selected = target })
                    Icon(iconFor(target), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label(target), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickable { shuffle = !shuffle }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Shuffle", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Play in random order instead of the list order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = shuffle, onCheckedChange = { shuffle = it })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = { onDone(selected, shuffle) }) { Text("Add widget", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun iconFor(target: PlayTarget): ImageVector = when (target) {
    PlayTarget.ShuffleAll -> Icons.Default.Shuffle
    PlayTarget.Favorites -> Icons.Default.Favorite
    PlayTarget.MostPlayed -> Icons.Default.Whatshot
    PlayTarget.RecentlyAdded -> Icons.Default.LibraryAdd
    is PlayTarget.CustomPlaylist -> Icons.AutoMirrored.Filled.QueueMusic
}

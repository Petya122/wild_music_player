package com.wildmusicplayer.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wildmusicplayer.BuildConfig
import com.wildmusicplayer.R

/** A license text shipped in res/raw, so it can be read offline. */
enum class LicenseText(val title: String, @RawRes val res: Int) {
    GPL_3("GNU General Public License v3.0", R.raw.license_gpl_3_0),
    APACHE_2("Apache License 2.0", R.raw.license_apache_2_0),
    MIT_JSOUP("MIT License (jsoup)", R.raw.license_mit_jsoup),
    BSD_PROTOBUF("BSD 3-Clause License (Protocol Buffers)", R.raw.license_bsd_protobuf),
    MPL_RHINO("Mozilla Public License 2.0 (Rhino)", R.raw.license_mpl_rhino),
}

/** [youTubeOnly]: part of the YouTube extractor, so only in the GitHub (full) version. */
data class OpenSourceLibrary(
    val name: String,
    val license: String,
    val url: String,
    val text: LicenseText,
    val youTubeOnly: Boolean = false
)

/** What the APK includes (see README "Third-party software"). */
private val allLibraries = listOf(
    OpenSourceLibrary("NewPipeExtractor", "GPL-3.0-or-later", "https://github.com/TeamNewPipe/NewPipeExtractor", LicenseText.GPL_3, youTubeOnly = true),
    OpenSourceLibrary("nanojson (NewPipe's copy)", "Apache-2.0", "https://github.com/TeamNewPipe/nanojson", LicenseText.APACHE_2, youTubeOnly = true),
    OpenSourceLibrary("Mozilla Rhino", "MPL-2.0", "https://github.com/mozilla/rhino", LicenseText.MPL_RHINO, youTubeOnly = true),
    OpenSourceLibrary("jsoup", "MIT", "https://jsoup.org", LicenseText.MIT_JSOUP, youTubeOnly = true),
    OpenSourceLibrary("Protocol Buffers (Java lite)", "BSD-3-Clause", "https://github.com/protocolbuffers/protobuf", LicenseText.BSD_PROTOBUF, youTubeOnly = true),
    OpenSourceLibrary("AndroidX: Core, Activity, Lifecycle, Media3, Compose, WorkManager, Palette", "Apache-2.0", "https://developer.android.com/jetpack/androidx", LicenseText.APACHE_2),
    OpenSourceLibrary("Compose Multiplatform", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform", LicenseText.APACHE_2),
    OpenSourceLibrary("Kotlin and kotlinx (coroutines, serialization)", "Apache-2.0", "https://kotlinlang.org", LicenseText.APACHE_2),
    OpenSourceLibrary("Material Icons", "Apache-2.0", "https://fonts.google.com/icons", LicenseText.APACHE_2),
    OpenSourceLibrary("Coil", "Apache-2.0", "https://github.com/coil-kt/coil", LicenseText.APACHE_2),
    OpenSourceLibrary("OkHttp and Okio", "Apache-2.0", "https://github.com/square/okhttp", LicenseText.APACHE_2),
    OpenSourceLibrary("Reorderable", "Apache-2.0", "https://github.com/Calvin-LL/Reorderable", LicenseText.APACHE_2),
    OpenSourceLibrary("Accompanist Drawable Painter", "Apache-2.0", "https://github.com/google/accompanist", LicenseText.APACHE_2),
    OpenSourceLibrary("Guava, JSR-305, JetBrains annotations, JSpecify", "Apache-2.0", "https://github.com/google/guava", LicenseText.APACHE_2),
)

/** The libraries in this version of the app (the Google Play one has no YouTube extractor). */
val openSourceLibraries = allLibraries.filter { BuildConfig.YOUTUBE || !it.youTubeOnly }

/** The app's own license notice, then every library; tap one to read its license. */
@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showing by rememberSaveable { mutableStateOf<LicenseText?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Inside the dialog, so it gets the dialog's Back: on a license text, Back returns to the list
        BackHandler(enabled = showing != null) { showing = null }
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    val current = showing
                    IconButton(onClick = { if (current != null) showing = null else onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = current?.title ?: "Open source licenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                val current = showing
                if (current != null) {
                    LicenseTextView(current, Modifier.weight(1f))
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        item { AppLicenseNotice(onRead = { showing = LicenseText.GPL_3 }) }
                        items(openSourceLibraries) { library ->
                            LibraryRow(
                                library = library,
                                onRead = { showing = library.text },
                                onOpen = { openWebPage(context, library.url) },
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Close", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun AppLicenseNotice(onRead: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onRead).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("Wild Music Player", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = APP_LICENSE_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Read the GPL-3.0",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun LibraryRow(library: OpenSourceLibrary, onRead: () -> Unit, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRead).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(library.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(library.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onOpen) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open ${library.name} website",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LicenseTextView(license: LicenseText, modifier: Modifier) {
    val context = LocalContext.current
    val text = remember(license) { reflowLicense(readLicense(context, license.res)) }
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private fun readLicense(context: Context, @RawRes res: Int): String =
    try {
        context.resources.openRawResource(res).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "This license text couldn't be loaded."
    }

/**
 * License files are hard-wrapped at ~72 characters, which wraps again (badly) on a phone.
 * Joins each paragraph into one line and drops "-----" / "=====" underlines.
 */
internal fun reflowLicense(text: String): String =
    text.replace("\r\n", "\n")
        .split(Regex("""\n\s*\n"""))
        .map { paragraph ->
            paragraph.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.all { c -> c == '-' || c == '=' } }
                .joinToString(" ")
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

internal fun openWebPage(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}

const val APP_LICENSE_NOTICE =
    "Copyright © 2026 Petya122. Free software under the GNU General Public License v3.0 only. " +
        "It comes with ABSOLUTELY NO WARRANTY. You may share and change it under the GPL's terms. " +
        "Source code: github.com/Petya122/wild_music_player"

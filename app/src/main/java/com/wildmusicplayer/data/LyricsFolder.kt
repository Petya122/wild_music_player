package com.wildmusicplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import com.wildmusicplayer.lyrics.LocalLyricsFiles
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The folder the user picked for their own .lrc files (Settings → Lyrics). Android lets a music app read
 * songs but not other files, so the user grants this one folder once; it stays granted after restarts.
 *
 * A song's lyrics file is found by name, anywhere inside the folder: the song's file name ("Ice MC - Think
 * About the Way.mp3" → "Ice MC - Think About the Way.lrc"), "Artist - Title.lrc" or "Title.lrc".
 */
class LyricsFolder private constructor(context: Context) : LocalLyricsFiles {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _folder = MutableStateFlow(prefs.getString(KEY_FOLDER, null))
    /** The picked folder (a document tree URI), or null. */
    val folder: StateFlow<String?> = _folder.asStateFlow()

    // Lowercased file name without ".lrc" → the file; rebuilt when it's older than INDEX_MAX_AGE_MS
    private var index: Map<String, Uri>? = null
    private var indexBuiltAt = 0L
    private val indexLock = Mutex()

    /** Remembers [uri] (from the folder picker) for good, or forgets the folder when null. */
    fun setFolder(uri: Uri?) {
        _folder.value?.let { old ->
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uri != null) {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.edit().putString(KEY_FOLDER, uri?.toString()).apply()
        _folder.value = uri?.toString()
        index = null
    }

    /** The folder's name for Settings ("Music"), or null when none is picked. */
    fun folderName(): String? = _folder.value?.let { tree ->
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfterLast(':').substringAfterLast('/') }
            .getOrNull()?.ifBlank { "Phone storage" }
    }

    override suspend fun lrcFor(song: Song): String? = withContext(Dispatchers.IO) {
        val tree = _folder.value?.let(Uri::parse) ?: return@withContext null
        val files = indexLock.withLock {
            val current = index
            if (current != null && SystemClock.elapsedRealtime() - indexBuiltAt < INDEX_MAX_AGE_MS) current
            else buildIndex(tree).also {
                index = it
                indexBuiltAt = SystemClock.elapsedRealtime()
            }
        }
        val file = namesFor(song).firstNotNullOfOrNull { files[it] } ?: return@withContext null
        runCatching {
            appContext.contentResolver.openInputStream(file)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
    }

    /** Every .lrc file in the folder and its subfolders (a few levels deep), by lowercased name. */
    private fun buildIndex(tree: Uri): Map<String, Uri> {
        val found = HashMap<String, Uri>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return found
        val queue = ArrayDeque(listOf(rootId to 0))
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_ENTRIES) {
            val (folderId, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            try {
                appContext.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && visited < MAX_ENTRIES) {
                        visited++
                        val id = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH) queue.addLast(id to depth + 1)
                        } else if (name.endsWith(".lrc", ignoreCase = true)) {
                            found.putIfAbsent(name.dropLast(4).trim().lowercase(), DocumentsContract.buildDocumentUriUsingTree(tree, id))
                        }
                    }
                }
            } catch (e: Exception) {
                // Permission withdrawn or a folder that can't be listed: use what was found
            }
        }
        return found
    }

    companion object {
        private const val PREFS_NAME = "lyrics_folder"
        private const val KEY_FOLDER = "folder"
        private const val INDEX_MAX_AGE_MS = 2 * 60 * 1000L
        private const val MAX_DEPTH = 6
        private const val MAX_ENTRIES = 20_000

        @Volatile private var instance: LyricsFolder? = null

        fun getInstance(context: Context): LyricsFolder =
            instance ?: synchronized(this) { instance ?: LyricsFolder(context).also { instance = it } }

        /** "Think About the Way (Radio Edit)" → "Think About the Way". */
        private fun withoutBrackets(title: String): String =
            title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifEmpty { title }

        /** The .lrc names that count as [song]'s, lowercased, best first. */
        internal fun namesFor(song: Song): List<String> = buildList {
            song.filePath?.substringAfterLast('/')?.substringBeforeLast('.')?.let { add(it) }
            val title = song.title.trim()
            val artist = song.artist.trim()
            if (artist.isNotEmpty() && !artist.equals("Unknown Artist", ignoreCase = true)) {
                add("$artist - $title")
                add("$artist - ${withoutBrackets(title)}")
            }
            add(title)
            add(withoutBrackets(title))
        }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    }
}

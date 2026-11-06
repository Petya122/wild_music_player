package com.wildmusicplayer.service

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Hands album covers to the car (Android Auto). Covers live at MediaStore addresses that only apps
 * allowed to read music can open; the car app may not be one of them, so it gets them through here.
 *
 * Read-only, and only album covers: content://<app>.artwork/art/<album number>. The provider takes just
 * the number and builds the MediaStore address itself, so a caller can't make it open anything else.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only")
        val segments = uri.pathSegments
        val albumId = albumIdFromPath(segments) ?: throw FileNotFoundException("Not a cover")
        val cover = ContentUris.withAppendedId(ALBUM_ART, albumId)
        return context?.contentResolver?.openFileDescriptor(cover, "r") ?: throw FileNotFoundException("No cover")
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        // Where AudioRepository's covers live (see albumArtBaseUri there)
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

        /** The address the car can open for [artUri]: phone covers go through here, web images stay as they are. */
        fun uriFor(context: Context, artUri: String?): Uri? {
            artUri ?: return null
            val albumId = albumIdOf(artUri) ?: return if (artUri.startsWith("content://media/")) null else Uri.parse(artUri)
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".artwork")
                .appendPath("art")
                .appendPath(albumId.toString())
                .build()
        }
    }
}

private val ALBUM_ART_ADDRESS = Regex("""^content://media/external/audio/albumart/(\d{1,19})$""")

/** The album number in a phone cover address ("content://media/external/audio/albumart/42" → 42), else null. */
internal fun albumIdOf(artUri: String): Long? = ALBUM_ART_ADDRESS.find(artUri)?.groupValues?.get(1)?.toLongOrNull()

/** "art/<number>" → the number; anything else (other paths, extra parts, not a number) → null. */
internal fun albumIdFromPath(segments: List<String>): Long? {
    if (segments.size != 2 || segments[0] != "art") return null
    val id = segments[1]
    if (id.isEmpty() || id.length > 19 || !id.all { it in '0'..'9' }) return null
    return id.toLongOrNull()?.takeIf { it >= 0 }
}

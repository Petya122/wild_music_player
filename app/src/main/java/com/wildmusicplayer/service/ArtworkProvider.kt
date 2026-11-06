package com.wildmusicplayer.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Hands album covers to the car (Android Auto). Covers live at MediaStore addresses that only apps
 * allowed to read music can open; the car app may not be one of them, so it gets them through here.
 * Read-only, and only MediaStore album art: content://<app>.artwork/art?u=<MediaStore URI>.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only")
        val target = uri.getQueryParameter(PARAM)?.let(Uri::parse) ?: throw FileNotFoundException("No cover")
        if (!isAlbumArt(target)) throw FileNotFoundException("Not a cover")
        return context?.contentResolver?.openFileDescriptor(target, "r") ?: throw FileNotFoundException("No cover")
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val PARAM = "u"

        private fun isAlbumArt(uri: Uri): Boolean =
            uri.scheme == "content" && uri.authority == "media" && uri.path?.contains("/audio/albumart/") == true

        /** The address the car can open for [artUri]: covers from the phone go through here, web images stay as they are. */
        fun uriFor(context: Context, artUri: String?): Uri? {
            val original = artUri?.let(Uri::parse) ?: return null
            if (!isAlbumArt(original)) return original
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".artwork")
                .path("art")
                .appendQueryParameter(PARAM, artUri)
                .build()
        }
    }
}

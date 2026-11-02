package com.wildmusicplayer.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.wildmusicplayer.model.Song

/** Deletes song files from the phone through MediaStore. */
class SongDeleter(private val context: Context) {

    sealed interface Result {
        data object Deleted : Result
        /** Android 10: the file belongs to another app; the user has to allow it first. */
        data class NeedsPermission(val intentSender: IntentSender) : Result
        data object Failed : Result
    }

    /**
     * Android 11+: Android's own dialog asks the user and deletes the files if they agree,
     * so the app shows no confirmation of its own. Null on older versions.
     */
    fun systemDeleteRequest(songs: List<Song>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(context.contentResolver, songs.map { it.contentUri }).intentSender
    }

    /** Android 10 and older: deletes right away (after the app's own confirmation). */
    fun deleteDirectly(song: Song): Result = try {
        if (context.contentResolver.delete(song.contentUri, null, null) > 0) Result.Deleted else Result.Failed
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            Result.NeedsPermission(e.userAction.actionIntent.intentSender)
        } else {
            Result.Failed
        }
    } catch (e: Exception) {
        Result.Failed
    }
}

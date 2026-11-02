package com.wildmusicplayer.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wildmusicplayer.data.AudioRepository
import com.wildmusicplayer.data.LoudnessAnalyzer
import com.wildmusicplayer.data.LoudnessStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures the loudness of every library song that hasn't been measured yet, one at a time,
 * in the background (skipped while the battery is low). Already-measured songs are skipped,
 * so running it again after new downloads only measures the new ones.
 */
class LoudnessAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = LoudnessStore.getInstance(applicationContext)
        val songs = AudioRepository(applicationContext).getLocalSongs()
        var measured = songs.count { store.isKnown(it.id) }
        store.updateProgress(measured, songs.size, running = true)

        for (song in songs) {
            if (isStopped) break
            if (store.isKnown(song.id)) continue
            store.put(song.id, LoudnessAnalyzer.measure(applicationContext, song.contentUri))
            measured++
            store.updateProgress(measured, songs.size, running = true)
        }

        store.updateProgress(measured, songs.size, running = false)
        Result.success()
    }

    companion object {
        private const val WORK_NAME = "loudness_analysis"

        /** Measures any songs still unmeasured; queued behind a run already in progress. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<LoudnessAnalysisWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

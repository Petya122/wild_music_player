package com.wildmusicplayer.ui

import android.content.Context
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The main color of a cover, picked by Android's Palette from a small copy of the image: the vibrant
 * color if there is one, otherwise the most common. Remembered per cover so each is only read once.
 */
class AndroidArtworkColors(context: Context) : ArtworkColors {

    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, Int>()

    override suspend fun colorOf(artworkUri: String): Int? {
        cache[artworkUri]?.let { return it.takeIf { color -> color != NO_COLOR } }
        val color = withContext(Dispatchers.Default) {
            try {
                val request = ImageRequest.Builder(appContext)
                    .data(artworkUri)
                    .size(SAMPLE_SIZE)
                    .allowHardware(false) // Palette needs to read the pixels
                    .build()
                val result = SingletonImageLoader.get(appContext).execute(request) as? SuccessResult
                val palette = result?.image?.toBitmap()?.let { Palette.from(it).generate() }
                (palette?.vibrantSwatch ?: palette?.dominantSwatch)?.rgb
            } catch (e: Exception) {
                null
            }
        }
        cache[artworkUri] = color ?: NO_COLOR
        return color
    }

    private companion object {
        const val SAMPLE_SIZE = 96
        const val NO_COLOR = 0
    }
}

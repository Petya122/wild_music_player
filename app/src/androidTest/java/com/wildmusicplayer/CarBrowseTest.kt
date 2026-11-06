package com.wildmusicplayer

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wildmusicplayer.data.BrowseCatalog
import com.wildmusicplayer.service.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Talks to PlaybackService the way Android Auto does: a MediaBrowser that lists tabs and folders, taps rows,
 * searches and asks by voice. Uses whatever music is on the test device (it needs a few songs with albums).
 */
@RunWith(AndroidJUnit4::class)
class CarBrowseTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var browser: MediaBrowser

    // Media3 wants its calls on the main thread; the test thread waits for the answers
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun children(parentId: String): List<MediaItem> =
        onMain { browser.getChildren(parentId, 0, 500, null) }.get(20, TimeUnit.SECONDS).value!!.toList()

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 15_000
        while (!onMain(check)) {
            if (System.currentTimeMillis() > until) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(200)
        }
    }

    @Before
    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browser = onMain { MediaBrowser.Builder(context, token).buildAsync() }.get(20, TimeUnit.SECONDS)
    }

    @After
    fun release() {
        onMain {
            browser.stop()
            browser.release()
        }
    }

    @Test
    fun rootHasFourTabs() {
        val root = onMain { browser.getLibraryRoot(null) }.get(20, TimeUnit.SECONDS).value!!
        val tabs = children(root.mediaId)
        assertEquals(listOf("Home", "Playlists", "Albums", "Artists"), tabs.map { it.mediaMetadata.title.toString() })
        assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true })
    }

    @Test
    fun tappingAnAlbumSongPlaysTheAlbumFromThatSong() {
        val album = children(BrowseCatalog.TAB_ALBUMS).first { children(it.mediaId).size >= 3 }
        val rows = children(album.mediaId)
        assertEquals("Shuffle", rows.first().mediaMetadata.title.toString())
        val tapped = rows[2]
        assertEquals(true, tapped.mediaMetadata.isPlayable)

        // The car sends just the row's ID
        onMain {
            browser.setMediaItem(MediaItem.Builder().setMediaId(tapped.mediaId).build())
            browser.prepare()
        }
        waitFor("the album to load") { browser.mediaItemCount == rows.size - 1 }
        assertEquals(1, onMain { browser.currentMediaItemIndex })
        assertEquals(tapped.mediaMetadata.title, onMain { browser.currentMediaItem?.mediaMetadata?.title })
    }

    @Test
    fun shuffleRowLoadsTheWholeList() {
        val home = children(BrowseCatalog.TAB_HOME)
        val shuffleAll = home.first { it.mediaMetadata.title.toString() == "Shuffle all" }
        onMain {
            browser.setMediaItem(MediaItem.Builder().setMediaId(shuffleAll.mediaId).build())
            browser.prepare()
        }
        waitFor("all songs to load") { browser.mediaItemCount > 3 }
    }

    @Test
    fun searchFindsSongsThatCanBePlayed() {
        val anySong = children(BrowseCatalog.TAB_ALBUMS).first().let { children(it.mediaId)[1] }
        val word = anySong.mediaMetadata.title.toString().split(' ').first { it.length >= 3 }
        val count = onMain { browser.search(word, null) }.get(20, TimeUnit.SECONDS)
        assertTrue(count.resultCode == 0)
        val results = onMain { browser.getSearchResult(word, 0, 50, null) }.get(20, TimeUnit.SECONDS).value!!
        assertFalse(results.isEmpty())
        assertTrue(results.all { it.mediaMetadata.title.toString().contains(word, ignoreCase = true) || it.mediaMetadata.artist.toString().contains(word, ignoreCase = true) || it.mediaMetadata.albumTitle.toString().contains(word, ignoreCase = true) })
    }

    @Test
    fun voiceRequestForAnArtistPlaysTheirSongs() {
        val artist = children(BrowseCatalog.TAB_ARTISTS).first { it.mediaMetadata.title.toString() != "Unknown Artist" }
        val name = artist.mediaMetadata.title.toString()
        val songCount = children(artist.mediaId).size - 1
        // "Hey Google, play <artist> on Wild Music"
        onMain {
            browser.setMediaItem(
                MediaItem.Builder().setRequestMetadata(
                    MediaItem.RequestMetadata.Builder().setSearchQuery(name).build()
                ).build()
            )
            browser.prepare()
        }
        waitFor("the artist's songs") { browser.mediaItemCount == songCount }
        val artists = onMain { (0 until browser.mediaItemCount).map { browser.getMediaItemAt(it).mediaMetadata.artist.toString() } }
        assertTrue(artists.all { it.equals(name, ignoreCase = true) })
    }

    @Test
    fun coversOpenThroughTheArtworkProvider() {
        val withCover = children(BrowseCatalog.TAB_ALBUMS).mapNotNull { it.mediaMetadata.artworkUri }
            .firstOrNull { it.authority == context.packageName + ".artwork" }
            ?: return // no MediaStore covers on this device
        val bytes = context.contentResolver.openInputStream(withCover)!!.use { it.readBytes() }
        assertTrue(bytes.size > 100)
    }
}

package com.wildmusicplayer

import com.wildmusicplayer.data.youTubeVideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoIdTest {

    @Test
    fun sameVideoFromYouTubeAndYouTubeMusic() {
        assertEquals("cvjIvVyuNno", youTubeVideoId("https://www.youtube.com/watch?v=cvjIvVyuNno"))
        assertEquals("cvjIvVyuNno", youTubeVideoId("https://music.youtube.com/watch?v=cvjIvVyuNno&list=RDAMVM"))
        assertEquals("cvjIvVyuNno", youTubeVideoId("https://youtu.be/cvjIvVyuNno?si=abc"))
    }

    @Test
    fun otherParametersBeforeV() {
        assertEquals("cvjIvVyuNno", youTubeVideoId("https://www.youtube.com/watch?feature=share&v=cvjIvVyuNno"))
    }

    @Test
    fun shortsAndEmbeds() {
        assertEquals("abcDEF12345", youTubeVideoId("https://www.youtube.com/shorts/abcDEF12345"))
        assertEquals("abcDEF12345", youTubeVideoId("https://www.youtube.com/embed/abcDEF12345"))
    }

    @Test
    fun notAVideoLink() {
        assertNull(youTubeVideoId("https://www.youtube.com/channel/UC123"))
        assertNull(youTubeVideoId("promise man"))
    }
}

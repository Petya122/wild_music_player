package com.wildmusicplayer

import com.wildmusicplayer.data.UpdateSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSafetyTest {

    @Test
    fun onlyThisRepositorysReleaseFiles() {
        assertTrue(UpdateSafety.isReleaseUrl("https://github.com/Petya122/wild_music_player/releases/download/v0.9.7/wild_music_player-v0.9.7.apk"))
        assertFalse(UpdateSafety.isReleaseUrl("https://github.com/someone/else/releases/download/v1/app.apk"))
        assertFalse(UpdateSafety.isReleaseUrl("http://github.com/Petya122/wild_music_player/releases/download/v0.9.7/wild_music_player-v0.9.7.apk"))
        assertFalse(UpdateSafety.isReleaseUrl("https://github.com/Petya122/wild_music_player/releases/download/../../x/evil.apk"))
        assertFalse(UpdateSafety.isReleaseUrl("https://github.com/Petya122/wild_music_player/releases/download/v0.9.7/notes.txt"))
    }

    @Test
    fun downloadsMayOnlyMoveToGitHubsFileServers() {
        assertTrue(UpdateSafety.isAllowedRedirect("https://objects.githubusercontent.com/github-production-release-asset/123"))
        assertTrue(UpdateSafety.isAllowedRedirect("https://release-assets.githubusercontent.com/x"))
        assertFalse(UpdateSafety.isAllowedRedirect("http://objects.githubusercontent.com/x"))
        assertFalse(UpdateSafety.isAllowedRedirect("https://githubusercontent.com.evil.example/x"))
        assertFalse(UpdateSafety.isAllowedRedirect("https://evil.example/github.com"))
        assertFalse(UpdateSafety.isAllowedRedirect("not a url"))
    }

    private fun problem(pkg: String? = "com.wildmusicplayer", version: Long = 54, certs: Set<String>? = setOf("cdbd")) =
        UpdateSafety.problemWith(pkg, version, certs, "com.wildmusicplayer", 53, setOf("cdbd"))

    @Test
    fun onlyANewerWildMusicWithTheSameKeyIsInstalled() {
        assertNull(problem())
        assertEquals("The update file isn't Wild Music", problem(pkg = "com.evil.app"))
        assertEquals("The update file isn't newer than this version", problem(version = 53))
        assertEquals("The update file isn't signed by Wild Music", problem(certs = setOf("0bad")))
        assertEquals("The update file is damaged", problem(pkg = null))
        // Android couldn't read the certificates: the package and version checks still apply
        assertNull(problem(certs = null))
    }
}

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

    private fun problem(
        pkg: String? = "com.wildmusicplayer",
        version: Long = 54,
        certs: Set<String>? = setOf("cdbd"),
        ownCerts: Set<String>? = setOf("cdbd")
    ) = UpdateSafety.problemWith(pkg, version, certs, "com.wildmusicplayer", 53, ownCerts)

    @Test
    fun onlyANewerWildMusicWithTheSameKeyIsInstalled() {
        assertNull(problem())
        assertEquals("The update file isn't Wild Music", problem(pkg = "com.evil.app"))
        assertEquals("The update file isn't newer than this version", problem(version = 53))
        assertEquals("The update file isn't signed by Wild Music", problem(certs = setOf("0bad")))
        assertEquals("The update file is damaged", problem(pkg = null))
        // A key rotation keeps the old certificate in the history: one shared certificate is enough
        assertNull(problem(certs = setOf("new1", "cdbd")))
    }

    @Test
    fun whatCantBeCheckedIsntInstalled() {
        // Android couldn't read the file's certificates, or this app's own
        assertEquals(UpdateSafety.UNVERIFIED, problem(certs = null))
        assertEquals(UpdateSafety.UNVERIFIED, problem(certs = emptySet()))
        assertEquals(UpdateSafety.UNVERIFIED, problem(ownCerts = null))
        assertEquals(UpdateSafety.UNVERIFIED, problem(ownCerts = emptySet()))
        // The cheaper answers still come first
        assertEquals("The update file isn't Wild Music", problem(pkg = "com.evil.app", certs = null))
    }
}

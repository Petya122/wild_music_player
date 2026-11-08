package com.wildmusicplayer.data

import java.net.URI

/**
 * What the in-app updater accepts: release files from this repository only (and GitHub's own download
 * hosts it redirects to), and an APK that really is a newer Wild Music signed with the same key.
 * Without the checks, a tampered release could make the updater install some other app.
 */
internal object UpdateSafety {

    private const val RELEASE_PREFIX = "https://github.com/Petya122/wild_music_player/releases/download/"

    /** A download link from the release notes: only this repository's release files. */
    fun isReleaseUrl(url: String): Boolean =
        url.startsWith(RELEASE_PREFIX) && !url.contains("..") && url.endsWith(".apk", ignoreCase = true)

    /** Where GitHub may send the download: itself or its file servers, always over HTTPS. */
    fun isAllowedRedirect(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && (host == "github.com" || host.endsWith(".githubusercontent.com"))
    }

    /**
     * Why the downloaded APK must not be installed, or null when it's a genuine update: the same package,
     * a newer version, and the same signing certificates. What can't be checked isn't installed: when
     * Android can't read the certificates (of the file or of this app), the answer is no.
     */
    fun problemWith(
        apkPackage: String?,
        apkVersionCode: Long,
        apkCertificates: Set<String>?,
        ownPackage: String,
        ownVersionCode: Long,
        ownCertificates: Set<String>?
    ): String? = when {
        apkPackage == null -> "The update file is damaged"
        apkPackage != ownPackage -> "The update file isn't Wild Music"
        apkVersionCode <= ownVersionCode -> "The update file isn't newer than this version"
        apkCertificates.isNullOrEmpty() || ownCertificates.isNullOrEmpty() -> UNVERIFIED
        apkCertificates.intersect(ownCertificates).isEmpty() -> "The update file isn't signed by Wild Music"
        else -> null
    }

    /** The answer when the signature can't be checked at all. */
    const val UNVERIFIED = "The update file's signature couldn't be checked"
}

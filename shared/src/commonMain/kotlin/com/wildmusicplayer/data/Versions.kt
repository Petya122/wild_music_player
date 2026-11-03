package com.wildmusicplayer.data

/** "0.10.0" is newer than "0.9.9": compares dot-separated numbers, missing parts count as 0. */
fun isNewerVersion(remote: String, current: String): Boolean {
    val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
    val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

    val maxLength = maxOf(remoteParts.size, currentParts.size)
    for (i in 0 until maxLength) {
        val r = remoteParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
    }
    return false
}

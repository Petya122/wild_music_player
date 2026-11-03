package com.wildmusicplayer.data

/**
 * Lowercase, accents removed, punctuation to spaces:
 * "Move On, Baby! (Official)" → "move on baby official", "Beyoncé" → "beyonce".
 */
fun normalizeForSearch(text: String): String =
    decomposeUnicode(text.lowercase())
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

/** Unicode NFD: "é" becomes "e" followed by a combining accent, which [normalizeForSearch] then drops. */
internal expect fun decomposeUnicode(text: String): String

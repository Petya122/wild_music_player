package com.wildmusicplayer.data

/**
 * Lowercase, accents removed, punctuation to spaces:
 * "Move On, Baby! (Official)" → "move on baby official", "Beyoncé" → "beyonce".
 */
fun normalizeForSearch(text: String): String =
    wordsOf(
        decomposeUnicode(text.lowercase()).filter { it.category != CharCategory.NON_SPACING_MARK }
    ).joinToString(" ")

/**
 * Splits [text] into words of letters and numbers (plus any [extraWordChars]), dropping everything else.
 * Character checks instead of `\p{L}` regex classes, which iOS's regex engine doesn't support.
 */
fun wordsOf(text: String, extraWordChars: String = ""): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    for (c in text) {
        if (c.isLetterOrNumber() || c in extraWordChars) {
            current.append(c)
        } else if (current.isNotEmpty()) {
            words += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) words += current.toString()
    return words
}

private val numberCategories = setOf(
    CharCategory.DECIMAL_DIGIT_NUMBER, CharCategory.LETTER_NUMBER, CharCategory.OTHER_NUMBER
)

private fun Char.isLetterOrNumber(): Boolean = isLetter() || category in numberCategories

/** Unicode NFD: "é" becomes "e" followed by a combining accent, which [normalizeForSearch] then drops. */
internal expect fun decomposeUnicode(text: String): String

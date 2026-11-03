package com.wildmusicplayer.data

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decomposeUnicode(text: String): String =
    (text as NSString).decomposedStringWithCanonicalMapping

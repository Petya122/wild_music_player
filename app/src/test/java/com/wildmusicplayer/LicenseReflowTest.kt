package com.wildmusicplayer

import com.wildmusicplayer.ui.components.reflowLicense
import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseReflowTest {

    @Test
    fun paragraphsBecomeOneLineAndUnderlinesGo() {
        val text = "2. License Grants\r\n------------------\r\n\r\n  Each Contributor hereby\r\n  grants You a license:\r\n\r\n\r\n(a) under rights\n    to use\n"
        assertEquals(
            "2. License Grants\n\nEach Contributor hereby grants You a license:\n\n(a) under rights to use",
            reflowLicense(text),
        )
    }
}

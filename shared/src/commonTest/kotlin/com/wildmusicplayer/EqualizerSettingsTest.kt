package com.wildmusicplayer

import com.wildmusicplayer.platform.EqualizerSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class EqualizerSettingsTest {

    @Test
    fun roundTrip() {
        val custom = EqualizerSettings(isEnabled = true, presetIndex = -1, bandLevelsMb = listOf(300, -150, 0, 450, -1500))
        assertEquals(custom, EqualizerSettings.decode(custom.encode()))

        val preset = EqualizerSettings(isEnabled = false, presetIndex = 3)
        assertEquals(preset, EqualizerSettings.decode(preset.encode()))
    }

    @Test
    fun nothingSavedOrBrokenMeansOff() {
        assertEquals(EqualizerSettings(), EqualizerSettings.decode(null))
        assertEquals(EqualizerSettings(), EqualizerSettings.decode("garbage"))
    }
}

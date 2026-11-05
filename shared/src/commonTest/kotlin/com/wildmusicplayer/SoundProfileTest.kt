package com.wildmusicplayer

import com.wildmusicplayer.platform.soundProfileId
import com.wildmusicplayer.platform.soundProfileKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundProfileTest {

    @Test
    fun phoneSettingsKeepTheirOldNames() {
        assertEquals("pref_player_volume", soundProfileKey("pref_player_volume", null))
        assertEquals("pref_player_volume@bt:AA:BB", soundProfileKey("pref_player_volume", "bt:AA:BB"))
    }

    @Test
    fun devicesAreKnownByAddressThenName() {
        assertEquals("bt:AA:BB:CC:DD:EE:FF", soundProfileId("Nissan Connect", "AA:BB:CC:DD:EE:FF"))
        // Hidden addresses (privacy) fall back to the name
        assertEquals("bt:Nissan Connect", soundProfileId("Nissan Connect", "02:00:00:00:00:00"))
        assertEquals("bt:Nissan Connect", soundProfileId(" Nissan Connect ", ""))
        assertEquals("bt:unknown", soundProfileId(null, null))
    }
}

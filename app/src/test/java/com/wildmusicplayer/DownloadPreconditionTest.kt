package com.wildmusicplayer

import com.wildmusicplayer.data.DownloadNetworkMode
import com.wildmusicplayer.data.DownloadPrecondition
import com.wildmusicplayer.data.decideDownloadPrecondition
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPreconditionTest {

    private fun decide(
        offline: Boolean = false,
        connected: Boolean = true,
        wifi: Boolean = false,
        cellular: Boolean = false,
        metered: Boolean = false,
        mode: DownloadNetworkMode = DownloadNetworkMode.WIFI_ONLY,
        blockHotspots: Boolean = true
    ) = decideDownloadPrecondition(offline, connected, wifi, cellular, metered, mode, blockHotspots)

    @Test
    fun offlineModeBlocksEvenWithWifi() {
        assertEquals(DownloadPrecondition.BlockedOfflineMode, decide(offline = true, wifi = true))
    }

    @Test
    fun noConnectionIsBlocked() {
        assertEquals(DownloadPrecondition.BlockedNoNetwork, decide(connected = false))
    }

    @Test
    fun unmeteredWifiIsAllowed() {
        assertEquals(DownloadPrecondition.Allowed, decide(wifi = true))
    }

    @Test
    fun meteredHotspotPromptsWhenBlockingIsOn() {
        assertEquals(
            DownloadPrecondition.PromptMobileData(isMetered = true),
            decide(wifi = true, metered = true)
        )
    }

    @Test
    fun meteredHotspotAllowedWhenBlockingIsOff() {
        assertEquals(DownloadPrecondition.Allowed, decide(wifi = true, metered = true, blockHotspots = false))
    }

    @Test
    fun cellularPromptsInWifiOnlyAndAskModes() {
        assertEquals(
            DownloadPrecondition.PromptMobileData(isMetered = true),
            decide(cellular = true, metered = true, mode = DownloadNetworkMode.WIFI_ONLY)
        )
        assertEquals(
            DownloadPrecondition.PromptMobileData(isMetered = true),
            decide(cellular = true, metered = true, mode = DownloadNetworkMode.ASK)
        )
    }

    @Test
    fun cellularAllowedInAlwaysAllowMode() {
        assertEquals(
            DownloadPrecondition.Allowed,
            decide(cellular = true, metered = true, mode = DownloadNetworkMode.ALWAYS_ALLOW)
        )
    }
}

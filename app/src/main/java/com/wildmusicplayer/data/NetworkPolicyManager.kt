package com.wildmusicplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

enum class DownloadNetworkMode(val displayName: String, val description: String) {
    WIFI_ONLY("Wi-Fi Only", "Downloads are only permitted when connected to Wi-Fi"),
    ASK("Ask on Mobile Data", "Prompt for confirmation before downloading on cellular data"),
    ALWAYS_ALLOW("Always Allow", "Downloads can run on both Wi-Fi and mobile data")
}

sealed interface DownloadPrecondition {
    object Allowed : DownloadPrecondition
    object BlockedNoNetwork : DownloadPrecondition
    object BlockedMetered : DownloadPrecondition
    object BlockedOfflineMode : DownloadPrecondition
    data class PromptMobileData(val isMetered: Boolean) : DownloadPrecondition
}

class NetworkPolicyManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wild_music_player_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_OFFLINE_ONLY_MODE = "key_offline_only_mode"
        const val KEY_DOWNLOAD_NETWORK_MODE = "key_download_network_mode"
        const val KEY_BLOCK_METERED_HOTSPOTS = "key_block_metered_hotspots"
        const val KEY_BACKGROUND_DOWNLOADS = "key_background_downloads"
    }

    var isOfflineOnlyMode: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_ONLY_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_OFFLINE_ONLY_MODE, value).apply()
        }

    var downloadNetworkMode: DownloadNetworkMode
        get() {
            val saved = prefs.getString(KEY_DOWNLOAD_NETWORK_MODE, DownloadNetworkMode.WIFI_ONLY.name)
            return try {
                DownloadNetworkMode.valueOf(saved ?: DownloadNetworkMode.WIFI_ONLY.name)
            } catch (_: Exception) {
                DownloadNetworkMode.WIFI_ONLY
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DOWNLOAD_NETWORK_MODE, value.name).apply()
        }

    var blockMeteredHotspots: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_METERED_HOTSPOTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BLOCK_METERED_HOTSPOTS, value).apply()
        }

    var backgroundDownloads: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_DOWNLOADS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BACKGROUND_DOWNLOADS, value).apply()
        }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isCellularConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun isMeteredConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkDownloadPrecondition(): DownloadPrecondition {
        if (isOfflineOnlyMode) {
            return DownloadPrecondition.BlockedOfflineMode
        }

        if (!isConnected()) {
            return DownloadPrecondition.BlockedNoNetwork
        }

        val metered = isMeteredConnection()
        val cellular = isCellularConnected()
        val wifi = isWifiConnected()

        // If connected to a metered Wi-Fi / hotspot and blocking metered is active
        if (wifi && metered && blockMeteredHotspots && downloadNetworkMode == DownloadNetworkMode.WIFI_ONLY) {
            return DownloadPrecondition.PromptMobileData(isMetered = true)
        }

        if (cellular) {
            return when (downloadNetworkMode) {
                DownloadNetworkMode.WIFI_ONLY -> DownloadPrecondition.PromptMobileData(isMetered = metered)
                DownloadNetworkMode.ASK -> DownloadPrecondition.PromptMobileData(isMetered = metered)
                DownloadNetworkMode.ALWAYS_ALLOW -> DownloadPrecondition.Allowed
            }
        }

        return DownloadPrecondition.Allowed
    }
}

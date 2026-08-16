package com.bobsdevattic.networkanalyzer.network

/** Radio band a WiFi network operates on. */
enum class WifiBand(val label: String) {
    GHZ_2_4("2.4 GHz"),
    GHZ_5("5 GHz"),
    GHZ_6("6 GHz"),
    UNKNOWN("—"),
}

/** A single access point seen in a scan. */
data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: WifiBand,
    val widthMhz: Int?,
    val security: String,
    val isCurrent: Boolean = false,
) {
    /** 0..4 bars from RSSI, matching Android's signal-level buckets. */
    val signalBars: Int
        get() = when {
            rssiDbm >= -55 -> 4
            rssiDbm >= -66 -> 3
            rssiDbm >= -77 -> 2
            rssiDbm >= -88 -> 1
            else -> 0
        }
}

/** The phone's current WiFi association, if any. */
data class CurrentWifi(
    val ssid: String,
    val bssid: String?,
    val rssiDbm: Int,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val band: WifiBand,
)

/** How many APs occupy a given channel (for congestion view). */
data class ChannelLoad(
    val band: WifiBand,
    val channel: Int,
    val count: Int,
)

/** UI-facing state for the WiFi screen. */
data class WifiState(
    val scanning: Boolean = false,
    val current: CurrentWifi? = null,
    val aps: List<WifiAp> = emptyList(),
    val channelLoads: List<ChannelLoad> = emptyList(),
    val message: String? = null,
)

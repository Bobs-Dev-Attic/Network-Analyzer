package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Wraps [WifiManager] to list nearby access points and the current association.
 *
 * Uses the phone's own WiFi radio — independent of the USB Ethernet link.
 * Requires the caller to hold the scan permission (NEARBY_WIFI_DEVICES on 33+,
 * ACCESS_FINE_LOCATION below that); [SecurityException] is caught and surfaced
 * as empty results so the UI can prompt for the grant.
 */
class WifiScanner(context: Context) {

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    val isWifiEnabled: Boolean get() = wifi.isWifiEnabled

    /** Ask the framework to run a fresh scan. Deprecated + throttled since API 28. */
    @Suppress("DEPRECATION")
    fun requestScan(): Boolean = runCatching { wifi.startScan() }.getOrDefault(false)

    /** Latest scan results as APs, or empty if permission/location is missing. */
    fun accessPoints(currentBssid: String?): List<WifiAp> = runCatching {
        wifi.scanResults.map { it.toAp(currentBssid) }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    fun current(): CurrentWifi? = runCatching {
        val info = wifi.connectionInfo ?: return null
        val bssid = info.bssid
        // networkId -1 and a null/blank BSSID both mean "not associated".
        if (bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00") return null

        val ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info.frequency else null

        CurrentWifi(
            ssid = ssid ?: "(hidden)",
            bssid = bssid,
            rssiDbm = info.rssi,
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            frequencyMhz = freq,
            channel = freq?.let { WifiChannels.channelFor(it) },
            band = freq?.let { WifiChannels.bandFor(it) } ?: WifiBand.UNKNOWN,
        )
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun ScanResult.toAp(currentBssid: String?): WifiAp {
        val name = SSID?.takeIf { it.isNotBlank() } ?: "(hidden)"
        val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WifiChannels.widthMhz(channelWidth)
        } else null
        return WifiAp(
            ssid = name,
            bssid = BSSID.orEmpty(),
            rssiDbm = level,
            frequencyMhz = frequency,
            channel = WifiChannels.channelFor(frequency),
            band = WifiChannels.bandFor(frequency),
            widthMhz = width,
            security = WifiChannels.security(capabilities),
            isCurrent = currentBssid != null && BSSID.equals(currentBssid, ignoreCase = true),
        )
    }
}

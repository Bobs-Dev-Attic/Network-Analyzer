package com.bobsdevattic.networkanalyzer.network

import android.net.wifi.ScanResult

/** Frequency → channel/band conversions and channel-width mapping. */
object WifiChannels {

    fun bandFor(freqMhz: Int): WifiBand = when (freqMhz) {
        in 2401..2495 -> WifiBand.GHZ_2_4
        in 4900..5895 -> WifiBand.GHZ_5
        in 5925..7125 -> WifiBand.GHZ_6
        else -> WifiBand.UNKNOWN
    }

    fun channelFor(freqMhz: Int): Int = when (freqMhz) {
        2484 -> 14
        in 2401..2472 -> (freqMhz - 2407) / 5
        in 4900..5895 -> (freqMhz - 5000) / 5
        in 5925..7125 -> (freqMhz - 5950) / 5
        else -> 0
    }

    /** ScanResult.channelWidth enum → nominal width in MHz. */
    fun widthMhz(channelWidth: Int): Int? = when (channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
        else -> null
    }

    /** Best-effort security label from the capabilities string. */
    fun security(capabilities: String?): String {
        val caps = capabilities.orEmpty()
        return when {
            caps.contains("WPA3") || caps.contains("SAE") -> "WPA3"
            caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            caps.contains("OWE") -> "Open (OWE)"
            else -> "Open"
        }
    }
}

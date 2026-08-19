package com.bobsdevattic.networkanalyzer.network

/**
 * One signal reading placed on the map. [x] and [y] are normalized (0..1)
 * positions on the grid; [rssiDbm] is the connected network's signal there.
 */
data class SignalSample(
    val x: Float,
    val y: Float,
    val rssiDbm: Int,
)

/** UI-facing state for the blank-grid signal heatmap. */
data class HeatmapState(
    val samples: List<SignalSample> = emptyList(),
    val connected: Boolean = false,
    val currentRssi: Int? = null,
    val ssid: String? = null,
    val message: String? = null,
)

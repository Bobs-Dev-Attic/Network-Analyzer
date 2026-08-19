package com.bobsdevattic.networkanalyzer.network

/**
 * One signal reading placed on the map. Coordinate meaning depends on the mode:
 * in GRID mode [x]/[y] are normalized (0..1) tap positions; in AR mode they are
 * real-world metres (ground-plane X/Z) from the tracking origin, normalized to
 * 0..1 only at render time.
 */
data class SignalSample(
    val x: Float,
    val y: Float,
    val rssiDbm: Int,
)

/** How the map collects positions. */
enum class MapMode { GRID, AR }

/** UI-facing state for the signal heatmap. */
data class HeatmapState(
    val mode: MapMode = MapMode.GRID,
    /** Manual tap samples (normalized 0..1). */
    val gridSamples: List<SignalSample> = emptyList(),
    /** Camera-tracked samples (real-world metres, ground-plane X/Z). */
    val arSamples: List<SignalSample> = emptyList(),
    /** ARCore is currently tracking the camera pose. */
    val tracking: Boolean = false,
    val connected: Boolean = false,
    val currentRssi: Int? = null,
    val ssid: String? = null,
    val message: String? = null,
) {
    /** Samples for the currently active mode. */
    val activeSamples: List<SignalSample>
        get() = if (mode == MapMode.AR) arSamples else gridSamples
}

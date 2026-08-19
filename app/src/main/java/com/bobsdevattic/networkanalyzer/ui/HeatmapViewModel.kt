package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.HeatmapState
import com.bobsdevattic.networkanalyzer.network.MapMode
import com.bobsdevattic.networkanalyzer.network.SignalSample
import com.bobsdevattic.networkanalyzer.network.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Builds a signal heatmap in two modes:
 *  - GRID: tap a blank grid where you're standing.
 *  - AR: the camera (ARCore) tracks your position as you walk and auto-records
 *    the signal every ~step, so the map fills in to real-world scale.
 *
 * The connected network's RSSI (via connectionInfo — not scan-throttled) is
 * polled so the header shows a live reading and AR samples use the latest value.
 */
class HeatmapViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(HeatmapState())
    val state: StateFlow<HeatmapState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var lastArX: Float? = null
    private var lastArZ: Float? = null

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val current = withContext(Dispatchers.IO) { scanner.current() }
                _state.update {
                    it.copy(
                        connected = current != null,
                        currentRssi = current?.rssiDbm,
                        ssid = current?.ssid,
                    )
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun setMode(mode: MapMode) = _state.update { it.copy(mode = mode, message = null) }

    /** GRID: record a sample at a normalized (0..1) grid position, using live RSSI. */
    fun addGridSample(x: Float, y: Float) {
        val rssi = _state.value.currentRssi
        if (rssi == null) {
            _state.update { it.copy(message = "Connect to a WiFi network to record a reading.") }
            return
        }
        _state.update {
            it.copy(gridSamples = it.gridSamples + SignalSample(x, y, rssi), message = null)
        }
    }

    /** AR: whether ARCore is currently tracking (drives the status line). */
    fun setTracking(tracking: Boolean) {
        if (_state.value.tracking != tracking) _state.update { it.copy(tracking = tracking) }
    }

    /**
     * AR: a new camera pose (ground-plane metres). Records a sample when we've
     * moved at least [AR_STEP_M] from the last one, tagging it with the live RSSI.
     * Called from the AR frame callback, so it stays cheap and non-blocking.
     */
    fun recordArPose(x: Float, z: Float) {
        val rssi = _state.value.currentRssi ?: return
        val lx = lastArX
        val lz = lastArZ
        if (lx != null && lz != null &&
            hypot((x - lx).toDouble(), (z - lz).toDouble()) < AR_STEP_M
        ) {
            return
        }
        lastArX = x
        lastArZ = z
        _state.update { it.copy(arSamples = it.arSamples + SignalSample(x, z, rssi)) }
    }

    fun undo() = _state.update {
        when (it.mode) {
            MapMode.GRID -> it.copy(gridSamples = it.gridSamples.dropLast(1))
            MapMode.AR -> it.copy(arSamples = it.arSamples.dropLast(1))
        }
    }

    fun clear() {
        lastArX = null
        lastArZ = null
        _state.update {
            when (it.mode) {
                MapMode.GRID -> it.copy(gridSamples = emptyList(), message = null)
                MapMode.AR -> it.copy(arSamples = emptyList(), message = null)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private companion object {
        const val POLL_MS = 1000L
        const val AR_STEP_M = 0.4 // record a new sample every ~0.4 m walked
    }
}

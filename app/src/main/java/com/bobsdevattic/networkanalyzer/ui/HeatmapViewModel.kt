package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.HeatmapState
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

/**
 * Builds a signal heatmap from manually-placed samples. The connected network's
 * RSSI (via connectionInfo — not scan-throttled) is polled so the header shows a
 * live reading, and each tap records the signal at that grid position.
 */
class HeatmapViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(HeatmapState())
    val state: StateFlow<HeatmapState> = _state.asStateFlow()

    private var pollJob: Job? = null

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

    /** Record a sample at a normalized (0..1) grid position, using the live RSSI. */
    fun addSample(x: Float, y: Float) {
        viewModelScope.launch {
            val current = withContext(Dispatchers.IO) { scanner.current() }
            if (current == null) {
                _state.update {
                    it.copy(message = "Connect to a WiFi network to record a reading.")
                }
                return@launch
            }
            _state.update {
                it.copy(
                    samples = it.samples + SignalSample(x, y, current.rssiDbm),
                    message = null,
                )
            }
        }
    }

    fun undo() = _state.update { it.copy(samples = it.samples.dropLast(1)) }

    fun clear() = _state.update { it.copy(samples = emptyList(), message = null) }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private companion object {
        const val POLL_MS = 1000L
    }
}

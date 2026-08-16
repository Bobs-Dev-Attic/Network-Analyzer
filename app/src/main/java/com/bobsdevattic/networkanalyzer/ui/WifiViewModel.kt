package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.bobsdevattic.networkanalyzer.network.ChannelLoad
import com.bobsdevattic.networkanalyzer.network.WifiScanner
import com.bobsdevattic.networkanalyzer.network.WifiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Surfaces nearby WiFi APs, channel congestion, and the current association.
 *
 * Listens for SCAN_RESULTS_AVAILABLE so fresh scans update the UI as they land;
 * [scan] also requests a new scan (subject to Android's scan throttling) and
 * immediately reads the cached results. Call [refresh] once the scan permission
 * has been granted.
 */
class WifiViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(WifiState())
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = load()
    }

    init {
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Trigger a fresh scan and load whatever results are already cached. */
    fun scan() {
        _state.value = _state.value.copy(scanning = true)
        scanner.requestScan()
        load()
    }

    /** Re-read results (e.g. after the permission was just granted). */
    fun refresh() = load()

    private fun load() {
        if (!scanner.isWifiEnabled) {
            _state.value = WifiState(scanning = false, message = "WiFi is turned off.")
            return
        }

        val current = scanner.current()
        val aps = scanner.accessPoints(current?.bssid)
            .sortedByDescending { it.rssiDbm }

        val loads = aps
            .filter { it.channel > 0 }
            .groupBy { it.band to it.channel }
            .map { (key, list) -> ChannelLoad(key.first, key.second, list.size) }
            .sortedWith(compareByDescending<ChannelLoad> { it.count }.thenBy { it.channel })

        _state.value = WifiState(
            scanning = false,
            current = current,
            aps = aps,
            channelLoads = loads,
            message = if (aps.isEmpty()) {
                "No networks found. On Android 12 and below, location services must " +
                    "be ON for scan results, and scans are rate-limited."
            } else null,
        )
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
    }
}

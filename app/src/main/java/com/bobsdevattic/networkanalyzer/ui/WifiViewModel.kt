package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.ChannelLoad
import com.bobsdevattic.networkanalyzer.network.WifiScanner
import com.bobsdevattic.networkanalyzer.network.WifiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Surfaces nearby WiFi APs, channel congestion, and the current association.
 *
 * Scanning is asynchronous: [scan] requests a fresh scan and shows any cached
 * results immediately, but keeps a "scanning" state until either the
 * SCAN_RESULTS_AVAILABLE broadcast lands (fresh results) or a fallback timeout
 * elapses. Only then does it finalize an empty result — and the message it shows
 * is version-aware (Location-services off, scan throttled, or genuinely empty)
 * so the user knows what to fix.
 */
class WifiViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = WifiScanner(app)

    private val _state = MutableStateFlow(WifiState())
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private var finalizeJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        // A completed scan (ours or the system's) — populate if it has results,
        // but don't finalize an empty message here; the poll loop decides that.
        override fun onReceive(context: Context?, intent: Intent?) = load(finalize = false)
    }

    init {
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * Request a fresh scan and show cached results now. Because Android throttles
     * app-initiated scans, we then poll [scanner]'s results for a while — the
     * system scans on its own and our SCAN_RESULTS_AVAILABLE receiver also feeds
     * in — finalizing as soon as any AP appears, or with a message if the window
     * elapses empty.
     */
    fun scan() {
        _state.value = _state.value.copy(scanning = true, message = null)
        val started = scanner.requestScan()
        load(finalize = false)

        finalizeJob?.cancel()
        finalizeJob = viewModelScope.launch {
            var waited = 0L
            while (waited < SCAN_MAX_WAIT_MS) {
                delay(SCAN_POLL_MS)
                waited += SCAN_POLL_MS
                if (scanner.isWifiEnabled &&
                    scanner.accessPoints(scanner.current()?.bssid).isNotEmpty()
                ) {
                    load(finalize = false) // populates and stops the spinner
                    return@launch
                }
            }
            load(finalize = true, scanStarted = started)
        }
    }

    /** Re-read/scan (e.g. once the permission was just granted). */
    fun refresh() = scan()

    private fun load(finalize: Boolean, scanStarted: Boolean = true) {
        if (!scanner.isWifiEnabled) {
            finalizeJob?.cancel()
            _state.value = WifiState(scanning = false, message = "WiFi is turned off.")
            return
        }

        val current = scanner.current()
        val aps = scanner.accessPoints(current?.bssid)
            .sortedByDescending { it.rssiDbm }

        if (aps.isNotEmpty()) {
            finalizeJob?.cancel()
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
                message = null,
            )
            return
        }

        // No APs yet.
        if (!finalize) {
            // Keep waiting for the scan to complete; show the current link meanwhile.
            _state.value = WifiState(scanning = true, current = current)
            return
        }

        _state.value = WifiState(
            scanning = false,
            current = current,
            message = emptyMessage(scanStarted),
        )
    }

    private fun emptyMessage(scanStarted: Boolean): String = when {
        scanner.lastError != null ->
            "Couldn't read scan results: ${scanner.lastError}. This usually means the " +
                "Location permission or the system Location switch is still required — " +
                "grant Location and turn on system Location, then tap Scan."
        !isLocationEnabled() ->
            "No networks found. Turn on system Location (Settings → Location) — most " +
                "phones require it for WiFi scans even when the app permission is " +
                "granted — then tap Scan."
        !scanStarted ->
            "Android limits how often apps can trigger a WiFi scan, so this is showing " +
                "the system's cached results. Tap Scan again in a few seconds to refresh."
        else ->
            "No networks found. Check that WiFi is on and this app has the " +
                "Nearby-devices (or Location) permission, then tap Scan."
    }

    /** Whether the system Location master switch is on (gates scan results on many devices). */
    private fun isLocationEnabled(): Boolean = runCatching {
        val ctx = getApplication<Application>()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            val mode = Settings.Secure.getInt(
                ctx.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }.getOrDefault(true)

    override fun onCleared() {
        super.onCleared()
        finalizeJob?.cancel()
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
    }

    private companion object {
        /** How often to re-check for scan results while waiting. */
        const val SCAN_POLL_MS = 2000L
        /** Total time to wait for the system to produce a scan before finalizing. */
        const val SCAN_MAX_WAIT_MS = 16000L
    }
}

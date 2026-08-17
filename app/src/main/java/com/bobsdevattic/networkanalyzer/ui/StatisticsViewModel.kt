package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.CurrentWifi
import com.bobsdevattic.networkanalyzer.network.EthernetInterfaceManager
import com.bobsdevattic.networkanalyzer.network.RawCounters
import com.bobsdevattic.networkanalyzer.network.StatisticsReader
import com.bobsdevattic.networkanalyzer.network.StatsState
import com.bobsdevattic.networkanalyzer.network.ThroughputSample
import com.bobsdevattic.networkanalyzer.network.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Polls the interface counters once per second and derives live throughput by
 * differencing successive snapshots. Keeps a bounded rolling history for the
 * sparkline. Unrooted — reads only world-readable sysfs.
 */
class StatisticsViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = EthernetInterfaceManager(app)
    private val wifiScanner = WifiScanner(app)

    private val _state = MutableStateFlow(StatsState.empty())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    private var previous: RawCounters? = null
    private var previousIface: String? = null
    private val history = ArrayDeque<ThroughputSample>()
    private val rssiHistory = ArrayDeque<Int>()
    private var pollJob: Job? = null

    /** Begin (or resume) polling. Idempotent. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun tick() {
        // WiFi signal is sampled every tick, independent of the wired link, so the
        // graph works even with no adapter attached.
        val wifi = withContext(Dispatchers.IO) { wifiScanner.current() }
        if (wifi != null) {
            rssiHistory.addLast(wifi.rssiDbm)
            while (rssiHistory.size > HISTORY_SIZE) rssiHistory.removeFirst()
        } else {
            rssiHistory.clear()
        }

        val iface = withContext(Dispatchers.IO) { manager.currentInterfaceName() }
        if (iface == null) {
            resetCounters()
            _state.value = StatsState(available = false).withWifi(wifi)
            return
        }

        val now = withContext(Dispatchers.IO) { StatisticsReader.read(iface) }
        if (now == null) {
            resetCounters()
            _state.value = StatsState(available = false, interfaceName = iface).withWifi(wifi)
            return
        }

        val prev = previous
        val ifaceChanged = iface != previousIface
        previous = now
        previousIface = iface

        // First sample after (re)start or interface change: no rate yet.
        if (prev == null || ifaceChanged) {
            if (ifaceChanged) history.clear()
            _state.value = snapshot(iface, now, rx = 0.0, tx = 0.0).withWifi(wifi)
            return
        }

        val dtSeconds = (now.timestampNanos - prev.timestampNanos) / 1_000_000_000.0
        if (dtSeconds <= 0) return

        val rxMbps = rate(now.rxBytes - prev.rxBytes, dtSeconds)
        val txMbps = rate(now.txBytes - prev.txBytes, dtSeconds)

        history.addLast(ThroughputSample(rxMbps, txMbps))
        while (history.size > HISTORY_SIZE) history.removeFirst()

        _state.value = snapshot(iface, now, rxMbps, txMbps).withWifi(wifi)
    }

    private fun StatsState.withWifi(wifi: CurrentWifi?) = copy(
        wifiSsid = wifi?.ssid,
        wifiRssiDbm = wifi?.rssiDbm,
        wifiRssiHistory = rssiHistory.toList(),
    )

    private fun snapshot(
        iface: String,
        c: RawCounters,
        rx: Double,
        tx: Double,
    ) = StatsState(
        available = true,
        interfaceName = iface,
        rxMbps = rx,
        txMbps = tx,
        rxBytes = c.rxBytes,
        txBytes = c.txBytes,
        rxPackets = c.rxPackets,
        txPackets = c.txPackets,
        rxErrors = c.rxErrors,
        txErrors = c.txErrors,
        rxDropped = c.rxDropped,
        txDropped = c.txDropped,
        history = history.toList(),
    )

    private fun resetCounters() {
        previous = null
        previousIface = null
        history.clear()
    }

    /** bytes over dt seconds -> Mbps; clamped at 0 to absorb counter resets. */
    private fun rate(deltaBytes: Long, dtSeconds: Double): Double {
        if (deltaBytes <= 0) return 0.0
        return (deltaBytes * 8.0) / dtSeconds / 1_000_000.0
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1000L
        const val HISTORY_SIZE = 60
    }
}

package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.PortScanState
import com.bobsdevattic.networkanalyzer.network.PortScanner
import com.bobsdevattic.networkanalyzer.network.PortState
import com.bobsdevattic.networkanalyzer.network.ServiceCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives on-demand port scanning of a single host. The target IP can be set
 * directly or handed in from the Hosts tab.
 */
class PortScanViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = PortScanner(app)

    private val _state = MutableStateFlow(PortScanState())
    val state: StateFlow<PortScanState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** Preselect a target (e.g. when arriving from the Hosts tab). */
    fun setTarget(ip: String) {
        if (_state.value.scanning) return
        _state.update { PortScanState(target = ip) }
    }

    fun scan(host: String, portsSpec: String) {
        if (_state.value.scanning) return

        val ports = PortScanner.parsePortsSpec(portsSpec)
        if (ports == null) {
            _state.value = PortScanState(
                target = host,
                message = "Couldn't parse ports. Use e.g. \"22,80,443\" or \"1-1024\" " +
                    "(max ${PortScanner.MAX_PORTS} ports).",
            )
            return
        }
        if (host.isBlank()) {
            _state.value = PortScanState(message = "Enter a host IP to scan.")
            return
        }

        scanJob = viewModelScope.launch {
            _state.value = PortScanState(scanning = true, target = host)

            val result = scanner.scan(host, ports) { scanned, total ->
                _state.update { it.copy(scanned = scanned, total = total) }
            }

            _state.value = when (result) {
                is PortScanner.Result.Ok -> {
                    val open = result.results.filter { it.state == PortState.OPEN }
                        .sortedBy { it.port }
                    PortScanState(
                        scanning = false,
                        target = host,
                        scanned = ports.size,
                        total = ports.size,
                        openPorts = open,
                        closedCount = result.results.count { it.state == PortState.CLOSED },
                        filteredCount = result.results.count { it.state == PortState.FILTERED },
                        message = if (open.isEmpty()) {
                            "No open ports found among ${ports.size} scanned."
                        } else null,
                    )
                }
                is PortScanner.Result.Error ->
                    PortScanState(scanning = false, target = host, message = result.message)
            }
        }
    }

    fun cancel() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = false) }
    }

    /** The default "Common ports" spec as a comma list. */
    fun commonPortsSpec(): String = ServiceCatalog.COMMON_PORTS.joinToString(",")

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

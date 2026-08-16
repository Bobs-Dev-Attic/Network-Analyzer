package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.DiscoveryState
import com.bobsdevattic.networkanalyzer.network.HostDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives on-demand subnet discovery. A scan is a one-shot fan-out over the
 * wired subnet; progress streams into [state] as it runs.
 */
class DiscoveryViewModel(app: Application) : AndroidViewModel(app) {

    private val discovery = HostDiscovery(app)

    private val _state = MutableStateFlow(DiscoveryState())
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun scan() {
        if (_state.value.scanning) return
        scanJob = viewModelScope.launch {
            _state.value = DiscoveryState(scanning = true)

            val result = discovery.scan { scanned, total ->
                _state.update { it.copy(scanned = scanned, total = total) }
            }

            _state.value = when (result) {
                is HostDiscovery.Result.Ok -> {
                    val responders = result.hosts.count { !it.isSelf }
                    DiscoveryState(
                        scanning = false,
                        subnet = result.subnet,
                        hosts = result.hosts,
                        scanned = result.hosts.size,
                        total = result.hosts.size,
                        message = if (responders == 0) {
                            "No other hosts responded on ${result.subnet}."
                        } else null,
                    )
                }
                is HostDiscovery.Result.Error ->
                    DiscoveryState(scanning = false, message = result.message)
            }
        }
    }

    fun cancel() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = false) }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

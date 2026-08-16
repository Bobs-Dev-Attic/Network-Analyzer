package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.SpeedQualityState
import com.bobsdevattic.networkanalyzer.network.SpeedQualityTester
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates the M5 test suite: latency, then DNS, then a download. Each phase
 * updates [state] as it completes so results appear progressively. Errors in one
 * phase are recorded but don't stop the others.
 */
class SpeedQualityViewModel(app: Application) : AndroidViewModel(app) {

    private val tester = SpeedQualityTester(app)

    private val _state = MutableStateFlow(SpeedQualityState())
    val state: StateFlow<SpeedQualityState> = _state.asStateFlow()

    private var job: Job? = null

    val defaultDownloadUrl: String get() = SpeedQualityTester.DEFAULT_DOWNLOAD_URL

    fun run(targetOverride: String, downloadUrl: String) {
        if (_state.value.running) return

        job = viewModelScope.launch {
            _state.value = SpeedQualityState(running = true, phase = "Latency")
            val errors = mutableListOf<String>()

            // Latency
            when (val r = tester.latency(targetOverride) { done, total ->
                _state.update { it.copy(phase = "Latency ($done/$total)") }
            }) {
                is SpeedQualityTester.LatencyOutcome.Ok ->
                    _state.update { it.copy(latency = r.result) }
                is SpeedQualityTester.LatencyOutcome.Error -> errors += "Latency: ${r.message}"
            }

            // DNS
            _state.update { it.copy(phase = "DNS") }
            val dns = tester.dns(SpeedQualityTester.DEFAULT_DNS_NAMES)
            val oks = dns.filter { it.ok && it.ms != null }
            _state.update {
                it.copy(
                    dns = dns,
                    dnsAvgMs = if (oks.isEmpty()) null else oks.mapNotNull { d -> d.ms }.average(),
                )
            }

            // Download
            _state.update { it.copy(phase = "Download") }
            when (val r = tester.download(downloadUrl.ifBlank { defaultDownloadUrl })) {
                is SpeedQualityTester.ThroughputOutcome.Ok ->
                    _state.update { it.copy(throughput = r.result) }
                is SpeedQualityTester.ThroughputOutcome.Error -> errors += "Download: ${r.message}"
            }

            _state.update {
                it.copy(
                    running = false,
                    phase = null,
                    message = errors.joinToString("\n").ifBlank { null },
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false, phase = null) }
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}

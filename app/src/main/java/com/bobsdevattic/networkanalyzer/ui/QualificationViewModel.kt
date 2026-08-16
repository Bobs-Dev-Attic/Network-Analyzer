package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.network.CableQualifier
import com.bobsdevattic.networkanalyzer.network.QualRole
import com.bobsdevattic.networkanalyzer.network.QualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the two-phone cable qualification screen: hosts the listener in SERVER
 * mode, or runs a timed download/upload against a peer in CLIENT mode.
 */
class QualificationViewModel(app: Application) : AndroidViewModel(app) {

    private val qualifier = CableQualifier(app)

    private val _state = MutableStateFlow(QualState())
    val state: StateFlow<QualState> = _state.asStateFlow()

    private var serverJob: Job? = null
    private var clientJob: Job? = null

    fun setRole(role: QualRole) {
        if (_state.value.serverListening || _state.value.running) return
        _state.update { QualState(role = role) }
    }

    fun startServer() {
        if (_state.value.serverListening) return
        _state.update { it.copy(serverListening = true, message = null, serverStatus = "Starting…") }
        serverJob = viewModelScope.launch {
            qualifier.serve { event ->
                when (event) {
                    is CableQualifier.ServerEvent.Listening ->
                        _state.update { it.copy(serverAddress = event.address) }
                    is CableQualifier.ServerEvent.Status ->
                        _state.update { it.copy(serverStatus = event.message) }
                    is CableQualifier.ServerEvent.Error ->
                        _state.update {
                            it.copy(serverListening = false, serverStatus = null, message = event.message)
                        }
                }
            }
            _state.update { it.copy(serverListening = false, serverStatus = null) }
        }
    }

    fun stopServer() {
        qualifier.stopServer()
        serverJob?.cancel()
        _state.update { it.copy(serverListening = false, serverStatus = null, serverAddress = null) }
    }

    fun runClient(serverIp: String) {
        if (_state.value.running) return
        if (serverIp.isBlank()) {
            _state.update { it.copy(message = "Enter the server phone's IP.") }
            return
        }
        _state.update { it.copy(running = true, result = null, message = null, phase = "Connecting") }
        clientJob = viewModelScope.launch {
            val outcome = qualifier.runClient(serverIp.trim()) { phase ->
                _state.update { it.copy(phase = phase) }
            }
            _state.update {
                when (outcome) {
                    is CableQualifier.ClientOutcome.Ok ->
                        it.copy(running = false, phase = null, result = outcome.result)
                    is CableQualifier.ClientOutcome.Error ->
                        it.copy(running = false, phase = null, message = outcome.message)
                }
            }
        }
    }

    fun cancelClient() {
        clientJob?.cancel()
        _state.update { it.copy(running = false, phase = null) }
    }

    override fun onCleared() {
        super.onCleared()
        qualifier.stopServer()
        serverJob?.cancel()
        clientJob?.cancel()
    }
}

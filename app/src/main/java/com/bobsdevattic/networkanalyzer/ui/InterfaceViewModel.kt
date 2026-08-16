package com.bobsdevattic.networkanalyzer.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.networkanalyzer.data.AdapterStatus
import com.bobsdevattic.networkanalyzer.data.EthernetInterfaceInfo
import com.bobsdevattic.networkanalyzer.network.EthernetInterfaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the live [EthernetInterfaceInfo] snapshot for the M1 screen.
 *
 * Registers a network callback scoped to the Ethernet transport so the UI
 * updates automatically when the adapter is plugged in, unplugged, or its link
 * properties change — no manual polling needed.
 */
class InterfaceViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = EthernetInterfaceManager(app)
    private val cm =
        app.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(
        EthernetInterfaceInfo(status = AdapterStatus.ABSENT)
    )
    val state: StateFlow<EthernetInterfaceInfo> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onLinkPropertiesChanged(
            network: Network,
            properties: android.net.LinkProperties,
        ) = refresh()
    }

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { manager.inspect() }
        }
    }

    fun bindToEthernet() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manager.bindProcessToEthernet() }
            refresh()
        }
    }

    fun unbind() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manager.unbindProcess() }
            refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}

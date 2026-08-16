package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import com.bobsdevattic.networkanalyzer.data.AdapterStatus
import com.bobsdevattic.networkanalyzer.data.EthernetInterfaceInfo
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * Locates the wired USB-C Ethernet transport and reports its link/IP details,
 * and can pin the app's process traffic to it.
 *
 * Unrooted-only (v1): all data comes from public [ConnectivityManager] APIs plus
 * world-readable sysfs. No raw sockets, no privileged calls.
 */
class EthernetInterfaceManager(context: Context) {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** The first network advertising the Ethernet transport, if any. */
    fun findEthernetNetwork(): Network? =
        cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }

    /** Kernel interface name of the current Ethernet link (e.g. "eth0"), or null. */
    fun currentInterfaceName(): String? =
        findEthernetNetwork()?.let { cm.getLinkProperties(it)?.interfaceName }

    /**
     * Inspect the current wired link. Returns [AdapterStatus.ABSENT] when no
     * Ethernet transport is present (adapter unplugged, unsupported chipset, or
     * no carrier).
     */
    fun inspect(): EthernetInterfaceInfo {
        val network = findEthernetNetwork()
            ?: return EthernetInterfaceInfo(status = AdapterStatus.ABSENT)

        val link: LinkProperties? = cm.getLinkProperties(network)
        val caps: NetworkCapabilities? = cm.getNetworkCapabilities(network)
        val ifaceName = link?.interfaceName

        val ipv4 = mutableListOf<String>()
        val ipv6 = mutableListOf<String>()
        link?.linkAddresses?.forEach { la: LinkAddress ->
            val addr = la.address
            val withPrefix = "${addr.hostAddress}/${la.prefixLength}"
            when (addr) {
                is Inet4Address -> ipv4 += withPrefix
                is Inet6Address -> ipv6 += withPrefix
            }
        }

        val gateway = link?.routes
            ?.firstOrNull { it.isDefaultRoute && it.hasGateway() }
            ?.gateway?.hostAddress

        val dns = link?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        return EthernetInterfaceInfo(
            status = AdapterStatus.CONNECTED,
            interfaceName = ifaceName,
            linkSpeedMbps = ifaceName?.let { LinkStatsReader.readSpeedMbps(it) },
            duplex = ifaceName?.let { LinkStatsReader.readDuplex(it) },
            macAddress = readMac(ifaceName),
            mtu = readMtu(ifaceName),
            ipv4Addresses = ipv4,
            ipv6Addresses = ipv6,
            gateway = gateway,
            dnsServers = dns,
            searchDomains = link?.domains,
            downstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upstreamKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            boundToProcess = isBoundToEthernet(network),
        )
    }

    /**
     * Pin this app process's sockets to the Ethernet network so that discovery,
     * scans, and speed tests traverse the wire rather than WiFi/cellular.
     * Returns true on success.
     */
    fun bindProcessToEthernet(): Boolean {
        val network = findEthernetNetwork() ?: return false
        return cm.bindProcessToNetwork(network)
    }

    /** Release any process-to-network binding. */
    fun unbindProcess() {
        cm.bindProcessToNetwork(null)
    }

    private fun isBoundToEthernet(ethernet: Network): Boolean =
        cm.boundNetworkForProcess == ethernet

    /**
     * Hardware MAC is withheld from non-system apps on most Android 6+ builds
     * (getHardwareAddress returns null). We attempt it and degrade to null.
     */
    private fun readMac(interfaceName: String?): String? {
        interfaceName ?: return null
        return runCatching {
            NetworkInterface.getByName(interfaceName)
                ?.hardwareAddress
                ?.joinToString(":") { "%02X".format(it) }
        }.getOrNull()
    }

    private fun readMtu(interfaceName: String?): Int? {
        interfaceName ?: return null
        return runCatching {
            NetworkInterface.getByName(interfaceName)?.mtu?.takeIf { it > 0 }
        }.getOrNull()
    }
}

private fun RouteInfo.hasGateway(): Boolean = gateway != null

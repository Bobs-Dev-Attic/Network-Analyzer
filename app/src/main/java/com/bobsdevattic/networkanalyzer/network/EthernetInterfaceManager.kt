package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import com.bobsdevattic.networkanalyzer.data.AdapterStatus
import com.bobsdevattic.networkanalyzer.data.EthernetInterfaceInfo
import java.io.File
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

    private val appContext = context.applicationContext
    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * The managed wired network. Prefer a real TRANSPORT_ETHERNET network; if none
     * is tagged that way (some OEMs, e.g. Samsung, don't), fall back to any network
     * whose interface name looks wired (eth*/usb*/rndis*) so we still get its full
     * LinkProperties (gateway/DNS) and can bind to it.
     */
    fun findEthernetNetwork(): Network? {
        val networks = cm.allNetworks
        networks.firstOrNull {
            cm.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }?.let { return it }
        return networks.firstOrNull { network ->
            cm.getLinkProperties(network)?.interfaceName?.let { isWiredName(it) } == true
        }
    }

    private fun isWiredName(name: String): Boolean =
        name.startsWith("eth") || name.startsWith("usb") || name.startsWith("rndis")

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
            ?: return inspectWithoutNetwork()

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
     * No managed Ethernet network from ConnectivityManager — enumerate raw
     * interfaces instead (apps can read [NetworkInterface] even when sysfs listing
     * and the CM transport are unavailable, which is the Samsung/Android-16 case).
     * A wired interface with an IPv4 is effectively CONNECTED (shown with degraded
     * data: no gateway/DNS/bind without a managed network); one without an address
     * is DETECTED. If there's no wired interface at all, report attached USB
     * devices so the user can see whether the adapter enumerated on USB (ABSENT).
     */
    private fun inspectWithoutNetwork(): EthernetInterfaceInfo {
        val usb = attachedUsbDevices()
        val ni = findWiredInterface()
            ?: return EthernetInterfaceInfo(status = AdapterStatus.ABSENT, usbDevices = usb)

        val name = ni.name
        val ipv4 = mutableListOf<String>()
        val ipv6 = mutableListOf<String>()
        runCatching {
            ni.interfaceAddresses.forEach { ia ->
                val addr = ia.address ?: return@forEach
                val withPrefix = "${addr.hostAddress}/${ia.networkPrefixLength}"
                when (addr) {
                    is Inet4Address -> ipv4 += withPrefix
                    is Inet6Address -> ipv6 += withPrefix
                }
            }
        }
        val up = runCatching { ni.isUp }.getOrDefault(false)

        return EthernetInterfaceInfo(
            status = if (ipv4.isNotEmpty()) AdapterStatus.CONNECTED else AdapterStatus.DETECTED,
            interfaceName = name,
            linkSpeedMbps = LinkStatsReader.readSpeedMbps(name),
            duplex = LinkStatsReader.readDuplex(name),
            macAddress = macOf(ni),
            mtu = runCatching { ni.mtu.takeIf { it > 0 } }.getOrNull(),
            ipv4Addresses = ipv4,
            ipv6Addresses = ipv6,
            carrier = readCarrier(name) ?: up,
            usbDevices = usb,
        )
    }

    /** First wired interface (eth*/usb*/rndis*) visible via NetworkInterface, or null. */
    private fun findWiredInterface(): NetworkInterface? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().firstOrNull { ni ->
            val name = ni.name.orEmpty()
            isWiredName(name) && runCatching { !ni.isLoopback }.getOrDefault(true)
        }
    }.getOrNull()

    private fun macOf(ni: NetworkInterface): String? = runCatching {
        ni.hardwareAddress?.joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /** Physical link (carrier) state; null when the file isn't readable/valid. */
    private fun readCarrier(iface: String): Boolean? = runCatching {
        val f = File("$SYS_NET/$iface/carrier")
        if (f.canRead()) f.readText().trim() == "1" else null
    }.getOrNull()

    /** Attached USB devices as "name (vvvv:pppp)" — no permission needed to list. */
    private fun attachedUsbDevices(): List<String> = runCatching {
        val usb = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return emptyList()
        usb.deviceList.values.map { d ->
            val id = "%04x:%04x".format(d.vendorId, d.productId)
            d.productName?.takeIf { it.isNotBlank() }?.let { "$it ($id)" } ?: id
        }
    }.getOrDefault(emptyList())

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

    private companion object {
        const val SYS_NET = "/sys/class/net"
    }
}

private fun RouteInfo.hasGateway(): Boolean = gateway != null

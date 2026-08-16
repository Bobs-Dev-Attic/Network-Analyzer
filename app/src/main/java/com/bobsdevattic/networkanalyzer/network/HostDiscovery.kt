package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Discovers hosts on the wired subnet without root.
 *
 * Technique: probe every candidate address with short TCP connects. A connect
 * that succeeds *or* is refused proves the host is up; either way the outbound
 * SYN forces the kernel to resolve the host's MAC into /proc/net/arp. After the
 * sweep we read the ARP cache, so hosts that silently dropped our probes are
 * still found (union of probe hits and ARP entries). MAC → vendor via OUI,
 * plus best-effort reverse DNS for hostnames.
 *
 * All sockets are created through the Ethernet network's SocketFactory so the
 * scan traverses the wire regardless of the process-wide default network.
 */
class HostDiscovery(context: Context) {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    sealed interface Result {
        data class Ok(val subnet: String, val hosts: List<DiscoveredHost>) : Result
        data class Error(val message: String) : Result
    }

    /**
     * Run a full sweep. [onProgress] is called as (scanned, total) so the UI can
     * show a progress bar. Suspends until complete.
     */
    suspend fun scan(onProgress: (scanned: Int, total: Int) -> Unit): Result {
        val network = findEthernet() ?: return Result.Error("No wired Ethernet link.")
        val lp = cm.getLinkProperties(network)
        val iface = lp?.interfaceName

        val ipv4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
            ?: return Result.Error("No IPv4 address on the wired link.")

        val prefix = ipv4.prefixLength
        if (prefix < MIN_PREFIX) {
            return Result.Error(
                "Subnet /$prefix is too large to scan (> ${1 shl (32 - MIN_PREFIX)} hosts). " +
                    "Connect to a smaller subnet."
            )
        }

        val selfIp = (ipv4.address as Inet4Address)
        val candidates = hostAddresses(ipv4)
        val total = candidates.size
        var scanned = 0
        onProgress(0, total)

        val semaphore = Semaphore(CONCURRENCY)
        val probeHits = coroutineScope {
            candidates.map { addr ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val ports = probe(network, addr)
                        synchronized(this@HostDiscovery) { onProgress(++scanned, total) }
                        if (ports != null) addr.hostAddress to ports else null
                    }
                }
            }.awaitAll()
        }.filterNotNull().toMap()

        // ARP cache now holds MACs for anything the sweep touched.
        val arp = ArpTableReader.macByIp(iface)

        val allIps = (probeHits.keys + arp.keys).toSortedSet(IpComparator)
        val hosts = allIps.map { ip ->
            val mac = arp[ip]
            DiscoveredHost(
                ip = ip,
                mac = mac,
                vendor = OuiLookup.vendorFor(mac),
                hostname = reverseDns(ip),
                openPorts = probeHits[ip].orEmpty(),
                isSelf = ip == selfIp.hostAddress,
            )
        }

        val network32 = ipv4.toNetworkString()
        return Result.Ok(subnet = network32, hosts = hosts)
    }

    /** Probe one host; returns open ports if alive (empty list = alive, no open port), null if dead. */
    private suspend fun probe(network: Network, addr: InetAddress): List<Int>? {
        val open = mutableListOf<Int>()
        var refused = false

        for (port in PROBE_PORTS) {
            val outcome = connect(network, addr, port)
            when (outcome) {
                ConnectOutcome.OPEN -> open += port
                ConnectOutcome.REFUSED -> refused = true
                ConnectOutcome.NO_RESPONSE -> Unit
            }
        }

        return when {
            open.isNotEmpty() -> open
            refused -> emptyList()
            // ICMP-style fallback for hosts that dropped every SYN.
            reachable(addr) -> emptyList()
            else -> null
        }
    }

    private enum class ConnectOutcome { OPEN, REFUSED, NO_RESPONSE }

    private fun connect(network: Network, addr: InetAddress, port: Int): ConnectOutcome {
        var socket: Socket? = null
        return try {
            socket = network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS)
            ConnectOutcome.OPEN
        } catch (e: ConnectException) {
            // "Connection refused" = host present, port closed.
            if (e.message?.contains("refused", ignoreCase = true) == true) {
                ConnectOutcome.REFUSED
            } else {
                ConnectOutcome.NO_RESPONSE
            }
        } catch (e: Exception) {
            ConnectOutcome.NO_RESPONSE
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun reachable(addr: InetAddress): Boolean =
        runCatching { addr.isReachable(CONNECT_TIMEOUT_MS) }.getOrDefault(false)

    private suspend fun reverseDns(ip: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(REVERSE_DNS_TIMEOUT_MS) {
            runCatching {
                val addr = InetAddress.getByName(ip)
                val name = addr.canonicalHostName
                // getCanonicalHostName returns the IP string when no PTR record exists.
                if (name == ip) null else name
            }.getOrNull()
        }
    }

    private fun findEthernet(): Network? =
        cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }

    /** All usable host addresses in the subnet, excluding network and broadcast. */
    private fun hostAddresses(link: LinkAddress): List<InetAddress> {
        val base = ipToInt(link.address as Inet4Address)
        val prefix = link.prefixLength
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val network = base and mask
        val broadcast = network or mask.inv()

        val list = ArrayList<InetAddress>()
        var host = network + 1
        while (host < broadcast) {
            list += intToInet(host)
            host++
        }
        return list
    }

    private fun ipToInt(addr: Inet4Address): Int =
        addr.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    private fun intToInet(value: Int): InetAddress = InetAddress.getByAddress(
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    )

    private fun LinkAddress.toNetworkString(): String {
        val base = ipToInt(address as Inet4Address)
        val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
        return "${intToInet(base and mask).hostAddress}/$prefixLength"
    }

    private object IpComparator : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
            val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until minOf(pa.size, pb.size)) {
                val c = pa[i].compareTo(pb[i])
                if (c != 0) return c
            }
            return pa.size.compareTo(pb.size)
        }
    }

    private companion object {
        // Small, fast port set. Connect success or refusal both prove liveness;
        // open ports also seed M4's port-scan view.
        val PROBE_PORTS = listOf(80, 443, 22, 445, 53)
        const val CONNECT_TIMEOUT_MS = 300
        const val REVERSE_DNS_TIMEOUT_MS = 1200L
        const val CONCURRENCY = 48
        const val MIN_PREFIX = 22 // up to 1022 hosts
    }
}

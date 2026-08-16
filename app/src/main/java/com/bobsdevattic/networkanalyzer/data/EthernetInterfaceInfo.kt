package com.bobsdevattic.networkanalyzer.data

/**
 * Snapshot of the wired USB-C Ethernet link as seen by an unrooted app.
 *
 * Every field is nullable because Android exposes different subsets depending on
 * OS version, adapter driver, and platform privacy restrictions (e.g. hardware
 * MAC is withheld from non-system apps on many builds). The UI degrades
 * gracefully, showing "unavailable" rather than failing.
 */
data class EthernetInterfaceInfo(
    val status: AdapterStatus,
    val interfaceName: String? = null,
    /** Negotiated PHY speed in Mbps (10/100/1000), from /sys when readable. */
    val linkSpeedMbps: Int? = null,
    val duplex: String? = null,
    val macAddress: String? = null,
    val mtu: Int? = null,
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val searchDomains: String? = null,
    /** Platform bandwidth estimate (not the negotiated PHY rate). */
    val downstreamKbps: Int? = null,
    val upstreamKbps: Int? = null,
    /** True once app process traffic is pinned to this interface. */
    val boundToProcess: Boolean = false,
)

enum class AdapterStatus {
    /** A TRANSPORT_ETHERNET network is present and inspected. */
    CONNECTED,

    /** No Ethernet transport found — adapter unplugged, unsupported, or no link. */
    ABSENT,
}

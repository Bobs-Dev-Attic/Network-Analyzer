package com.bobsdevattic.networkanalyzer.network

/**
 * A host found on the wired subnet.
 *
 * MAC comes from the kernel ARP cache (/proc/net/arp), vendor from an OUI
 * lookup, and hostname from best-effort reverse DNS. Any of these may be null
 * when the platform or network doesn't supply them.
 */
data class DiscoveredHost(
    val ip: String,
    val mac: String? = null,
    val vendor: String? = null,
    val hostname: String? = null,
    /** Ports that accepted a connection during the probe (seeds M4). */
    val openPorts: List<Int> = emptyList(),
    val isSelf: Boolean = false,
)

/** UI-facing state for the discovery screen. */
data class DiscoveryState(
    val scanning: Boolean = false,
    val subnet: String? = null,
    val scanned: Int = 0,
    val total: Int = 0,
    val hosts: List<DiscoveredHost> = emptyList(),
    val message: String? = null,
) {
    val progress: Float
        get() = if (total > 0) scanned.toFloat() / total else 0f
}

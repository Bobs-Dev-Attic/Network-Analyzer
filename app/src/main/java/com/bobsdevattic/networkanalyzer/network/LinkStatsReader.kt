package com.bobsdevattic.networkanalyzer.network

import java.io.File

/**
 * Reads physical-link attributes from sysfs.
 *
 * On an unrooted device the files under /sys/class/net/<iface>/ are usually
 * world-readable, which lets us surface the negotiated PHY speed and duplex that
 * the ConnectivityManager APIs don't expose. Any failure is treated as
 * "unavailable" and returns null — never an exception to the caller.
 */
object LinkStatsReader {

    private const val SYS_NET = "/sys/class/net"

    /** Negotiated link speed in Mbps, or null if unreadable/down. */
    fun readSpeedMbps(interfaceName: String): Int? {
        val raw = readSysValue(interfaceName, "speed") ?: return null
        // Kernel reports -1 when the link is down or speed is unknown.
        return raw.toIntOrNull()?.takeIf { it > 0 }
    }

    /** "full" / "half" duplex, or null if unreadable. */
    fun readDuplex(interfaceName: String): String? =
        readSysValue(interfaceName, "duplex")?.takeIf { it.isNotBlank() }

    /** operstate, e.g. "up" / "down", or null if unreadable. */
    fun readOperState(interfaceName: String): String? =
        readSysValue(interfaceName, "operstate")?.takeIf { it.isNotBlank() }

    private fun readSysValue(interfaceName: String, attr: String): String? =
        runCatching {
            val f = File("$SYS_NET/$interfaceName/$attr")
            if (f.canRead()) f.readText().trim() else null
        }.getOrNull()
}

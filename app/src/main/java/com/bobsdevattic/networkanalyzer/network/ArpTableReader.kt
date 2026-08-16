package com.bobsdevattic.networkanalyzer.network

import java.io.File

/** One completed entry from the kernel ARP cache. */
data class ArpEntry(
    val ip: String,
    val mac: String,
    val device: String,
)

/**
 * Reads the kernel ARP cache from /proc/net/arp.
 *
 * This is the key to MAC discovery on an unrooted device: after we send any
 * packet (even a TCP SYN to a closed port) to a host on the subnet, the kernel
 * resolves and caches its MAC here — no raw ARP frames or root required.
 *
 * File format (whitespace-separated columns, one header line):
 *   IP address   HW type   Flags   HW address          Mask   Device
 *   192.168.1.1  0x1       0x2     aa:bb:cc:dd:ee:ff   *      eth0
 *
 * Flags 0x2 = ATF_COM (entry complete). Incomplete/zero-MAC rows are skipped.
 */
object ArpTableReader {

    private const val PROC_ARP = "/proc/net/arp"
    private const val FLAG_COMPLETE = 0x2
    private const val ZERO_MAC = "00:00:00:00:00:00"

    /** All complete ARP entries, optionally filtered to one interface. */
    fun read(deviceFilter: String? = null): List<ArpEntry> = runCatching {
        val file = File(PROC_ARP)
        if (!file.canRead()) return emptyList()

        file.readLines()
            .drop(1) // header
            .mapNotNull { parseLine(it) }
            .filter { deviceFilter == null || it.device == deviceFilter }
    }.getOrDefault(emptyList())

    /** Convenience: IP -> MAC map for one interface. */
    fun macByIp(deviceFilter: String? = null): Map<String, String> =
        read(deviceFilter).associate { it.ip to it.mac }

    private fun parseLine(line: String): ArpEntry? {
        val cols = line.trim().split(Regex("\\s+"))
        if (cols.size < 6) return null

        val ip = cols[0]
        val flags = cols[2].removePrefix("0x").toIntOrNull(16) ?: return null
        val mac = cols[3].uppercase()
        val device = cols[5]

        if (flags and FLAG_COMPLETE == 0) return null
        if (mac == ZERO_MAC) return null

        return ArpEntry(ip = ip, mac = mac, device = device)
    }
}

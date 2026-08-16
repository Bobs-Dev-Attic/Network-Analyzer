package com.bobsdevattic.networkanalyzer.network

import java.io.File

/**
 * Reads interface counters from /sys/class/net/<iface>/statistics/.
 *
 * These files are world-readable on virtually all Android builds, so this works
 * unrooted. rx/tx bytes and packets are required; error/dropped counters are
 * optional and default to 0 when a particular file is absent (driver-dependent).
 */
object StatisticsReader {

    private const val SYS_NET = "/sys/class/net"

    fun read(interfaceName: String): RawCounters? = runCatching {
        val dir = File("$SYS_NET/$interfaceName/statistics")
        if (!dir.canRead()) return null

        fun required(name: String): Long = File(dir, name).readText().trim().toLong()
        fun optional(name: String): Long =
            runCatching { File(dir, name).readText().trim().toLong() }.getOrElse { 0L }

        RawCounters(
            timestampNanos = System.nanoTime(),
            rxBytes = required("rx_bytes"),
            txBytes = required("tx_bytes"),
            rxPackets = required("rx_packets"),
            txPackets = required("tx_packets"),
            rxErrors = optional("rx_errors"),
            txErrors = optional("tx_errors"),
            rxDropped = optional("rx_dropped"),
            txDropped = optional("tx_dropped"),
        )
    }.getOrNull()
}

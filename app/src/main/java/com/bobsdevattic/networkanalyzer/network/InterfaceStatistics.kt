package com.bobsdevattic.networkanalyzer.network

/**
 * A raw reading of the interface counters at a point in time, taken from
 * /sys/class/net/<iface>/statistics/. Monotonic since the interface came up;
 * comparing two snapshots yields per-interval rates.
 */
data class RawCounters(
    val timestampNanos: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long,
    val rxErrors: Long,
    val txErrors: Long,
    val rxDropped: Long,
    val txDropped: Long,
)

/** One point in the throughput history, in megabits per second. */
data class ThroughputSample(
    val rxMbps: Double,
    val txMbps: Double,
)

/**
 * UI-facing statistics state for the live meter. When [available] is false the
 * counters directory couldn't be read (no adapter, or sysfs withheld).
 */
data class StatsState(
    val available: Boolean,
    val interfaceName: String? = null,
    val rxMbps: Double = 0.0,
    val txMbps: Double = 0.0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxPackets: Long = 0,
    val txPackets: Long = 0,
    val rxErrors: Long = 0,
    val txErrors: Long = 0,
    val rxDropped: Long = 0,
    val txDropped: Long = 0,
    val history: List<ThroughputSample> = emptyList(),
) {
    companion object {
        fun empty() = StatsState(available = false)
    }
}

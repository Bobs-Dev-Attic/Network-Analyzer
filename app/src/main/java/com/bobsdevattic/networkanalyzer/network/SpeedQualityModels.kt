package com.bobsdevattic.networkanalyzer.network

/**
 * TCP-ping latency summary. On an unrooted device we can't send ICMP reliably,
 * so we time TCP handshakes: both a completed connect and a refusal (RST) are a
 * valid round trip; a timeout counts as loss.
 */
data class LatencyResult(
    val target: String,
    val port: Int,
    val sent: Int,
    val received: Int,
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    /** Mean absolute difference between consecutive samples (delay variation). */
    val jitterMs: Double,
) {
    val lossPct: Double get() = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent
}

/** One DNS resolution timing. */
data class DnsResult(
    val name: String,
    val ms: Double?,
    val ok: Boolean,
)

/** HTTP download throughput. */
data class ThroughputResult(
    val url: String,
    val bytes: Long,
    val seconds: Double,
) {
    val mbps: Double get() = if (seconds > 0) bytes * 8.0 / seconds / 1_000_000.0 else 0.0
}

/** UI-facing state for the speed & quality screen. */
data class SpeedQualityState(
    val running: Boolean = false,
    val phase: String? = null,
    val latency: LatencyResult? = null,
    val dns: List<DnsResult> = emptyList(),
    val dnsAvgMs: Double? = null,
    val throughput: ThroughputResult? = null,
    val message: String? = null,
)

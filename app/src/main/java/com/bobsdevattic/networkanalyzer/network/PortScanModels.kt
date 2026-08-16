package com.bobsdevattic.networkanalyzer.network

/** Result of probing a single TCP port. */
enum class PortState { OPEN, CLOSED, FILTERED }

data class PortResult(
    val port: Int,
    val state: PortState,
    val service: String? = null,
    /** Unsolicited banner text captured after connect, if the service sent one. */
    val banner: String? = null,
)

/** UI-facing state for the port-scan screen. */
data class PortScanState(
    val scanning: Boolean = false,
    val target: String? = null,
    val scanned: Int = 0,
    val total: Int = 0,
    val openPorts: List<PortResult> = emptyList(),
    val closedCount: Int = 0,
    val filteredCount: Int = 0,
    val message: String? = null,
) {
    val progress: Float
        get() = if (total > 0) scanned.toFloat() / total else 0f
}

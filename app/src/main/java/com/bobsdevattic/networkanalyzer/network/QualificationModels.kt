package com.bobsdevattic.networkanalyzer.network

/** Which end of the two-phone test this device is playing. */
enum class QualRole { SERVER, CLIENT }

/**
 * Pass/fail outcome of a cable qualification run.
 *
 * This is a *qualifier*, not a wiremap tester or certifier: it says whether the
 * run carries clean gigabit traffic, not where a fault is or how long the cable
 * is. See the M7 notes in the README.
 */
enum class Verdict { PASS, MARGINAL, FAULT, UNKNOWN }

/** Result computed on the client end after a run. */
data class QualResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val linkSpeedMbps: Int?,
    /** Combined receive errors on both ends during the transfer. */
    val rxErrorsDelta: Long,
    val droppedDelta: Long,
    val verdict: Verdict,
    val reasons: List<String>,
)

/** UI-facing state for the cable-qualification screen. */
data class QualState(
    val role: QualRole = QualRole.CLIENT,
    // Server side
    val serverListening: Boolean = false,
    val serverAddress: String? = null,
    val serverStatus: String? = null,
    // Client side
    val running: Boolean = false,
    val phase: String? = null,
    val result: QualResult? = null,
    // Shared
    val message: String? = null,
)

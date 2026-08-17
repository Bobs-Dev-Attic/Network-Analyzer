package com.bobsdevattic.networkanalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.bobsdevattic.networkanalyzer.network.PortState
import com.bobsdevattic.networkanalyzer.network.Verdict
import com.bobsdevattic.networkanalyzer.network.WifiBand

/**
 * Semantic state, independent of any particular colour. Screens map their domain values
 * to a [Status] and let the theme resolve the colour, so thresholds live in one place
 * rather than being scattered across composables.
 */
enum class Status { GOOD, WARN, BAD, INFO, NEUTRAL }

fun statusOf(verdict: Verdict): Status = when (verdict) {
    Verdict.PASS -> Status.GOOD
    Verdict.MARGINAL -> Status.WARN
    Verdict.FAULT -> Status.BAD
    Verdict.UNKNOWN -> Status.NEUTRAL
}

/** Matches the 0..4 buckets of `WifiAp.signalBars`, and the zone bands on the chart. */
fun statusOfSignalBars(bars: Int): Status = when {
    bars >= 3 -> Status.GOOD
    bars == 2 -> Status.WARN
    else -> Status.BAD
}

/**
 * OPEN is [Status.INFO], not GOOD, on purpose: on a port scan an open port is
 * noteworthy rather than reassuring, and green would read as the opposite of the truth.
 */
fun statusOf(state: PortState): Status = when (state) {
    PortState.OPEN -> Status.INFO
    PortState.FILTERED -> Status.WARN
    PortState.CLOSED -> Status.NEUTRAL
}

fun statusOfLossPct(pct: Double): Status = when {
    pct <= 0.0 -> Status.GOOD
    pct <= 2.0 -> Status.WARN
    else -> Status.BAD
}

fun statusOfLatencyMs(ms: Double): Status = when {
    ms < 30.0 -> Status.GOOD
    ms < 100.0 -> Status.WARN
    else -> Status.BAD
}

/** Counters where zero is healthy and anything above zero deserves attention. */
fun statusOfErrorCount(n: Long): Status = if (n <= 0L) Status.GOOD else Status.WARN

fun statusOfOk(ok: Boolean): Status = if (ok) Status.GOOD else Status.BAD

/** Glyph paired with every status so colour is never the only signal. */
val Status.glyph: String
    get() = when (this) {
        Status.GOOD -> "✓"
        Status.WARN -> "!"
        Status.BAD -> "✕"
        Status.INFO -> "●"
        Status.NEUTRAL -> "○"
    }

@Composable
@ReadOnlyComposable
fun Status.colors(): StatusColor {
    val s = MaterialTheme.statusColors
    return when (this) {
        Status.GOOD -> s.good
        Status.WARN -> s.warn
        Status.BAD -> s.bad
        Status.INFO -> s.info
        Status.NEUTRAL -> StatusColor(
            fg = MaterialTheme.colorScheme.onSurfaceVariant,
            container = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
@ReadOnlyComposable
fun WifiBand.colors(): StatusColor {
    val s = MaterialTheme.statusColors
    return when (this) {
        WifiBand.GHZ_2_4 -> s.band24
        WifiBand.GHZ_5 -> s.band5
        WifiBand.GHZ_6 -> s.band6
        WifiBand.UNKNOWN -> Status.NEUTRAL.colors()
    }
}

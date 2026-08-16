package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.StatsState
import com.bobsdevattic.networkanalyzer.network.ThroughputSample
import kotlin.math.max

private val RxColor = Color(0xFF3D9970)
private val TxColor = Color(0xFF0074D9)

/**
 * M2 screen: live RX/TX throughput meter, a rolling sparkline, and the raw
 * interface counters (packets, errors, dropped, totals).
 */
@Composable
fun StatisticsScreen(
    stats: StatsState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Live Statistics", style = MaterialTheme.typography.headlineSmall)

        if (!stats.available) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No counters available", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect a USB-C Ethernet adapter with a live link. Throughput is " +
                            "read from the interface's kernel counters.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return
        }

        ThroughputHeader(stats)
        SparklineCard(stats.history)
        CountersCard(stats)
    }
}

@Composable
private fun ThroughputHeader(stats: StatsState) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RateColumn("Download", stats.rxMbps, RxColor, Modifier.weight(1f))
            RateColumn("Upload", stats.txMbps, TxColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RateColumn(label: String, mbps: Double, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            formatMbps(mbps),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text("Mbps", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SparklineCard(history: List<ThroughputSample>) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot("Down", RxColor)
                LegendDot("Up", TxColor)
            }
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                Sparkline(history)
            }
            Text(
                "Last ${max(history.size, 1)}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Sparkline(history: List<ThroughputSample>) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxSize()) {
        // Baseline.
        drawLine(
            color = axisColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 2f,
        )
        if (history.size < 2) return@Canvas

        val peak = history.maxOf { max(it.rxMbps, it.txMbps) }.coerceAtLeast(1.0)
        val stepX = size.width / (history.size - 1)

        fun path(select: (ThroughputSample) -> Double): Path = Path().apply {
            history.forEachIndexed { i, sample ->
                val x = i * stepX
                val y = size.height - (select(sample) / peak * size.height).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(path { it.rxMbps }, color = RxColor, style = strokeStyle())
        drawPath(path { it.txMbps }, color = TxColor, style = strokeStyle())
    }
}

private fun strokeStyle() =
    androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(color, radius = size.minDimension / 2, center = center)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CountersCard(stats: StatsState) {
    val rows = listOf(
        "RX total" to formatBytes(stats.rxBytes),
        "TX total" to formatBytes(stats.txBytes),
        "RX packets" to stats.rxPackets.toString(),
        "TX packets" to stats.txPackets.toString(),
        "RX errors" to stats.rxErrors.toString(),
        "TX errors" to stats.txErrors.toString(),
        "RX dropped" to stats.rxDropped.toString(),
        "TX dropped" to stats.txDropped.toString(),
    )
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Counters", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace)
                }
            }
            Text(
                "Nonzero errors or drops under load can indicate a marginal cable or link.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMbps(mbps: Double): String = when {
    mbps >= 100 -> "%.0f".format(mbps)
    mbps >= 10 -> "%.1f".format(mbps)
    else -> "%.2f".format(mbps)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var idx = 0
    while (value >= 1024 && idx < units.size - 1) {
        value /= 1024
        idx++
    }
    return "%.2f %s".format(value, units[idx])
}

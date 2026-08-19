package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.HeatmapState
import com.bobsdevattic.networkanalyzer.network.SignalSample

private const val GRID_N = 24          // heatmap interpolation resolution
private const val GRID_LINES = 8       // visible reference grid

private val WeakColor = Color(0xFFC62828)
private val MidColor = Color(0xFFF9A825)
private val StrongColor = Color(0xFF2E7D32)

/**
 * Blank-grid WiFi signal heatmap (MVP). Walk the space and tap the grid where
 * you're standing; each tap records the connected network's RSSI at that spot,
 * and the samples are interpolated (inverse-distance weighting) into a heatmap.
 * There's no auto-positioning — you mark each location by tapping, which is the
 * standard approach for an unrooted survey.
 */
@Composable
fun HeatmapScreen(
    state: HeatmapState,
    onAddSample: (x: Float, y: Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Signal Map", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tap the grid where you're standing to record the connected network's " +
                "signal. Walk the area and tap each spot to build a heatmap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HeaderCard(state)

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        val heat = remember(state.samples) { computeHeat(state.samples) }
        val gridLineColor = MaterialTheme.colorScheme.outlineVariant
        val dotRing = MaterialTheme.colorScheme.surface

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, gridLineColor, RoundedCornerShape(8.dp)),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0 && h > 0) {
                                onAddSample(
                                    (off.x / w).coerceIn(0f, 1f),
                                    (off.y / h).coerceIn(0f, 1f),
                                )
                            }
                        }
                    },
            ) {
                // Interpolated heatmap.
                if (heat != null) {
                    val cw = size.width / GRID_N
                    val ch = size.height / GRID_N
                    for (gy in 0 until GRID_N) {
                        for (gx in 0 until GRID_N) {
                            drawRect(
                                color = rssiColor(heat[gy * GRID_N + gx]).copy(alpha = 0.85f),
                                topLeft = Offset(gx * cw, gy * ch),
                                size = Size(cw, ch),
                            )
                        }
                    }
                }
                // Reference grid.
                val stepX = size.width / GRID_LINES
                val stepY = size.height / GRID_LINES
                for (i in 0..GRID_LINES) {
                    drawLine(gridLineColor, Offset(i * stepX, 0f), Offset(i * stepX, size.height), 1f)
                    drawLine(gridLineColor, Offset(0f, i * stepY), Offset(size.width, i * stepY), 1f)
                }
                // Sample points.
                state.samples.forEach { s ->
                    val c = Offset(s.x * size.width, s.y * size.height)
                    drawCircle(dotRing, radius = 10f, center = c)
                    drawCircle(rssiColor(s.rssiDbm.toFloat()), radius = 7f, center = c)
                }
            }
        }

        Legend()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = state.samples.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Undo") }
            OutlinedButton(
                onClick = onClear,
                enabled = state.samples.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
        }
        Text("${state.samples.size} points",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeaderCard(state: HeatmapState) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Network", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.ssid ?: "not connected",
                    style = MaterialTheme.typography.titleMedium)
            }
            Text(
                state.currentRssi?.let { "$it dBm" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weak", style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(WeakColor, MidColor, StrongColor))),
        )
        Text("Strong", style = MaterialTheme.typography.labelSmall)
    }
}

/** Map an RSSI (dBm) to a weak→strong colour over the −100..−30 range. */
private fun rssiColor(rssi: Float): Color {
    val f = ((rssi + 100f) / 70f).coerceIn(0f, 1f)
    return if (f < 0.5f) lerp(WeakColor, MidColor, f * 2f)
    else lerp(MidColor, StrongColor, (f - 0.5f) * 2f)
}

/**
 * Inverse-distance-weighted interpolation of the samples onto a GRID_N×GRID_N
 * lattice (normalized space). Returns null when there are no samples.
 */
private fun computeHeat(samples: List<SignalSample>): FloatArray? {
    if (samples.isEmpty()) return null
    val out = FloatArray(GRID_N * GRID_N)
    for (gy in 0 until GRID_N) {
        for (gx in 0 until GRID_N) {
            val cx = (gx + 0.5f) / GRID_N
            val cy = (gy + 0.5f) / GRID_N
            var num = 0.0
            var den = 0.0
            var exact: Float? = null
            for (s in samples) {
                val dx = cx - s.x
                val dy = cy - s.y
                val d2 = dx * dx + dy * dy
                if (d2 < 1e-6f) {
                    exact = s.rssiDbm.toFloat()
                    break
                }
                val w = 1.0 / d2 // IDW with power 2
                num += w * s.rssiDbm
                den += w
            }
            out[gy * GRID_N + gx] = exact ?: (num / den).toFloat()
        }
    }
    return out
}

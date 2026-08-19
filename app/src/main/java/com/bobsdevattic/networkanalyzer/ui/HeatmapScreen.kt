package com.bobsdevattic.networkanalyzer.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bobsdevattic.networkanalyzer.network.HeatmapState
import com.bobsdevattic.networkanalyzer.network.MapMode
import com.bobsdevattic.networkanalyzer.network.SignalSample
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import kotlin.math.max
import kotlin.math.min

private const val GRID_N = 24
private const val GRID_LINES = 8

private val WeakColor = Color(0xFFC62828)
private val MidColor = Color(0xFFF9A825)
private val StrongColor = Color(0xFF2E7D32)

/**
 * WiFi signal heatmap with two modes (toggle):
 *  - GRID: tap a blank grid where you're standing.
 *  - AR: the camera tracks your position as you walk (ARCore) and auto-records.
 * Both interpolate samples (inverse-distance weighting) into a weak→strong map.
 */
@Composable
fun HeatmapScreen(
    state: HeatmapState,
    onSetMode: (MapMode) -> Unit,
    onAddGridSample: (x: Float, y: Float) -> Unit,
    onArPose: (x: Float, z: Float) -> Unit,
    onTracking: (Boolean) -> Unit,
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == MapMode.GRID,
                onClick = { onSetMode(MapMode.GRID) },
                label = { Text("Grid") },
            )
            FilterChip(
                selected = state.mode == MapMode.AR,
                onClick = { onSetMode(MapMode.AR) },
                label = { Text("AR (camera)") },
            )
        }

        HeaderCard(state)

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        when (state.mode) {
            MapMode.GRID -> GridMode(
                samples = state.gridSamples,
                onTap = onAddGridSample,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            MapMode.AR -> ArMode(
                state = state,
                onArPose = onArPose,
                onTracking = onTracking,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        Legend()

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onUndo,
                enabled = state.activeSamples.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Undo") }
            OutlinedButton(
                onClick = onClear,
                enabled = state.activeSamples.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
        }
        Text("${state.activeSamples.size} points",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GridMode(
    samples: List<SignalSample>,
    onTap: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    val heat = remember(samples) { computeHeat(samples) }
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val dotRing = MaterialTheme.colorScheme.surface
    Box(
        modifier
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
                            onTap((off.x / w).coerceIn(0f, 1f), (off.y / h).coerceIn(0f, 1f))
                        }
                    }
                },
        ) {
            drawHeatField(heat, samples, gridLineColor, dotRing, showGrid = true)
        }
    }
}

@Composable
private fun ArMode(
    state: HeatmapState,
    onArPose: (Float, Float) -> Unit,
    onTracking: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    if (!granted) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Camera needed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "AR survey uses the camera to track your position as you walk. No " +
                        "photos are taken or stored — it's motion tracking only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera")
                }
            }
        }
        return
    }

    val normalized = remember(state.arSamples) { normalizeWorld(state.arSamples) }
    val heat = remember(normalized) { computeHeat(normalized) }
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val dotRing = MaterialTheme.colorScheme.surface
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)

    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = false,
            onSessionUpdated = { _, frame ->
                val camera = frame.camera
                if (camera.trackingState == TrackingState.TRACKING) {
                    onTracking(true)
                    val pose = camera.pose
                    onArPose(pose.tx(), pose.tz())
                } else {
                    onTracking(false)
                }
            },
        )

        // Status overlay.
        Text(
            text = if (state.tracking) "Tracking — walk the area to map"
            else "Move the phone slowly to start tracking",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(scrim)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )

        // Top-down heatmap minimap building up as you walk.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scrim)
                .border(1.dp, gridLineColor, RoundedCornerShape(8.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                drawHeatField(heat, normalized, gridLineColor, dotRing, showGrid = false)
            }
        }
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

private fun DrawScope.drawHeatField(
    heat: FloatArray?,
    samples: List<SignalSample>,
    gridColor: Color,
    dotRing: Color,
    showGrid: Boolean,
) {
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
    if (showGrid) {
        val stepX = size.width / GRID_LINES
        val stepY = size.height / GRID_LINES
        for (i in 0..GRID_LINES) {
            drawLine(gridColor, Offset(i * stepX, 0f), Offset(i * stepX, size.height), 1f)
            drawLine(gridColor, Offset(0f, i * stepY), Offset(size.width, i * stepY), 1f)
        }
    }
    samples.forEach { s ->
        val c = Offset(s.x * size.width, s.y * size.height)
        drawCircle(dotRing, radius = 9f, center = c)
        drawCircle(rssiColor(s.rssiDbm.toFloat()), radius = 6f, center = c)
    }
}

private fun rssiColor(rssi: Float): Color {
    val f = ((rssi + 100f) / 70f).coerceIn(0f, 1f)
    return if (f < 0.5f) lerp(WeakColor, MidColor, f * 2f)
    else lerp(MidColor, StrongColor, (f - 0.5f) * 2f)
}

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
                val w = 1.0 / d2
                num += w * s.rssiDbm
                den += w
            }
            out[gy * GRID_N + gx] = exact ?: (num / den).toFloat()
        }
    }
    return out
}

/** Normalize real-world (metre) samples into the 0..1 unit square, keeping aspect. */
private fun normalizeWorld(samples: List<SignalSample>): List<SignalSample> {
    if (samples.isEmpty()) return emptyList()
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (s in samples) {
        minX = min(minX, s.x); maxX = max(maxX, s.x)
        minY = min(minY, s.y); maxY = max(maxY, s.y)
    }
    val span = max(maxX - minX, maxY - minY).coerceAtLeast(0.001f)
    val pad = 0.1f
    val scale = 1f - 2f * pad
    return samples.map {
        SignalSample(
            x = (pad + (it.x - minX) / span * scale).coerceIn(0f, 1f),
            y = (pad + (it.y - minY) / span * scale).coerceIn(0f, 1f),
            rssiDbm = it.rssiDbm,
        )
    }
}

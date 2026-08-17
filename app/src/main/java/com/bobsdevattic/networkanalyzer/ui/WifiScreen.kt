package com.bobsdevattic.networkanalyzer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bobsdevattic.networkanalyzer.network.ChannelLoad
import com.bobsdevattic.networkanalyzer.network.CurrentWifi
import com.bobsdevattic.networkanalyzer.network.WifiAp
import com.bobsdevattic.networkanalyzer.network.WifiState
import com.bobsdevattic.networkanalyzer.ui.theme.BandChip
import com.bobsdevattic.networkanalyzer.ui.theme.SignalBars
import com.bobsdevattic.networkanalyzer.ui.theme.colors
import com.bobsdevattic.networkanalyzer.ui.theme.statusColors
import com.bobsdevattic.networkanalyzer.ui.theme.statusOfSignalBars
import kotlin.math.abs

/**
 * Scan permissions required on this API level. Location is the universal
 * requirement for reading scan results; API 33+ also needs Nearby devices.
 */
private fun requiredWifiPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Human-friendly name for the permission set shown to the user. */
private fun permissionLabel(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        "Location and Nearby devices"
    } else {
        "Location"
    }

/** Open this app's system settings page so the user can toggle permissions. */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * M6 screen: nearby APs, channel congestion, and current association — using the
 * phone's WiFi radio. Gated behind the runtime scan permission.
 */
@Composable
fun WifiScreen(
    state: WifiState,
    onScan: () -> Unit,
    onToggleLive: (Boolean) -> Unit,
    onSetInterval: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissions = remember { requiredWifiPermissions() }
    fun allGranted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(allGranted()) }
    // Once Android suppresses the prompt (denied before / "don't ask again"),
    // launch() silently no-ops — so we fall back to the app settings screen.
    var promptSuppressed by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = permissions.all { result[it] == true }
        if (granted) onScan() else promptSuppressed = true
    }

    // Re-check on resume so granting via system Settings reflects immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = allGranted()
                if (now && !granted) onScan()
                granted = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-scan when the tab opens with permission already granted, so the user
    // doesn't have to guess that they need to tap Scan first.
    LaunchedEffect(granted) {
        if (granted && state.aps.isEmpty() && !state.scanning) onScan()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("WiFi", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f))
            if (granted) {
                Button(onClick = onScan, enabled = !state.scanning) {
                    Text(if (state.scanning) "Scanning…" else "Scan")
                }
            }
        }

        if (!granted) {
            PermissionCard(
                permissionLabel = permissionLabel(),
                promptSuppressed = promptSuppressed,
                onGrant = { launcher.launch(permissions) },
                onOpenSettings = { openAppSettings(context) },
            )
            return
        }

        state.current?.let {
            CurrentCard(
                c = it,
                liveEnabled = state.liveEnabled,
                intervalMs = state.intervalMs,
                rssiHistory = state.rssiHistory,
                onToggleLive = onToggleLive,
                onSetInterval = onSetInterval,
            )
        }
        if (state.channelLoads.isNotEmpty()) CongestionCard(state.channelLoads)

        if (state.scanning && state.aps.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Scanning nearby networks…",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.aps.isNotEmpty()) {
            Text("${state.aps.size} networks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.aps, key = { it.bssid.ifBlank { it.ssid + it.channel } }) { ap ->
                ApCard(ap)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissionLabel: String,
    promptSuppressed: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permission needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "Listing WiFi networks needs the \"$permissionLabel\" permission. This " +
                    "app never uses your location — scanning is for network analysis only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (promptSuppressed) {
                Text(
                    "Android won't show the prompt again once it's been dismissed. Open " +
                        "app settings and enable \"$permissionLabel\" manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onOpenSettings) { Text("Open app settings") }
            } else {
                Button(onClick = onGrant) { Text("Grant permission") }
                OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
            }
        }
    }
}

private val LIVE_INTERVALS = listOf("0.5s" to 500L, "1s" to 1000L, "2s" to 2000L, "5s" to 5000L)

@Composable
private fun CollapsibleHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
        Text(
            if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentCard(
    c: CurrentWifi,
    liveEnabled: Boolean,
    intervalMs: Long,
    rssiHistory: List<Int>,
    onToggleLive: (Boolean) -> Unit,
    onSetInterval: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollapsibleHeader(
                title = "Connected",
                expanded = expanded,
                onToggle = { expanded = !expanded },
                trailing = {
                    // Live-updating (when enabled) signal, always visible in the header.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignalBars(rssiBars(c.rssiDbm))
                        Text(
                            "${c.rssiDbm} dBm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = statusOfSignalBars(rssiBars(c.rssiDbm)).colors().fg,
                        )
                    }
                },
            )

            if (expanded) {
                InfoLine("SSID", c.ssid)
                c.bssid?.let { InfoLine("BSSID", it, mono = true) }
                c.linkSpeedMbps?.let { InfoLine("Link speed", "$it Mbps") }
                val chan = c.channel?.let { "ch $it · ${c.band.label}" } ?: c.band.label
                InfoLine("Channel", chan)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Live signal", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = liveEnabled, onCheckedChange = onToggleLive)
                }

                if (liveEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LIVE_INTERVALS.forEach { (label, ms) ->
                            FilterChip(
                                selected = intervalMs == ms,
                                onClick = { onSetInterval(ms) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (rssiHistory.size >= 2) {
                        Box(Modifier.fillMaxWidth().height(140.dp)) {
                            RssiChart(rssiHistory, intervalMs)
                        }
                    } else {
                        Text("Sampling…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Candidate x-axis tick steps, in seconds. */
private val TICK_STEPS_SEC = listOf(5, 10, 15, 30, 60)

/**
 * Live RSSI trace on a labelled grid.
 *
 * The dBm range is fixed at -100..-30 rather than auto-scaled, so the frame stays put
 * as the signal moves. Zone boundaries (-66, -77) are the same thresholds as
 * `WifiAp.signalBars`, so the chart, the bars and the chip never disagree.
 */
@Composable
private fun RssiChart(history: List<Int>, intervalMs: Long) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.outline
    val line = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val status = MaterialTheme.statusColors
    val zoneGood = status.good.fg.copy(alpha = 0.08f)
    val zoneWarn = status.warn.fg.copy(alpha = 0.08f)
    val zoneBad = status.bad.fg.copy(alpha = 0.08f)

    Canvas(Modifier.fillMaxSize()) {
        val left = 36.dp.toPx()
        val bottom = 18.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - bottom
        if (plotW <= 0f || plotH <= 0f || history.size < 2) return@Canvas

        val minD = -100f
        val maxD = -30f
        fun y(dbm: Float): Float =
            plotH - ((dbm - minD) / (maxD - minD)).coerceIn(0f, 1f) * plotH

        // Strength zones behind the grid.
        drawRect(zoneGood, Offset(left, y(maxD)), Size(plotW, y(-66f) - y(maxD)))
        drawRect(zoneWarn, Offset(left, y(-66f)), Size(plotW, y(-77f) - y(-66f)))
        drawRect(zoneBad, Offset(left, y(-77f)), Size(plotW, y(minD) - y(-77f)))

        // Horizontal gridlines, labelled every 20 dB.
        for (dbm in intArrayOf(-100, -80, -60, -40)) {
            val gy = y(dbm.toFloat())
            drawLine(grid, Offset(left, gy), Offset(size.width, gy), 1f)
            val label = measurer.measure(AnnotatedString(dbm.toString()), labelStyle)
            drawText(
                label,
                topLeft = Offset(left - label.size.width - 4.dp.toPx(), gy - label.size.height / 2f),
            )
        }

        // Vertical gridlines, labelled by age. The step is picked so that every one of
        // the four sampling intervals produces round numbers.
        val spanSec = (history.size - 1) * intervalMs / 1000f
        if (spanSec > 0f) {
            val step = TICK_STEPS_SEC.minBy { abs(it - spanSec / 4f) }.toFloat()
            var age = 0f
            while (age <= spanSec) {
                val x = left + plotW - (age / spanSec) * plotW
                drawLine(grid, Offset(x, 0f), Offset(x, plotH), 1f)
                val label = measurer.measure(AnnotatedString("${age.toInt()}s"), labelStyle)
                val lx = (x - label.size.width / 2f)
                    .coerceIn(left, (size.width - label.size.width).coerceAtLeast(left))
                drawText(label, topLeft = Offset(lx, plotH + 3.dp.toPx()))
                age += step
            }
        }

        drawLine(axis, Offset(left, 0f), Offset(left, plotH), 2f)
        drawLine(axis, Offset(left, plotH), Offset(size.width, plotH), 2f)

        val stepX = plotW / (history.size - 1)
        var prev = Offset(left, y(history[0].toFloat()))
        for (i in 1 until history.size) {
            val cur = Offset(left + i * stepX, y(history[i].toFloat()))
            drawLine(line, prev, cur, strokeWidth = 3f)
            prev = cur
        }
        drawCircle(line, radius = 3.dp.toPx(), center = prev)
    }
}

@Composable
private fun CongestionCard(loads: List<ChannelLoad>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val max = loads.maxOf { it.count }.coerceAtLeast(1)
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CollapsibleHeader(
                title = "Channel congestion",
                expanded = expanded,
                onToggle = { expanded = !expanded },
                trailing = {
                    Text("${loads.size} ch", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
            if (expanded) {
                loads.take(8).forEach { load ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${load.band.label} ch ${load.channel}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(120.dp),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(load.count.toFloat() / max)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text("${load.count}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun rssiBars(dbm: Int): Int = when {
    dbm >= -55 -> 4
    dbm >= -66 -> 3
    dbm >= -77 -> 2
    dbm >= -88 -> 1
    else -> 0
}

@Composable
private fun ApCard(ap: WifiAp) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ap.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                BandChip(ap.band)
                SignalBars(ap.signalBars)
                Text("${ap.rssiDbm} dBm", style = MaterialTheme.typography.bodySmall,
                    color = statusOfSignalBars(ap.signalBars).colors().fg)
            }
            InfoLine("BSSID", ap.bssid.ifBlank { "—" }, mono = true)
            val width = ap.widthMhz?.let { " · ${it}MHz" }.orEmpty()
            // Band is carried by the chip above, so it isn't repeated here.
            InfoLine("Channel", "${ap.channel}$width")
            InfoLine("Security", ap.security + if (ap.isCurrent) "  · connected" else "")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
    }
}

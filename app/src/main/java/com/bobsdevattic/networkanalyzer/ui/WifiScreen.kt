package com.bobsdevattic.networkanalyzer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bobsdevattic.networkanalyzer.network.ChannelLoad
import com.bobsdevattic.networkanalyzer.network.CurrentWifi
import com.bobsdevattic.networkanalyzer.network.WifiAp
import com.bobsdevattic.networkanalyzer.network.WifiState

/** The scan permission required on this API level. */
private fun requiredWifiPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }

/**
 * M6 screen: nearby APs, channel congestion, and current association — using the
 * phone's WiFi radio. Gated behind the runtime scan permission.
 */
@Composable
fun WifiScreen(
    state: WifiState,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permission = remember { requiredWifiPermission() }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (ok) onScan()
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
            PermissionCard(onGrant = { launcher.launch(permission) })
            return
        }

        state.current?.let { CurrentCard(it) }
        if (state.channelLoads.isNotEmpty()) CongestionCard(state.channelLoads)

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
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.aps, key = { it.bssid.ifBlank { it.ssid + it.channel } }) { ap ->
                ApCard(ap)
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permission needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android requires the nearby-WiFi (or location) permission to list " +
                    "access points. This app never uses your location — scanning is " +
                    "for network analysis only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onGrant) { Text("Grant permission") }
        }
    }
}

@Composable
private fun CurrentCard(c: CurrentWifi) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Connected", style = MaterialTheme.typography.titleMedium)
            InfoLine("SSID", c.ssid)
            c.bssid?.let { InfoLine("BSSID", it, mono = true) }
            InfoLine("Signal", "${c.rssiDbm} dBm")
            c.linkSpeedMbps?.let { InfoLine("Link speed", "$it Mbps") }
            val chan = c.channel?.let { "ch $it · ${c.band.label}" } ?: c.band.label
            InfoLine("Channel", chan)
        }
    }
}

@Composable
private fun CongestionCard(loads: List<ChannelLoad>) {
    val max = loads.maxOf { it.count }.coerceAtLeast(1)
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Channel congestion", style = MaterialTheme.typography.titleMedium)
            loads.take(8).forEach { load ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${load.band.label} ch ${load.channel}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(120.dp),
                    )
                    // Full-width track with a proportional fill.
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
                Text(signalGlyph(ap.signalBars),
                    style = MaterialTheme.typography.bodyMedium)
                Text("${ap.rssiDbm} dBm", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoLine("BSSID", ap.bssid.ifBlank { "—" }, mono = true)
            val width = ap.widthMhz?.let { " · ${it}MHz" }.orEmpty()
            InfoLine("Channel", "${ap.channel} · ${ap.band.label}$width")
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

private fun signalGlyph(bars: Int): String {
    val filled = "▮".repeat(bars)
    val empty = "▯".repeat(4 - bars)
    return filled + empty
}

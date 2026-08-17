package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.DiscoveredHost
import com.bobsdevattic.networkanalyzer.network.DiscoveryState

/**
 * M3 screen: discover and inventory hosts on the wired subnet. Scan is
 * on-demand; results stream in with a progress bar.
 */
@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onScanPorts: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Column(Modifier.weight(1f)) {
                Text("Hosts", style = MaterialTheme.typography.headlineSmall)
                val subtitle = when {
                    state.subnet != null -> "${state.hosts.size} found on ${state.subnet}"
                    else -> "Scan the wired subnet"
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.scanning) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Button(onClick = onScan) { Text("Scan") }
            }
        }

        if (state.scanning) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Probing ${state.scanned} / ${state.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.hosts, key = { it.ip }) { host ->
                HostCard(host, onScanPorts = onScanPorts)
            }
        }
    }
}

@Composable
private fun HostCard(host: DiscoveredHost, onScanPorts: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    host.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (host.isSelf) {
                    AssistChip(onClick = {}, label = { Text("This device") })
                }
            }

            host.hostname?.let { Field("Host", it) }
            host.mac?.let { Field("MAC", it, mono = true) }
            host.vendor?.let { Field("Vendor", it) }
            if (host.openPorts.isNotEmpty()) {
                Field("Open ports", host.openPorts.joinToString(", "), mono = true)
            }

            if (!host.isSelf) {
                TextButton(
                    onClick = { onScanPorts(host.ip) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Scan ports") }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

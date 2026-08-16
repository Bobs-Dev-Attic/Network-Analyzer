package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.PortResult
import com.bobsdevattic.networkanalyzer.network.PortScanState

/**
 * M4 screen: scan the TCP ports of a single host and identify services. The
 * target IP prefills when arriving from the Hosts tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortScanScreen(
    state: PortScanState,
    commonPortsSpec: String,
    onScan: (host: String, portsSpec: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by rememberSaveable { mutableStateOf(state.target.orEmpty()) }
    var portsSpec by rememberSaveable { mutableStateOf(commonPortsSpec) }

    // Sync the host field when a target is handed in from another tab.
    LaunchedEffect(state.target) {
        if (!state.scanning && !state.target.isNullOrBlank() && state.target != host) {
            host = state.target
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Port Scan", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = portsSpec,
            onValueChange = { portsSpec = it },
            label = { Text("Ports (e.g. 22,80,443 or 1-1024)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(
                onClick = { portsSpec = commonPortsSpec },
                label = { Text("Common") },
            )
            SuggestionChip(
                onClick = { portsSpec = "1-1024" },
                label = { Text("1-1024") },
            )
            SuggestionChip(
                onClick = { portsSpec = "80,443,8080,8443" },
                label = { Text("Web") },
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.scanning) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            } else {
                Button(
                    onClick = { onScan(host.trim(), portsSpec) },
                    modifier = Modifier.weight(1f),
                ) { Text("Scan") }
            }
        }

        if (state.scanning) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Scanned ${state.scanned} / ${state.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!state.scanning && state.total > 0) {
            Text(
                "${state.openPorts.size} open · ${state.closedCount} closed · " +
                    "${state.filteredCount} filtered",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.openPorts, key = { it.port }) { result ->
                PortCard(result)
            }
        }
    }
}

@Composable
private fun PortCard(result: PortResult) {
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
                    result.port.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                result.service?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            result.banner?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.DnsResult
import com.bobsdevattic.networkanalyzer.network.LatencyResult
import com.bobsdevattic.networkanalyzer.network.SpeedQualityState
import com.bobsdevattic.networkanalyzer.network.ThroughputResult

/**
 * M5 screen: latency (TCP-ping), DNS timing, and HTTP download throughput over
 * the wired link. All three run in sequence from one button.
 */
@Composable
fun SpeedQualityScreen(
    state: SpeedQualityState,
    defaultDownloadUrl: String,
    onRun: (target: String, url: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var target by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf(defaultDownloadUrl) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speed & Quality", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Latency target (blank = gateway)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Download URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.running) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                Text(
                    state.phase ?: "Running…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Button(
                onClick = { onRun(target.trim(), url.trim()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run tests") }
        }

        state.latency?.let { LatencyCard(it) }
        if (state.dns.isNotEmpty()) DnsCard(state.dns, state.dnsAvgMs)
        state.throughput?.let { ThroughputCard(it) }

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LatencyCard(r: LatencyResult) {
    MetricCard("Latency (TCP-ping ${r.target}:${r.port})") {
        MetricRow("Avg", "%.1f ms".format(r.avgMs))
        MetricRow("Min / Max", "%.1f / %.1f ms".format(r.minMs, r.maxMs))
        MetricRow("Jitter", "%.1f ms".format(r.jitterMs))
        MetricRow("Loss", "%.0f%% (%d/%d)".format(r.lossPct, r.received, r.sent))
    }
}

@Composable
private fun DnsCard(results: List<DnsResult>, avgMs: Double?) {
    MetricCard("DNS resolution") {
        avgMs?.let { MetricRow("Average", "%.1f ms".format(it)) }
        results.forEach { d ->
            MetricRow(d.name, if (d.ok && d.ms != null) "%.0f ms".format(d.ms) else "failed")
        }
    }
}

@Composable
private fun ThroughputCard(r: ThroughputResult) {
    MetricCard("Download throughput") {
        MetricRow("Speed", "%.1f Mbps".format(r.mbps))
        MetricRow("Transferred", "%.1f MB".format(r.bytes / 1_000_000.0))
        MetricRow("Duration", "%.1f s".format(r.seconds))
    }
}

@Composable
private fun MetricCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
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

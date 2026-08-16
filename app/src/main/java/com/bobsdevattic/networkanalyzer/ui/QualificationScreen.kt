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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.network.QualRole
import com.bobsdevattic.networkanalyzer.network.QualResult
import com.bobsdevattic.networkanalyzer.network.QualState
import com.bobsdevattic.networkanalyzer.network.Verdict

/**
 * M7 screen: two-phone cable qualification. Pick SERVER on one phone and CLIENT
 * on the other, joined by the cable under test, then run the client.
 */
@Composable
fun QualificationScreen(
    state: QualState,
    onSetRole: (QualRole) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRunClient: (String) -> Unit,
    onCancelClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverIp by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cable Qualification", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Two phones, two adapters, the cable under test between them. This is a " +
                "pass/fail quality check — not a wiremap tester or a length meter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.role == QualRole.SERVER,
                onClick = { onSetRole(QualRole.SERVER) },
                label = { Text("Server") },
            )
            FilterChip(
                selected = state.role == QualRole.CLIENT,
                onClick = { onSetRole(QualRole.CLIENT) },
                label = { Text("Client") },
            )
        }

        when (state.role) {
            QualRole.SERVER -> ServerSection(state, onStartServer, onStopServer)
            QualRole.CLIENT -> ClientSection(
                state = state,
                serverIp = serverIp,
                onServerIpChange = { serverIp = it },
                onRun = { onRunClient(serverIp) },
                onCancel = onCancelClient,
            )
        }

        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ServerSection(
    state: QualState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (state.serverListening) {
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Stop server")
        }
    } else {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start server")
        }
    }

    if (state.serverAddress != null) {
        Card(
            Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Listening on", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.serverAddress, style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace)
                Text("Enter this IP on the client phone.",
                    style = MaterialTheme.typography.bodySmall)
                state.serverStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ClientSection(
    state: QualState,
    serverIp: String,
    onServerIpChange: (String) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    OutlinedTextField(
        value = serverIp,
        onValueChange = onServerIpChange,
        label = { Text("Server phone IP") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.running) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(state.phase?.let { "Cancel ($it…)" } ?: "Cancel")
        }
    } else {
        Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
            Text("Run qualification")
        }
    }

    state.result?.let { ResultCard(it) }
}

@Composable
private fun ResultCard(r: QualResult) {
    val (label, color) = when (r.verdict) {
        Verdict.PASS -> "PASS" to Color(0xFF2E7D32)
        Verdict.MARGINAL -> "MARGINAL" to Color(0xFFF9A825)
        Verdict.FAULT -> "FAULT" to Color(0xFFC62828)
        Verdict.UNKNOWN -> "INCONCLUSIVE" to Color(0xFF616161)
    }

    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = color)

            Metric("Link speed", r.linkSpeedMbps?.let { "$it Mbps" } ?: "unavailable")
            Metric("Download", "%.0f Mbps".format(r.downloadMbps))
            Metric("Upload", "%.0f Mbps".format(r.uploadMbps))
            Metric("RX errors", r.rxErrorsDelta.toString())
            Metric("Drops", r.droppedDelta.toString())

            r.reasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
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

package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.data.AdapterStatus
import com.bobsdevattic.networkanalyzer.data.EthernetInterfaceInfo
import com.bobsdevattic.networkanalyzer.network.CurrentWifi
import com.bobsdevattic.networkanalyzer.ui.theme.Status
import com.bobsdevattic.networkanalyzer.ui.theme.StatusChip

/**
 * M1 screen: shows the wired USB-C Ethernet link state and lets the user pin
 * app traffic to that interface. Unrooted-only — every value degrades to a
 * placeholder when the platform withholds it.
 */
@Composable
fun InterfaceScreen(
    info: EthernetInterfaceInfo,
    wifi: CurrentWifi?,
    onRefresh: () -> Unit,
    onBind: () -> Unit,
    onUnbind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Wired Link",
                style = MaterialTheme.typography.headlineSmall,
            )
            // ABSENT is neutral, not bad: an unplugged adapter is a normal state, and
            // a red error chip would overstate it.
            when (info.status) {
                AdapterStatus.CONNECTED -> StatusChip("connected", Status.GOOD)
                AdapterStatus.ABSENT -> StatusChip("no adapter", Status.NEUTRAL)
            }
        }

        when (info.status) {
            AdapterStatus.ABSENT -> AbsentCard()
            AdapterStatus.CONNECTED -> ConnectedCards(info)
        }

        wifi?.let { WifiCard(it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Text("Refresh")
            }
            if (info.status == AdapterStatus.CONNECTED) {
                if (info.boundToProcess) {
                    OutlinedButton(onClick = onUnbind, modifier = Modifier.weight(1f)) {
                        Text("Unbind")
                    }
                } else {
                    Button(onClick = onBind, modifier = Modifier.weight(1f)) {
                        Text("Bind traffic")
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsentCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No Ethernet adapter detected", style = MaterialTheme.typography.titleMedium)
            Text(
                "Plug in a USB-C Ethernet adapter with a live cable. Realtek " +
                    "(RTL8153/8156) and ASIX (AX88179) chipsets are best supported; " +
                    "some off-brand adapters won't enumerate on Android.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ConnectedCards(info: EthernetInterfaceInfo) {
    val bound = if (info.boundToProcess) "Yes" else "No"

    InfoCard(
        title = "Link",
        rows = listOf(
            "Interface" to info.interfaceName,
            "Speed" to info.linkSpeedMbps?.let { "$it Mbps" },
            "Duplex" to info.duplex,
            "MTU" to info.mtu?.toString(),
            "MAC" to info.macAddress,
            "Traffic bound to interface" to bound,
        ),
    )

    InfoCard(
        title = "IP configuration",
        rows = buildList {
            info.ipv4Addresses.forEachIndexed { i, a ->
                add((if (info.ipv4Addresses.size > 1) "IPv4 #${i + 1}" else "IPv4") to a)
            }
            add("Gateway" to info.gateway)
            if (info.dnsServers.isNotEmpty()) {
                add("DNS" to info.dnsServers.joinToString("\n"))
            } else {
                add("DNS" to null)
            }
            add("Search domains" to info.searchDomains)
            info.ipv6Addresses.forEachIndexed { i, a ->
                add((if (info.ipv6Addresses.size > 1) "IPv6 #${i + 1}" else "IPv6") to a)
            }
        },
    )

    val down = info.downstreamKbps?.let { "%.1f Mbps".format(it / 1000.0) }
    val up = info.upstreamKbps?.let { "%.1f Mbps".format(it / 1000.0) }
    InfoCard(
        title = "Platform estimate",
        rows = listOf(
            "Downstream" to down,
            "Upstream" to up,
        ),
        footnote = "Estimate reported by Android, not the negotiated PHY rate.",
    )
}

@Composable
private fun WifiCard(w: CurrentWifi) {
    val chan = w.channel?.let { "ch $it · ${w.band.label}" } ?: w.band.label
    InfoCard(
        title = "WiFi signal",
        rows = listOf(
            "Network" to w.ssid,
            "Signal" to "${signalGlyph(rssiBars(w.rssiDbm))}  ${w.rssiDbm} dBm",
            "Link speed" to w.linkSpeedMbps?.let { "$it Mbps" },
            "Channel" to chan,
        ),
        footnote = "From the phone's WiFi radio — refresh to update.",
    )
}

private fun rssiBars(dbm: Int): Int = when {
    dbm >= -55 -> 4
    dbm >= -66 -> 3
    dbm >= -77 -> 2
    dbm >= -88 -> 1
    else -> 0
}

private fun signalGlyph(bars: Int): String =
    "▮".repeat(bars) + "▯".repeat(4 - bars)

@Composable
private fun InfoCard(
    title: String,
    rows: List<Pair<String, String?>>,
    footnote: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) -> InfoRow(label, value) }
            if (footnote != null) {
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value ?: "unavailable",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (value != null) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.58f),
        )
    }
}

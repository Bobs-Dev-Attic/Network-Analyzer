package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs

/**
 * Runs latency, DNS, and throughput measurements over the wired Ethernet link.
 *
 * Everything is bound to the Ethernet [Network] via its own resolver
 * (getAllByName), SocketFactory, and openConnection — so results reflect the
 * wire, not WiFi/cellular. Unrooted: latency is TCP-ping, not ICMP.
 */
class SpeedQualityTester(context: Context) {

    private val interfaces = EthernetInterfaceManager(context)

    private fun network(): Network? = interfaces.findEthernetNetwork()

    // ---- Latency (TCP-ping) ------------------------------------------------

    sealed interface LatencyOutcome {
        data class Ok(val result: LatencyResult) : LatencyOutcome
        data class Error(val message: String) : LatencyOutcome
    }

    /**
     * TCP-ping [targetOverride] (or the gateway if null) [count] times.
     * [onProbe] fires (done, total) after each probe.
     */
    suspend fun latency(
        targetOverride: String?,
        count: Int = DEFAULT_PINGS,
        onProbe: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): LatencyOutcome = withContext(Dispatchers.IO) {
        val net = network() ?: return@withContext LatencyOutcome.Error("No wired Ethernet link.")

        val targetSpec = targetOverride?.takeIf { it.isNotBlank() }
            ?: interfaces.inspect().gateway
            ?: return@withContext LatencyOutcome.Error("No gateway to ping; enter a target.")

        val addr = resolve(net, targetSpec)
            ?: return@withContext LatencyOutcome.Error("Couldn't resolve $targetSpec.")

        val port = pickResponsivePort(net, addr)
            ?: return@withContext LatencyOutcome.Error(
                "$targetSpec didn't respond on ${PING_PORTS.joinToString(", ")}."
            )

        val samples = ArrayList<Double>(count)
        for (i in 0 until count) {
            probeRtt(net, addr, port)?.let { samples += it }
            onProbe(i + 1, count)
            if (i < count - 1) delay(PING_INTERVAL_MS)
        }

        if (samples.isEmpty()) {
            return@withContext LatencyOutcome.Error("All $count probes to $targetSpec timed out.")
        }

        LatencyOutcome.Ok(
            LatencyResult(
                target = targetSpec,
                port = port,
                sent = count,
                received = samples.size,
                minMs = samples.min(),
                avgMs = samples.average(),
                maxMs = samples.max(),
                jitterMs = jitter(samples),
            )
        )
    }

    private fun pickResponsivePort(net: Network, addr: InetAddress): Int? =
        PING_PORTS.firstOrNull { probeRtt(net, addr, it) != null }

    /** One TCP handshake timing in ms; null if it timed out (loss). */
    private fun probeRtt(net: Network, addr: InetAddress, port: Int): Double? {
        var socket: Socket? = null
        val start = System.nanoTime()
        return try {
            socket = net.socketFactory.createSocket()
            socket.connect(InetSocketAddress(addr, port), PING_TIMEOUT_MS)
            elapsedMs(start)
        } catch (e: ConnectException) {
            // RST (refused) is still a completed round trip.
            if (e.message?.contains("refused", ignoreCase = true) == true) elapsedMs(start)
            else null
        } catch (e: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }

    // ---- DNS ----------------------------------------------------------------

    suspend fun dns(names: List<String>): List<DnsResult> = withContext(Dispatchers.IO) {
        val net = network() ?: return@withContext names.map { DnsResult(it, null, false) }
        names.map { name ->
            val start = System.nanoTime()
            val ok = withTimeoutOrNull(DNS_TIMEOUT_MS) {
                runCatching { net.getAllByName(name).isNotEmpty() }.getOrDefault(false)
            } ?: false
            DnsResult(name, if (ok) elapsedMs(start) else null, ok)
        }
    }

    // ---- Throughput (HTTP download) ----------------------------------------

    sealed interface ThroughputOutcome {
        data class Ok(val result: ThroughputResult) : ThroughputOutcome
        data class Error(val message: String) : ThroughputOutcome
    }

    suspend fun download(url: String): ThroughputOutcome = withContext(Dispatchers.IO) {
        val net = network() ?: return@withContext ThroughputOutcome.Error("No wired Ethernet link.")

        val parsed = runCatching { URL(url) }.getOrNull()
            ?: return@withContext ThroughputOutcome.Error("Invalid URL.")

        var conn: HttpURLConnection? = null
        var stream: InputStream? = null
        try {
            conn = (net.openConnection(parsed) as HttpURLConnection).apply {
                connectTimeout = HTTP_CONNECT_TIMEOUT_MS
                readTimeout = HTTP_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext ThroughputOutcome.Error("Server returned HTTP $code.")
            }

            stream = conn.inputStream
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            val start = System.nanoTime()
            val deadline = start + MAX_DOWNLOAD_NANOS

            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total >= MAX_DOWNLOAD_BYTES || System.nanoTime() >= deadline) break
            }
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0

            if (total == 0L) {
                ThroughputOutcome.Error("No data received.")
            } else {
                ThroughputOutcome.Ok(ThroughputResult(url = url, bytes = total, seconds = seconds))
            }
        } catch (e: Exception) {
            ThroughputOutcome.Error(e.message ?: "Download failed.")
        } finally {
            runCatching { stream?.close() }
            runCatching { conn?.disconnect() }
        }
    }

    // ---- helpers ------------------------------------------------------------

    private fun resolve(net: Network, spec: String): InetAddress? =
        runCatching { net.getAllByName(spec).firstOrNull() }.getOrNull()

    private fun elapsedMs(startNanos: Long): Double =
        (System.nanoTime() - startNanos) / 1_000_000.0

    private fun jitter(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until samples.size) sum += abs(samples[i] - samples[i - 1])
        return sum / (samples.size - 1)
    }

    companion object {
        val DEFAULT_DNS_NAMES = listOf(
            "google.com", "cloudflare.com", "github.com", "amazon.com", "wikipedia.org",
        )

        /** ~25 MB from Cloudflare's speed endpoint; user-editable in the UI. */
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"

        private val PING_PORTS = listOf(53, 80, 443, 7)
        private const val DEFAULT_PINGS = 20
        private const val PING_TIMEOUT_MS = 1000
        private const val PING_INTERVAL_MS = 120L
        private const val DNS_TIMEOUT_MS = 3000L
        private const val HTTP_CONNECT_TIMEOUT_MS = 5000
        private const val HTTP_READ_TIMEOUT_MS = 8000
        private const val MAX_DOWNLOAD_BYTES = 60L * 1024 * 1024
        private const val MAX_DOWNLOAD_NANOS = 12_000_000_000L // 12s cap
    }
}

package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * Cooperative two-phone cable qualification (M7).
 *
 * One phone runs [serve] (SERVER), the other runs [runClient] (CLIENT), joined
 * by the cable under test. The client drives a timed download then upload,
 * measures throughput, and reads error/drop counter deltas on *both* ends (the
 * server returns its own in the upload ack). The verdict says whether the run
 * carried clean gigabit traffic — it is a qualifier, not a wiremap or certifier:
 *
 *  - FAULT    link negotiated below gigabit → a pair is open/broken/miswired
 *  - MARGINAL receive errors/drops climbed under load → damaged/too-long cable
 *  - PASS     clean 4-pair gigabit link, negligible errors
 *
 * Throughput is reported but NOT used to fail a run: many phone/adapter combos
 * are USB 2.0 and cap ~300 Mbps regardless of cable, so a low number there is a
 * host limit, not a cable fault.
 *
 * Requires both ends to have IPv4 addresses on the wired link (e.g. via a switch
 * with DHCP). Unrooted: plain TCP sockets bound to the Ethernet interface.
 */
class CableQualifier(context: Context) {

    private val interfaces = EthernetInterfaceManager(context)

    @Volatile
    private var serverSocket: ServerSocket? = null

    // ---- events -------------------------------------------------------------

    sealed interface ServerEvent {
        data class Listening(val address: String) : ServerEvent
        data class Status(val message: String) : ServerEvent
        data class Error(val message: String) : ServerEvent
    }

    sealed interface ClientOutcome {
        data class Ok(val result: QualResult) : ClientOutcome
        data class Error(val message: String) : ClientOutcome
    }

    /** IPv4 address (no prefix) of the wired link, or null if unassigned. */
    fun ethernetIpv4(): String? =
        interfaces.inspect().ipv4Addresses.firstOrNull()?.substringBefore('/')

    // ---- server -------------------------------------------------------------

    /** Bind and accept connections until the coroutine is cancelled. */
    suspend fun serve(onEvent: (ServerEvent) -> Unit) = withContext(Dispatchers.IO) {
        val ip = ethernetIpv4()
        if (ip == null) {
            onEvent(ServerEvent.Error("No IPv4 on the wired link. Connect both phones " +
                "through a switch/router that hands out addresses."))
            return@withContext
        }
        val iface = interfaces.currentInterfaceName()

        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(ip), PORT))
            }
        } catch (e: Exception) {
            onEvent(ServerEvent.Error("Couldn't listen: ${e.message}"))
            return@withContext
        }
        serverSocket = server
        onEvent(ServerEvent.Listening("$ip:$PORT"))
        onEvent(ServerEvent.Status("Waiting for the client phone…"))

        try {
            while (coroutineContext.isActive) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    if (server.isClosed) break else continue
                }
                onEvent(ServerEvent.Status("Client connected — running transfer…"))
                runCatching { handleConnection(socket, iface) }
                onEvent(ServerEvent.Status("Transfer complete. Ready for another run."))
            }
        } finally {
            runCatching { server.close() }
            serverSocket = null
        }
    }

    fun stopServer() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleConnection(socket: Socket, iface: String?) {
        socket.use { s ->
            s.tcpNoDelay = true
            val input = DataInputStream(s.getInputStream())
            val output = DataOutputStream(s.getOutputStream())

            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) return
            val mode = input.readByte().toInt().toChar()

            val before = iface?.let { StatisticsReader.read(it) }

            when (mode) {
                'D' -> streamFor(output, DURATION_NANOS) // client measures download
                'U' -> {
                    // Sink client's upload until EOF, timing from first byte.
                    val buf = ByteArray(BUFFER)
                    var total = 0L
                    var start = 0L
                    while (true) {
                        val n = try { input.read(buf) } catch (e: EOFException) { -1 }
                        if (n < 0) break
                        if (start == 0L) start = System.nanoTime()
                        total += n
                    }
                    val elapsedMicros = if (start == 0L) 0L else (System.nanoTime() - start) / 1000
                    val after = iface?.let { StatisticsReader.read(it) }
                    output.writeLong(total)
                    output.writeLong(elapsedMicros)
                    output.writeLong(delta(before?.rxErrors, after?.rxErrors))
                    output.writeLong(delta(before?.rxDropped, after?.rxDropped))
                    output.flush()
                }
                else -> return
            }
        }
    }

    // ---- client -------------------------------------------------------------

    suspend fun runClient(
        serverIp: String,
        onPhase: (String) -> Unit,
    ): ClientOutcome = withContext(Dispatchers.IO) {
        val network = interfaces.findEthernetNetwork()
            ?: return@withContext ClientOutcome.Error("No wired Ethernet link.")
        val iface = interfaces.currentInterfaceName()

        val before = iface?.let { StatisticsReader.read(it) }

        // Download: server streams, we time to EOF.
        onPhase("Download")
        val downloadMbps = try {
            network.socketFactory.createSocket().use { s ->
                s.connect(InetSocketAddress(serverIp, PORT), CONNECT_TIMEOUT_MS)
                s.tcpNoDelay = true
                val out = s.getOutputStream()
                out.write(MAGIC); out.write('D'.code); out.flush()

                val buf = ByteArray(BUFFER)
                var total = 0L
                var start = 0L
                val input = s.getInputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (start == 0L) start = System.nanoTime()
                    total += n
                }
                val secs = if (start == 0L) 0.0 else (System.nanoTime() - start) / 1e9
                if (secs > 0) total * 8.0 / secs / 1e6 else 0.0
            }
        } catch (e: Exception) {
            return@withContext ClientOutcome.Error("Download failed: ${e.message}")
        }

        // Upload: we stream, server measures and returns its stats.
        onPhase("Upload")
        val ack = try {
            network.socketFactory.createSocket().use { s ->
                s.connect(InetSocketAddress(serverIp, PORT), CONNECT_TIMEOUT_MS)
                s.tcpNoDelay = true
                val out = DataOutputStream(s.getOutputStream())
                out.write(MAGIC); out.write('U'.code); out.flush()
                streamFor(out, DURATION_NANOS)
                s.shutdownOutput()

                val input = DataInputStream(s.getInputStream())
                UploadAck(
                    bytes = input.readLong(),
                    elapsedMicros = input.readLong(),
                    rxErrorsDelta = input.readLong(),
                    droppedDelta = input.readLong(),
                )
            }
        } catch (e: Exception) {
            return@withContext ClientOutcome.Error("Upload failed: ${e.message}")
        }

        val uploadMbps = if (ack.elapsedMicros > 0) {
            ack.bytes * 8.0 / (ack.elapsedMicros / 1e6) / 1e6
        } else 0.0

        val after = iface?.let { StatisticsReader.read(it) }
        val clientRxErr = delta(before?.rxErrors, after?.rxErrors)
        val clientDrop = delta(before?.rxDropped, after?.rxDropped)
        val linkSpeed = iface?.let { LinkStatsReader.readSpeedMbps(it) }

        val combinedRxErr = clientRxErr + ack.rxErrorsDelta
        val combinedDrop = clientDrop + ack.droppedDelta

        ClientOutcome.Ok(
            evaluate(downloadMbps, uploadMbps, linkSpeed, combinedRxErr, combinedDrop)
        )
    }

    private fun evaluate(
        downloadMbps: Double,
        uploadMbps: Double,
        linkSpeed: Int?,
        rxErr: Long,
        dropped: Long,
    ): QualResult {
        val reasons = mutableListOf<String>()
        val verdict: Verdict

        when {
            linkSpeed != null && linkSpeed in 1..100 -> {
                verdict = Verdict.FAULT
                reasons += "Link negotiated at $linkSpeed Mbps — not all four pairs are " +
                    "carrying gigabit. A pair is likely open, broken, or miswired."
            }
            rxErr > ERROR_THRESHOLD || dropped > DROP_THRESHOLD -> {
                verdict = Verdict.MARGINAL
                reasons += "$rxErr receive errors and $dropped drops during the transfer — " +
                    "the cable may be damaged, too long, or picking up interference."
            }
            linkSpeed == null -> {
                verdict = Verdict.UNKNOWN
                reasons += "Link speed wasn't readable, so 4-pair gigabit couldn't be " +
                    "confirmed. Errors/drops were negligible."
            }
            else -> {
                verdict = Verdict.PASS
                reasons += "Clean gigabit link with negligible errors."
            }
        }

        if (linkSpeed != null && linkSpeed >= 1000) {
            reasons += "Gigabit link — all four pairs are electrically continuous."
        }
        reasons += "Throughput is capped by USB generation, not the cable, so it isn't " +
            "used to fail a run."

        return QualResult(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            linkSpeedMbps = linkSpeed,
            rxErrorsDelta = rxErr,
            droppedDelta = dropped,
            verdict = verdict,
            reasons = reasons,
        )
    }

    // ---- helpers ------------------------------------------------------------

    private fun streamFor(out: java.io.OutputStream, durationNanos: Long) {
        val buf = ByteArray(BUFFER)
        val deadline = System.nanoTime() + durationNanos
        while (System.nanoTime() < deadline) {
            out.write(buf)
        }
        out.flush()
    }

    private fun delta(before: Long?, after: Long?): Long {
        if (before == null || after == null) return 0
        return (after - before).coerceAtLeast(0)
    }

    private data class UploadAck(
        val bytes: Long,
        val elapsedMicros: Long,
        val rxErrorsDelta: Long,
        val droppedDelta: Long,
    )

    companion object {
        const val PORT = 52526
        private val MAGIC = "NAQ1".toByteArray(Charsets.US_ASCII)
        private const val BUFFER = 64 * 1024
        private const val DURATION_NANOS = 5_000_000_000L // 5s per direction
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val ERROR_THRESHOLD = 2L
        private const val DROP_THRESHOLD = 2L
    }
}

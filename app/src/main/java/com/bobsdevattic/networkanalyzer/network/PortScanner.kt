package com.bobsdevattic.networkanalyzer.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP-connect port scanner for a single host. Unrooted: no SYN scan, so we
 * classify from connect outcomes — connected = OPEN, refused = CLOSED, timeout =
 * FILTERED. Sockets are bound to the Ethernet network so the scan goes over the
 * wire. For OPEN ports we passively read any greeting the service sends (SSH,
 * FTP, SMTP, etc.) as a lightweight service confirmation — no payload is sent.
 */
class PortScanner(context: Context) {

    private val interfaces = EthernetInterfaceManager(context)

    sealed interface Result {
        data class Ok(val results: List<PortResult>) : Result
        data class Error(val message: String) : Result
    }

    suspend fun scan(
        host: String,
        ports: List<Int>,
        onProgress: (scanned: Int, total: Int) -> Unit,
    ): Result {
        val network = interfaces.findEthernetNetwork()
            ?: return Result.Error("No wired Ethernet link.")

        val target = runCatching { InetAddress.getByName(host) }.getOrNull()
            ?: return Result.Error("Invalid host: $host")

        val total = ports.size
        var scanned = 0
        onProgress(0, total)

        val semaphore = Semaphore(CONCURRENCY)
        val results = coroutineScope {
            ports.map { port ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val r = probePort(network, target, port)
                        synchronized(this@PortScanner) { onProgress(++scanned, total) }
                        r
                    }
                }
            }.awaitAll()
        }

        return Result.Ok(results)
    }

    private fun probePort(
        network: android.net.Network,
        target: InetAddress,
        port: Int,
    ): PortResult {
        var socket: Socket? = null
        return try {
            socket = network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(target, port), CONNECT_TIMEOUT_MS)
            PortResult(
                port = port,
                state = PortState.OPEN,
                service = ServiceCatalog.serviceFor(port),
                banner = grabBanner(socket),
            )
        } catch (e: ConnectException) {
            val closed = e.message?.contains("refused", ignoreCase = true) == true
            PortResult(port, if (closed) PortState.CLOSED else PortState.FILTERED,
                ServiceCatalog.serviceFor(port))
        } catch (e: Exception) {
            PortResult(port, PortState.FILTERED, ServiceCatalog.serviceFor(port))
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** Read an unsolicited greeting if one arrives quickly; null otherwise. */
    private fun grabBanner(socket: Socket): String? = runCatching {
        socket.soTimeout = BANNER_TIMEOUT_MS
        val buf = ByteArray(BANNER_MAX_BYTES)
        val n = socket.getInputStream().read(buf)
        if (n <= 0) return null
        String(buf, 0, n, Charsets.US_ASCII)
            .trim()
            .filter { it == '\t' || it.code in 0x20..0x7E }
            .take(BANNER_MAX_CHARS)
            .ifBlank { null }
    }.getOrNull()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 500
        private const val BANNER_TIMEOUT_MS = 600
        private const val BANNER_MAX_BYTES = 256
        private const val BANNER_MAX_CHARS = 120
        private const val CONCURRENCY = 64
        const val MAX_PORTS = 4096

        /**
         * Parse a ports spec like "22,80,443" or "1-1024" or "22,80,8000-8100".
         * Returns a sorted, de-duplicated, in-range list, or null if unparseable.
         */
        fun parsePortsSpec(spec: String): List<Int>? {
            val out = sortedSetOf<Int>()
            val tokens = spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            for (token in tokens) {
                if ("-" in token) {
                    val parts = token.split("-")
                    if (parts.size != 2) return null
                    val start = parts[0].trim().toIntOrNull() ?: return null
                    val end = parts[1].trim().toIntOrNull() ?: return null
                    if (start !in 1..65535 || end !in 1..65535 || start > end) return null
                    for (p in start..end) out += p
                } else {
                    val p = token.toIntOrNull() ?: return null
                    if (p !in 1..65535) return null
                    out += p
                }
                if (out.size > MAX_PORTS) return null
            }
            return out.toList()
        }
    }
}

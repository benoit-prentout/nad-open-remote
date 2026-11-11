package org.openremote.nad

import java.net.InetSocketAddress
import java.net.Socket

/** Parses one protocol line "Main.Key=Value"; null if it is not one. */
fun parseLine(line: String): Pair<String, String>? {
    val l = line.trim()
    if (!l.startsWith("Main.")) return null
    val i = l.indexOf('=')
    if (i < 0) return null
    return l.substring(5, i) to l.substring(i + 1)
}

/**
 * Minimal NAD C 338 client (TCP, lines ending CRLF, never start a command with a newline).
 * Stays connected: TCP keep-alive, a ping every [pingMs], a read timeout of 3 pings, fast reconnect with small backoff.
 * [onState] receives every pushed or answered key/value (from a background thread).
 */
class NadClient(
    private val host: String,
    private val port: Int = 30001,
    private val onState: (String, String) -> Unit,
    private val onConnected: (Boolean) -> Unit = {},
    private val pingMs: Long = 5000,
) {
    @Volatile private var socket: Socket? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread({ loop() }, "nad-client").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        socket?.close()
        thread?.interrupt()
    }

    /** Fire-and-forget: the amp echoes the new value, which arrives through [onState]. */
    fun set(key: String, value: String) = write("Main.$key=$value")

    fun refresh() = write("Main?")

    /** Call when the app comes back to the foreground: reconnect now if down, else just refresh the state. */
    fun reconnectNow() { if (socket == null) thread?.interrupt() else refresh() }

    /** Sends a full command line as typed; callers must make sure it is a protocol command. */
    fun sendRaw(line: String) = write(line)

    private fun write(cmd: String) {
        val s = socket ?: return
        Thread {
            try { synchronized(s) { s.getOutputStream().apply { write((cmd + "\r\n").toByteArray()); flush() } } }
            catch (e: Exception) { s.close() }
        }.start()
    }

    private fun loop() {
        var attempt = 0
        while (running) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 3000)
                s.keepAlive = true
                s.soTimeout = (pingMs * 3).toInt() // silence for 3 pings (the amp answers every ping) = dead link
                socket = s
                attempt = 0
                onConnected(true)
                refresh()
                Thread {
                    try { while (socket === s && running) { Thread.sleep(pingMs); if (socket === s) write("Main.Power?") } } catch (_: InterruptedException) {}
                }.also { it.isDaemon = true }.start()
                s.getInputStream().bufferedReader().forEachLine { line -> parseLine(line)?.let { onState(it.first, it.second) } }
            } catch (_: Exception) {
            }
            socket?.close()
            socket = null
            onConnected(false)
            if (running) try { Thread.sleep(minOf(500L shl minOf(attempt++, 4), 5000L)) } catch (_: InterruptedException) {} // 0.5s, 1s, 2s, 4s, 5s
        }
    }
}

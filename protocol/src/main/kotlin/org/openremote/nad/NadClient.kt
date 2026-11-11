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
 * Reconnects by itself; [onState] receives every pushed or answered key/value (from a background thread).
 */
class NadClient(
    private val host: String,
    private val port: Int = 30001,
    private val onState: (String, String) -> Unit,
    private val onConnected: (Boolean) -> Unit = {},
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
    }

    /** Fire-and-forget: the amp echoes the new value, which arrives through [onState]. */
    fun set(key: String, value: String) = write("Main.$key=$value")

    fun refresh() = write("Main?")

    private fun write(cmd: String) {
        val s = socket ?: return
        Thread {
            try { synchronized(s) { s.getOutputStream().apply { write((cmd + "\r\n").toByteArray()); flush() } } }
            catch (e: Exception) { e.printStackTrace(); s.close() }
        }.start()
    }

    private fun loop() {
        while (running) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 3000)
                socket = s
                onConnected(true)
                refresh()
                s.getInputStream().bufferedReader().forEachLine { line -> parseLine(line)?.let { onState(it.first, it.second) } }
            } catch (e: Exception) { e.printStackTrace() }
            socket = null
            onConnected(false)
            if (running) Thread.sleep(2000) // ponytail: fixed delay, add backoff if this ever spams the amp
        }
    }
}

package org.openremote.nad

import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Asks one host for its model; returns e.g. "NADC338" or null if it is not an amp. */
fun probe(host: String, port: Int = 30001, timeoutMs: Int = 1500): String? = try {
    Socket().use { s ->
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = timeoutMs
        s.getOutputStream().apply { write("Main.Model?\r\n".toByteArray()); flush() }
        parseLine(s.getInputStream().bufferedReader().readLine() ?: "")?.takeIf { it.first == "Model" }?.second
    }
} catch (_: Exception) { null }

/** Tries every host in parallel; returns host to model for those that answered. */
fun scan(hosts: List<String>, port: Int = 30001): Map<String, String> {
    val first = scanOnce(hosts, port)
    return if (first.isNotEmpty()) first else scanOnce(hosts, port) // a slow or just-booted amp can miss one pass
}

private fun scanOnce(hosts: List<String>, port: Int): Map<String, String> {
    val pool = Executors.newFixedThreadPool(64)
    val found = java.util.concurrent.ConcurrentHashMap<String, String>()
    hosts.forEach { h -> pool.execute { probe(h, port)?.let { found[h] = it } } }
    pool.shutdown(); pool.awaitTermination(30, TimeUnit.SECONDS)
    return found
}

/** All other addresses of the phone's /24 networks. ponytail: assumes /24, which is what home boxes use. */
fun localSubnetHosts(): List<String> =
    NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filter { it.address.size == 4 && it.isSiteLocalAddress }
        .flatMap { a -> val b = a.address.map { it.toInt() and 255 }; (1..254).filter { it != b[3] }.map { "${b[0]}.${b[1]}.${b[2]}.$it" } }

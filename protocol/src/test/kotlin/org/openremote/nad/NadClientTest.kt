package org.openremote.nad

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NadClientTest {
    @Test fun parse() {
        assertEquals("Volume" to "-56.0", parseLine("Main.Volume=-56.0\r\n"))
        assertNull(parseLine("garbage"))
    }

    /** Fake amp: answers Main? with a state, echoes sets. */
    @Test fun roundTripAgainstFakeAmp() {
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val received = mutableListOf<String>()
        Thread {
            val s = server.accept()
            val out = s.getOutputStream()
            s.getInputStream().bufferedReader().forEachLine { cmd ->
                received += cmd
                val reply = if (cmd == "Main?") "Main.Power=On\r\nMain.Volume=-56.0\r\n" else cmd + "\r\n"
                out.write(reply.toByteArray()); out.flush()
            }
        }.start()
        val state = java.util.concurrent.ConcurrentHashMap<String, String>()
        val got = CountDownLatch(3)
        val client = NadClient("127.0.0.1", server.localPort, { k, v -> state[k] = v; got.countDown() })
        client.start()
        Thread.sleep(300)
        client.set("Volume", "-40.0")
        assertTrue(got.await(3, TimeUnit.SECONDS))
        assertEquals("On", state["Power"])
        assertEquals("-40.0", state["Volume"])
        assertTrue("Main.Volume=-40.0" in received)
        client.stop(); server.close()
    }
}

class DiscoveryTest {
    @Test fun findsFakeAmpOnly() {
        val amp = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread { while (true) { val s = amp.accept(); s.getInputStream().bufferedReader().readLine(); s.getOutputStream().write("Main.Model=NADC338\r\n".toByteArray()); s.close() } }.start()
        val silent = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        assertEquals("NADC338", probe("127.0.0.1", amp.localPort))
        assertNull(probe("127.0.0.1", silent.localPort, 300))
        assertEquals(mapOf("127.0.0.1" to "NADC338"), scan(listOf("127.0.0.1", "127.0.0.3"), amp.localPort))
        amp.close(); silent.close()
    }
}

class AddressTest {
    @Test fun parses() {
        assertEquals("192.168.1.30" to 30001, parseAddress(" 192.168.1.30 "))
        assertEquals("amp.local" to 4000, parseAddress("nad://amp.local:4000/x"))
        assertEquals("10.0.0.2" to 30001, parseAddress("http://10.0.0.2/"))
        assertNull(parseAddress("")); assertNull(parseAddress("a b")); assertNull(parseAddress("host:99999")); assertNull(parseAddress("host:abc"))
    }
}

class ReconnectTest {
    @Test fun reconnectsAfterDrop() {
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread { repeat(2) { val c = server.accept(); c.getInputStream().read(); c.close() } ; while (true) server.accept() }.start()
        val ups = java.util.concurrent.atomic.AtomicInteger()
        val twice = CountDownLatch(2)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val client = NadClient("127.0.0.1", server.localPort, { _, _ -> }, { if (it) { ups.incrementAndGet(); twice.countDown() } }, onEvent = { events += it })
        client.start()
        assertTrue(twice.await(5, TimeUnit.SECONDS), "should reconnect after the server dropped it")
        assertTrue(events.any { it.startsWith("dropped") } && events.count { it.startsWith("connected") } >= 2, events.toString())
        client.stop(); server.close()
    }

    @Test fun silentLinkIsDetectedByTimeout() {
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread { while (true) server.accept() }.start() // accepts and never answers
        val down = CountDownLatch(1)
        val client = NadClient("127.0.0.1", server.localPort, { _, _ -> }, { if (!it) down.countDown() }, pingMs = 100)
        client.start()
        assertTrue(down.await(3, TimeUnit.SECONDS), "300 ms of silence must be seen as a dead link")
        client.stop(); server.close()
    }
}

class TaperTest {
    @Test fun finerWhenLoud() {
        assertEquals(0.6f, volumeGain(-60f, 12f))
        assertEquals(0.6f, volumeGain(-45f, 12f))
        assertTrue(volumeGain(-20f, 12f) < volumeGain(-45f, 12f))
        assertEquals(0.2f, volumeGain(12f, 12f), 0.001f)
        assertEquals(0.6f, volumeGain(-10f, -50f)) // limit below the comfort level: never divide by zero
    }
}

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

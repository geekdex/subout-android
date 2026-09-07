package io.github.geekdex.subout

import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.domain.tester.NodeTester
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class NodeTesterTest {

    @Test
    fun testInvalidHostAndPortReturnsTimeout() = runBlocking {
        val result1 = NodeTester.testTcpPing("", 80)
        assertEquals(Node.LATENCY_TIMEOUT, result1)

        val result2 = NodeTester.testTcpPing("127.0.0.1", -1)
        assertEquals(Node.LATENCY_TIMEOUT, result2)

        val result3 = NodeTester.testTcpPing("127.0.0.1", 70000)
        assertEquals(Node.LATENCY_TIMEOUT, result3)
    }

    @Test
    fun testConnectionRefusedReturnsTimeout() = runBlocking {
        // Find an unused local port
        val tempSocket = ServerSocket(0)
        val unusedPort = tempSocket.localPort
        tempSocket.close() // ensure port is closed

        val result = NodeTester.testTcpPing("127.0.0.1", unusedPort, timeoutMs = 200)
        assertEquals(Node.LATENCY_TIMEOUT, result)
    }

    @Test
    fun testLocalConnectSuccess() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val testThread = Thread {
            try {
                val client = serverSocket.accept()
                client.close()
            } catch (_: Exception) {}
        }
        testThread.start()

        val latency = NodeTester.testTcpPing("127.0.0.1", port, timeoutMs = 1000)
        serverSocket.close()
        testThread.join()

        assertTrue(latency >= 0)
    }

    @Test
    fun testNodeLatencyHelperProperties() {
        val untestedNode = Node(
            tag = "Untested",
            protocol = "vmess",
            server = "1.1.1.1",
            serverPort = 443,
            rawJson = "{}",
            subscriptionId = null,
            latency = null
        )
        assertTrue(untestedNode.isUntested)
        assertFalse(untestedNode.isTimeout)
        assertFalse(untestedNode.isSuccess)

        val timeoutNode = untestedNode.copy(latency = Node.LATENCY_TIMEOUT)
        assertFalse(timeoutNode.isUntested)
        assertTrue(timeoutNode.isTimeout)
        assertFalse(timeoutNode.isSuccess)

        val successNode = untestedNode.copy(latency = 120)
        assertFalse(successNode.isUntested)
        assertFalse(successNode.isTimeout)
        assertTrue(successNode.isSuccess)
    }

    @Test
    fun testLatencySorting() {
        val nodeFast = Node(id = 1, tag = "Fast", protocol = "vless", server = "1.1.1.1", serverPort = 443, rawJson = "{}", subscriptionId = null, latency = 50)
        val nodeSlow = Node(id = 2, tag = "Slow", protocol = "vless", server = "1.1.1.1", serverPort = 443, rawJson = "{}", subscriptionId = null, latency = 300)
        val nodeTimeout = Node(id = 3, tag = "Timeout", protocol = "vless", server = "1.1.1.1", serverPort = 443, rawJson = "{}", subscriptionId = null, latency = Node.LATENCY_TIMEOUT)
        val nodeUntested = Node(id = 4, tag = "Untested", protocol = "vless", server = "1.1.1.1", serverPort = 443, rawJson = "{}", subscriptionId = null, latency = null)

        val list = listOf(nodeUntested, nodeSlow, nodeTimeout, nodeFast)
        val sorted = list.sortedWith(
            compareBy<Node> {
                when {
                    it.latency == null -> 2
                    it.latency < 0 -> 1
                    else -> 0
                }
            }.thenBy { it.latency ?: Int.MAX_VALUE }
        )

        assertEquals(listOf(nodeFast, nodeSlow, nodeTimeout, nodeUntested), sorted)
    }

    @Test
    fun testBuildQuicProbePacket() {
        val packet = NodeTester.buildQuicProbePacket()
        assertEquals(1200, packet.size)
        // First byte should be Long Header (0xc0)
        assertEquals(0xc0.toByte(), packet[0])
    }

    @Test
    fun testLocalQuicPingSuccess() = runBlocking {
        val udpServer = java.net.DatagramSocket(0)
        val port = udpServer.localPort

        val testThread = Thread {
            try {
                val buf = ByteArray(2048)
                val pack = java.net.DatagramPacket(buf, buf.size)
                udpServer.receive(pack)
                // Echo back a response to simulate server reply
                val resp = java.net.DatagramPacket(byteArrayOf(0xc0.toByte(), 0, 0, 0, 0), 5, pack.socketAddress)
                udpServer.send(resp)
            } catch (_: Exception) {}
        }
        testThread.start()

        val latency = NodeTester.testQuicPing("127.0.0.1", port, timeoutMs = 1000)
        udpServer.close()
        testThread.join()

        assertTrue(latency >= 0)
    }

    @Test
    fun testNodePingRoutesToAppropriateProtocol() = runBlocking {
        val hy2Node = Node(
            tag = "Hy2",
            protocol = "hysteria2",
            server = "127.0.0.1",
            serverPort = 59999, // Unused port, should timeout
            rawJson = "{}",
            subscriptionId = null
        )
        val result = NodeTester.testNodePing(hy2Node, timeoutMs = 200)
        assertEquals(Node.LATENCY_TIMEOUT, result)
    }
}

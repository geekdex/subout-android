package io.github.geekdex.subout.domain.tester

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object NodeTester {

    suspend fun testTcpPing(host: String, port: Int, timeoutMs: Int = 3000): Int? =
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                val startTime = System.currentTimeMillis()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latency = (System.currentTimeMillis() - startTime).toInt()
                socket.close()
                latency
            } catch (_: Exception) {
                null
            }
        }
}

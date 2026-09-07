package io.github.geekdex.subout.domain.tester

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

object NodeTester {

    private const val TAG = "NodeTester"
    private val random = SecureRandom()

    private fun safeLog(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            // JVM test environment fallback
        }
    }

    /**
     * 获取底层的真实物理网络（Wi-Fi 或蜂窝移动网络），排除当前系统的 VPN 虚拟网卡。
     * 避免当手机后台开启了未配置好或故障的 VPN（如 sing-box SFA）时，
     * 所有直连测速请求被路由至 VPN 虚拟网卡导致被直接拒绝（Connection Refused）。
     */
    private fun getPhysicalNetwork(): Network? {
        return try {
            val context = SuboutApplication.instance
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null

            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) {
                    return activeNetwork
                }
            }

            val networks = cm.allNetworks
            // 优先选择非 VPN 的 Wi-Fi 网络
            val wifi = networks.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            if (wifi != null) return wifi

            // 其次选择非 VPN 的蜂窝移动网络
            networks.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 根据 RFC 9000 标准构造 QUIC Initial 协商探测报文。
     * 向服务器发送不受支持的保留版本号（0x1a2a3a4a），
     * 任何遵循 RFC 9000 标准的 QUIC 服务器（包括 Hysteria 2、TUIC、HTTP/3）
     * 收到后均会立即回复 Version Negotiation 报文，以此准确测得 UDP/QUIC 真实 RTT 往返延迟。
     */
    fun buildQuicProbePacket(): ByteArray {
        val dcid = ByteArray(8).apply { random.nextBytes(this) }
        val scid = ByteArray(8).apply { random.nextBytes(this) }
        val version = 0x1a2a3a4a // 特殊预留协商版本

        val packet = ByteArray(1200) // RFC 9000 要求 Initial 包至少 1200 字节以防止流量放大攻击
        var offset = 0
        packet[offset++] = 0xc0.toByte() // Long Header, Initial type
        packet[offset++] = (version ushr 24).toByte()
        packet[offset++] = (version ushr 16).toByte()
        packet[offset++] = (version ushr 8).toByte()
        packet[offset++] = version.toByte()

        packet[offset++] = dcid.size.toByte()
        System.arraycopy(dcid, 0, packet, offset, dcid.size)
        offset += dcid.size

        packet[offset++] = scid.size.toByte()
        System.arraycopy(scid, 0, packet, offset, scid.size)
        offset += scid.size

        // 其余字节保持 0x00 填充，总长度达到 1200 字节
        return packet
    }

    /**
     * 对 UDP/QUIC 类型的节点进行握手探测（适用于 Hysteria, Hysteria2, TUIC 等）。
     */
    suspend fun testQuicPing(host: String, port: Int, timeoutMs: Int = 3000): Int =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            if (cleanHost.isBlank() || port !in 1..65535) {
                return@withContext Node.LATENCY_TIMEOUT
            }

            val result = withTimeoutOrNull(timeoutMs + 1000L) {
                try {
                    DatagramSocket().use { socket ->
                        val physicalNet = getPhysicalNetwork()
                        physicalNet?.bindSocket(socket)
                        socket.soTimeout = timeoutMs

                        val socketAddress = if (physicalNet != null) {
                            val addresses = try {
                                physicalNet.getAllByName(cleanHost)
                            } catch (_: Exception) {
                                InetAddress.getAllByName(cleanHost)
                            }
                            if (addresses.isEmpty()) return@use Node.LATENCY_TIMEOUT
                            InetSocketAddress(addresses[0], port)
                        } else {
                            InetSocketAddress(cleanHost, port)
                        }

                        val packetData = buildQuicProbePacket()
                        val sendPacket = DatagramPacket(packetData, packetData.size, socketAddress)

                        val startTime = System.currentTimeMillis()
                        socket.send(sendPacket)

                        val receiveBuffer = ByteArray(2048)
                        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(receivePacket)

                        val latency = (System.currentTimeMillis() - startTime).toInt()
                        if (latency >= 0) latency else 0
                    }
                } catch (e: Exception) {
                    safeLog("QUIC Ping failed for $cleanHost:$port: ${e.javaClass.simpleName} - ${e.message}")
                    Node.LATENCY_TIMEOUT
                }
            }
            result ?: Node.LATENCY_TIMEOUT
        }

    /**
     * 对 TCP 类型的节点进行 TCP 三次握手测速（适用于 VMess, VLESS, Shadowsocks, Trojan, HTTP, SOCKS 等）。
     */
    suspend fun testTcpPing(host: String, port: Int, timeoutMs: Int = 3000): Int =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            if (cleanHost.isBlank() || port !in 1..65535) {
                return@withContext Node.LATENCY_TIMEOUT
            }

            val result = withTimeoutOrNull(timeoutMs + 1000L) {
                try {
                    Socket().use { socket ->
                        val physicalNet = getPhysicalNetwork()
                        physicalNet?.bindSocket(socket)

                        val socketAddress = if (physicalNet != null) {
                            val addresses = try {
                                physicalNet.getAllByName(cleanHost)
                            } catch (_: Exception) {
                                InetAddress.getAllByName(cleanHost)
                            }
                            if (addresses.isEmpty()) return@use Node.LATENCY_TIMEOUT
                            InetSocketAddress(addresses[0], port)
                        } else {
                            InetSocketAddress(cleanHost, port)
                        }

                        val startTime = System.currentTimeMillis()
                        socket.connect(socketAddress, timeoutMs)
                        val latency = (System.currentTimeMillis() - startTime).toInt()
                        if (latency >= 0) latency else 0
                    }
                } catch (e: Exception) {
                    safeLog("Ping failed for $cleanHost:$port: ${e.javaClass.simpleName} - ${e.message}")
                    Node.LATENCY_TIMEOUT
                }
            }
            result ?: Node.LATENCY_TIMEOUT
        }

    /**
     * 智能路由测速入口：
     * 根据节点协议（UDP 类 vs TCP 类）自动选择最佳探测方式，并在首选方式超时时提供容错备用探测。
     */
    suspend fun testNodePing(node: Node, timeoutMs: Int = 3000): Int =
        testNodePing(node.protocol, node.server, node.serverPort, timeoutMs)

    suspend fun testNodePing(protocol: String, host: String, port: Int, timeoutMs: Int = 3000): Int {
        val isUdpProtocol = when (protocol.lowercase().trim()) {
            "hysteria", "hysteria2", "hy2", "tuic", "wireguard" -> true
            else -> false
        }

        return if (isUdpProtocol) {
            val quicLatency = testQuicPing(host, port, timeoutMs)
            if (quicLatency >= 0) {
                quicLatency
            } else {
                testTcpPing(host, port, timeoutMs)
            }
        } else {
            val tcpLatency = testTcpPing(host, port, timeoutMs)
            if (tcpLatency >= 0) {
                tcpLatency
            } else {
                testQuicPing(host, port, timeoutMs)
            }
        }
    }
}

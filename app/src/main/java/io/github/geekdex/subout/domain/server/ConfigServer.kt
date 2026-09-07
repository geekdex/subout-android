package io.github.geekdex.subout.domain.server

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 针对 sing-box 客户端优化的本地 HTTP 配置服务。
 * 完整解析 HTTP 头并规范输出响应，避免未读取完毕时关闭套接字导致 TCP RST 错误。
 */
class ConfigServer {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var currentPort: Int = 8888
        private set

    @Volatile
    var configContent: String = "{}"
        private set

    /**
     * 可选的动态配置生成器。若设置，在收到 GET /config 时自动调用以生成最新配置。
     */
    var contentProvider: (suspend () -> String)? = null

    val currentUrl: String
        get() = "http://127.0.0.1:$currentPort/config"

    /**
     * 更新正在服务的配置内容（若服务正在运行，无需重启端口，下一次请求立即生效）
     */
    fun updateContent(newJson: String) {
        configContent = newJson
    }

    @Synchronized
    fun start(jsonConfig: String, preferredPort: Int = 8888): Result<String> {
        stop()
        configContent = jsonConfig

        var port = preferredPort
        var bound = false

        while (port <= preferredPort + 20 && !bound) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                serverSocket = socket
                currentPort = port
                bound = true
            } catch (_: Exception) {
                port++
            }
        }

        if (!bound || serverSocket == null) {
            return Result.failure(Exception("无法绑定端口 (已尝试 $preferredPort - ${preferredPort + 20})"))
        }

        isRunning = true
        serverJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    launch {
                        handleClient(client)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        return Result.success(currentUrl)
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.use { s ->
                s.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = if (parts.isNotEmpty()) parts[0].uppercase() else "GET"
                val path = if (parts.size > 1) parts[1] else "/"

                // 务必完整读取 HTTP 请求头直至空行，否则未读取内容在套接字关闭时会导致内核向对端发送 TCP RST
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    line = reader.readLine()
                }

                val os = s.getOutputStream()

                when {
                    method == "GET" && path.startsWith("/config") -> {
                        val activeJson = try {
                            contentProvider?.invoke() ?: configContent
                        } catch (e: Exception) {
                            android.util.Log.e("SuboutConfigServer", "Dynamic content provider error, using cached config", e)
                            configContent
                        }
                        configContent = activeJson
                        val bodyBytes = activeJson.toByteArray(StandardCharsets.UTF_8)
                        val headers = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json; charset=utf-8\r\n" +
                                "Content-Length: ${bodyBytes.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                        os.write(headers.toByteArray(StandardCharsets.UTF_8))
                        os.write(bodyBytes)
                        os.flush()
                        android.util.Log.i("SuboutConfigServer", "Served /config (${bodyBytes.size} bytes) to ${s.inetAddress}")
                    }
                    method == "GET" && (path.startsWith("/ping") || path == "/") -> {
                        val msg = "Subout Config Server is running. Use /config to get sing-box configuration.\n"
                        val bodyBytes = msg.toByteArray(StandardCharsets.UTF_8)
                        val headers = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain; charset=utf-8\r\n" +
                                "Content-Length: ${bodyBytes.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                        os.write(headers.toByteArray(StandardCharsets.UTF_8))
                        os.write(bodyBytes)
                        os.flush()
                    }
                    else -> {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        os.write(notFound.toByteArray(StandardCharsets.UTF_8))
                        os.flush()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SuboutConfigServer", "Client connection error: ${e.message}")
        }
    }

    companion object {
        fun generateQrCode(content: String, size: Int = 512): Bitmap? {
            return try {
                val hints = mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 1
                )
                val bitMatrix = QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    hints
                )
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
                bitmap
            } catch (_: Exception) {
                null
            }
        }

        fun getSfaImportUri(serverUrl: String, profileName: String = "Subout"): String {
            val encodedUrl = URLEncoder.encode(serverUrl, "UTF-8")
            val encodedName = URLEncoder.encode(profileName, "UTF-8")
            return "sing-box://import-remote-profile?url=$encodedUrl#$encodedName"
        }
    }
}

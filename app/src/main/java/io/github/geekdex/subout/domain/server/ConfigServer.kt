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
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

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

    val currentUrl: String
        get() = "http://127.0.0.1:$currentPort/config"

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

    private fun handleClient(client: Socket) {
        try {
            client.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"

                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)

                when {
                    path.startsWith("/config") -> {
                        val bodyBytes = configContent.toByteArray(StandardCharsets.UTF_8)
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: application/json; charset=utf-8\r\n")
                        writer.print("Content-Length: ${bodyBytes.size}\r\n")
                        writer.print("Access-Control-Allow-Origin: *\r\n")
                        writer.print("Connection: close\r\n")
                        writer.print("\r\n")
                        writer.flush()
                        s.getOutputStream().write(bodyBytes)
                        s.getOutputStream().flush()
                    }
                    path.startsWith("/ping") -> {
                        val msg = "pong"
                        val bodyBytes = msg.toByteArray(StandardCharsets.UTF_8)
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: text/plain; charset=utf-8\r\n")
                        writer.print("Content-Length: ${bodyBytes.size}\r\n")
                        writer.print("Connection: close\r\n")
                        writer.print("\r\n")
                        writer.print(msg)
                        writer.flush()
                    }
                    else -> {
                        val msg = "Subout Config Server Running. Use /config to get sing-box configuration."
                        val bodyBytes = msg.toByteArray(StandardCharsets.UTF_8)
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: text/plain; charset=utf-8\r\n")
                        writer.print("Content-Length: ${bodyBytes.size}\r\n")
                        writer.print("Connection: close\r\n")
                        writer.print("\r\n")
                        writer.print(msg)
                        writer.flush()
                    }
                }
            }
        } catch (_: Exception) {
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
    }
}

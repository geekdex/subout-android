package io.github.geekdex.subout.data.network

import io.github.geekdex.subout.domain.model.ProxyNode
import io.github.geekdex.subout.domain.parser.ProxyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubscriptionFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetch(urlOrContent: String): Result<Pair<List<ProxyNode>, List<String>>> =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = urlOrContent.trim()

                // If it's a URL, fetch content over network
                val rawContent = if (trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    val request = Request.Builder()
                        .url(trimmed)
                        .header("User-Agent", "sing-box/1.10.0 subout-android/1.0.0")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(
                                Exception("HTTP 请求失败: ${response.code} ${response.message}")
                            )
                        }
                        response.body?.string() ?: ""
                    }
                } else {
                    // Plain text / Base64 / URI content directly
                    trimmed
                }

                if (rawContent.isBlank()) {
                    return@withContext Result.failure(Exception("订阅内容为空"))
                }

                val (nodes, skipped) = ProxyParser.parseSubscription(rawContent)
                if (nodes.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("未能解析出有效节点 (跳过公告节点数: ${skipped.size})")
                    )
                }

                Result.success(Pair(nodes, skipped))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

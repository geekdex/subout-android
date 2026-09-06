package io.github.geekdex.subout.data.network

import android.util.Log
import io.github.geekdex.subout.domain.model.ProxyNode
import io.github.geekdex.subout.domain.parser.ProxyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubscriptionFetcher {

    companion object {
        private const val TAG = "SubscriptionFetcher"
        private const val UA_SING_BOX = "sing-box/1.10.0 subout-android/1.0.0"
        private const val UA_FALLBACK = "ClashMeta/1.18.0 v2rayN/6.23"
    }

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
                if (trimmed.isBlank()) {
                    return@withContext Result.failure(Exception("订阅内容为空"))
                }

                if (trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    fetchFromUrl(trimmed)
                } else {
                    // Plain text / Base64 / URI / JSON content directly
                    val (nodes, skipped) = ProxyParser.parseSubscription(trimmed)
                    if (nodes.isEmpty()) {
                        Result.failure(Exception("未能解析出有效节点 (跳过公告节点数: ${skipped.size})"))
                    } else {
                        Result.success(Pair(nodes, skipped))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchFromUrl(url: String): Result<Pair<List<ProxyNode>, List<String>>> {
        // Step 1: Try with sing-box User-Agent (returns full sing-box JSON configuration on modern airports)
        val primaryResult = executeFetch(url, UA_SING_BOX)
        if (primaryResult.isSuccess) {
            val content = primaryResult.getOrThrow()
            val (nodes, skipped) = ProxyParser.parseSubscription(content)
            if (nodes.isNotEmpty()) {
                return Result.success(Pair(nodes, skipped))
            }
            Log.w(TAG, "Primary UA ($UA_SING_BOX) returned 0 valid nodes. Trying fallback UA...")
        } else {
            Log.w(TAG, "Primary UA ($UA_SING_BOX) failed: ${primaryResult.exceptionOrNull()?.message}. Trying fallback UA...")
        }

        // Step 2: Fallback with Clash/v2rayN User-Agent (returns standard Base64 URI lines)
        val fallbackResult = executeFetch(url, UA_FALLBACK)
        if (fallbackResult.isSuccess) {
            val content = fallbackResult.getOrThrow()
            val (nodes, skipped) = ProxyParser.parseSubscription(content)
            if (nodes.isNotEmpty()) {
                return Result.success(Pair(nodes, skipped))
            }
            return Result.failure(Exception("未能解析出有效节点 (跳过公告节点数: ${skipped.size})"))
        }

        val err = primaryResult.exceptionOrNull() ?: fallbackResult.exceptionOrNull()
        return Result.failure(err ?: Exception("订阅请求失败"))
    }

    private fun executeFetch(url: String, userAgent: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code} ${response.message}"))
                } else {
                    val body = response.body?.string() ?: ""
                    if (body.isBlank()) {
                        Result.failure(Exception("响应内容为空"))
                    } else {
                        Result.success(body)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

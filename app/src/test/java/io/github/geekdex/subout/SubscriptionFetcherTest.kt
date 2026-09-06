package io.github.geekdex.subout

import io.github.geekdex.subout.data.network.SubscriptionFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFetcherTest {

    private val fetcher = SubscriptionFetcher()

    @Test
    fun testFeiniaoLiveSubscription() = runBlocking {
        val url = "https://388583885.459245.xyz/api/v1/client/subscribe?token=117f1d288bc0c0e7a970af1196018af4"
        val result = fetcher.fetch(url)
        assertTrue("Expected feiniao fetch to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val (nodes, skipped) = result.getOrThrow()
        println("feiniao nodes parsed: ${nodes.size}, skipped announcements: ${skipped.size}")
        assertTrue("feiniao should have nodes", nodes.isNotEmpty())
        assertFalse("feiniao should have skipped announcements", skipped.isEmpty())
    }

    @Test
    fun testGhelperLiveSubscription() = runBlocking {
        val url = "https://08a7ade6.ghelper.me/subs/60e25dc808a7ade6b365c4fc901f0e4c"
        val result = fetcher.fetch(url)
        assertTrue("Expected Ghelper fetch to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val (nodes, skipped) = result.getOrThrow()
        println("Ghelper nodes parsed: ${nodes.size}, skipped announcements: ${skipped.size}")
        assertTrue("Ghelper should have nodes", nodes.isNotEmpty())
    }

    @Test
    fun testCfLiveSubscription() = runBlocking {
        val url = "https://edge.aoco.tech/sub?token=ee1499da73737a633e8c8094561fbc08&b64"
        val result = fetcher.fetch(url)
        assertTrue("Expected cf fetch to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val (nodes, skipped) = result.getOrThrow()
        println("cf nodes parsed: ${nodes.size}, skipped announcements: ${skipped.size}")
        assertTrue("cf should have nodes", nodes.isNotEmpty())
    }

    @Test
    fun testDirectPlainTextContent() = runBlocking {
        val content = """
            vless://uuid1@server1.com:443?security=reality&sni=sni.com#HK-1
            vless://uuid2@server2.com:443?security=reality&sni=sni.com#HK-2
        """.trimIndent()
        val result = fetcher.fetch(content)
        assertTrue(result.isSuccess)
        val (nodes, _) = result.getOrThrow()
        assertTrue(nodes.size == 2)
    }
}

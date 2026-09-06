package io.github.geekdex.subout

import io.github.geekdex.subout.domain.parser.ProxyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProxyParserTest {

    @Test
    fun testBase64Decode() {
        val raw = "Hello Subout Android"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray())
        val decoded = ProxyParser.decodeBase64(encoded)
        assertEquals(raw, decoded)
    }

    @Test
    fun testParseVmessNode() {
        // vmess json: {"add":"1.2.3.4","port":443,"id":"11111111-2222-3333-4444-555555555555","aid":0,"net":"ws","path":"/ws","tls":"tls","ps":"VMess-Node"}
        val json = """{"add":"1.2.3.4","port":443,"id":"11111111-2222-3333-4444-555555555555","aid":0,"net":"ws","path":"/ws","tls":"tls","ps":"VMess-Node"}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val line = "vmess://$encoded"

        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("VMess-Node", node!!.tag)
        assertEquals("vmess", node.protocol)
        assertEquals("1.2.3.4", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.rawJson.contains("\"type\":\"vmess\""))
        assertTrue(node.rawJson.contains("\"uuid\":\"11111111-2222-3333-4444-555555555555\""))
    }

    @Test
    fun testParseVlessRealityNode() {
        val line = "vless://uuid-1234@server.com:443?security=reality&sni=sni.example.com&pbk=publicKey123&fp=chrome&flow=xtls-rprx-vision#HK-Reality"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("HK-Reality", node!!.tag)
        assertEquals("vless", node.protocol)
        assertEquals("server.com", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.rawJson.contains("\"reality\""))
        assertTrue(node.rawJson.contains("publicKey123"))
        assertTrue(node.rawJson.contains("xtls-rprx-vision"))
    }

    @Test
    fun testParseShadowsocksNode() {
        // method:password -> chacha20-ietf-poly1305:secret123
        val auth = Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:secret123".toByteArray())
        val line = "ss://$auth@ss.example.com:8388#SS-Node"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("SS-Node", node!!.tag)
        assertEquals("shadowsocks", node.protocol)
        assertEquals("ss.example.com", node.server)
        assertEquals(8388, node.serverPort)
        assertTrue(node.rawJson.contains("\"method\":\"chacha20-ietf-poly1305\""))
        assertTrue(node.rawJson.contains("\"password\":\"secret123\""))
    }

    @Test
    fun testParseTrojanNode() {
        val line = "trojan://password123@trojan.example.com:443?sni=trojan.example.com#Trojan-US"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("Trojan-US", node!!.tag)
        assertEquals("trojan", node.protocol)
        assertEquals("trojan.example.com", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.rawJson.contains("\"password\":\"password123\""))
    }

    @Test
    fun testParseHysteria2Node() {
        val line = "hysteria2://myPass@hy2.example.com:443?insecure=1&sni=hy2.example.com&obfs=salamander&obfs-password=obfspass#Hy2-Node"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("Hy2-Node", node!!.tag)
        assertEquals("hysteria2", node.protocol)
        assertEquals("hy2.example.com", node.server)
        assertTrue(node.rawJson.contains("\"type\":\"hysteria2\""))
        assertTrue(node.rawJson.contains("\"password\":\"myPass\""))
        assertTrue(node.rawJson.contains("\"obfs\""))
    }

    @Test
    fun testAnnouncementFiltering() {
        val notice1 = "trojan://pass@127.0.0.1:443#NoticeLocalhost"
        val parsed1 = ProxyParser.parseLine(notice1)
        assertNotNull(parsed1)
        assertTrue(parsed1!!.isAnnouncement())

        val notice2 = "trojan://pass@server.com:443#套餐剩余流量50GB-到期提醒"
        val parsed2 = ProxyParser.parseLine(notice2)
        assertNotNull(parsed2)
        assertTrue(parsed2!!.isAnnouncement())

        val realNode = "trojan://pass@server.com:443#香港-01-BGP"
        val parsedReal = ProxyParser.parseLine(realNode)
        assertNotNull(parsedReal)
        assertFalse(parsedReal!!.isAnnouncement())
    }

    @Test
    fun testSubscriptionParseAndDeduplication() {
        val content = """
            trojan://pass@server.com:443#公告: 官网地址
            trojan://pass@server1.com:443#香港-01
            trojan://pass@server2.com:443#香港-01
            trojan://pass@server3.com:443#新加坡-01
        """.trimIndent()

        val (nodes, skipped) = ProxyParser.parseSubscription(content)
        assertEquals(1, skipped.size)
        assertEquals(3, nodes.size)
        assertEquals("香港-01", nodes[0].tag)
        assertEquals("香港-01-2", nodes[1].tag)
        assertEquals("新加坡-01", nodes[2].tag)
    }

    @Test
    fun testParseSingBoxJsonSubscription() {
        val singBoxJson = """
            {
              "dns": {"servers": [{"tag": "remote", "address": "https://1.1.1.1/dns-query"}]},
              "outbounds": [
                {"type": "direct", "tag": "DIRECT"},
                {"type": "selector", "tag": "节点选择", "outbounds": ["node-1", "node-2"]},
                {"type": "hysteria2", "tag": "剩余流量：100 GB", "server": "hy2.test.com", "server_port": 8443, "password": "p1"},
                {"type": "hysteria2", "tag": "新加坡01", "server": "sg01.test.com", "server_port": 8443, "password": "p1", "tls": {"enabled": true, "insecure": true}},
                {"type": "vless", "tag": "香港01", "server": "hk01.test.com", "server_port": 443, "uuid": "u1", "tls": {"enabled": true}},
                {"type": "tuic", "tag": "日本01", "server": "jp01.test.com", "server_port": 443, "uuid": "u2", "password": "p2", "congestion_control": "bbr"}
              ]
            }
        """.trimIndent()

        val (nodes, skipped) = ProxyParser.parseSubscription(singBoxJson)
        assertEquals(1, skipped.size)
        assertEquals("剩余流量：100 GB", skipped[0])
        assertEquals(3, nodes.size)

        assertEquals("新加坡01", nodes[0].tag)
        assertEquals("hysteria2", nodes[0].protocol)
        assertEquals("sg01.test.com", nodes[0].server)
        assertEquals(8443, nodes[0].serverPort)
        assertTrue(nodes[0].insecure)

        assertEquals("香港01", nodes[1].tag)
        assertEquals("vless", nodes[1].protocol)
        assertEquals("hk01.test.com", nodes[1].server)

        assertEquals("日本01", nodes[2].tag)
        assertEquals("tuic", nodes[2].protocol)
        assertEquals("jp01.test.com", nodes[2].server)
    }

    @Test
    fun testParseTuicNode() {
        val line = "tuic://uuid-1:pass-1@tuic.example.com:443?congestion_control=bbr&alpn=h3&sni=tuic.example.com&allow_insecure=1#TUIC-Node"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("TUIC-Node", node!!.tag)
        assertEquals("tuic", node.protocol)
        assertEquals("tuic.example.com", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.insecure)
        assertTrue(node.rawJson.contains("\"congestion_control\":\"bbr\""))
        assertTrue(node.rawJson.contains("\"uuid\":\"uuid-1\""))
        assertTrue(node.rawJson.contains("\"password\":\"pass-1\""))
    }

    @Test
    fun testParseAnyTlsNode() {
        val line = "anytls://secret@anytls.example.com:443?peer=sni.example.com&allowInsecure=1#AnyTLS-Node"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("AnyTLS-Node", node!!.tag)
        assertEquals("trojan", node.protocol)
        assertEquals("anytls.example.com", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.insecure)
        assertTrue(node.rawJson.contains("\"password\":\"secret\""))
        assertTrue(node.rawJson.contains("\"server_name\":\"sni.example.com\""))
    }

    @Test
    fun testParseHttpsBase64Line() {
        // user:pass@server.com:443/#Node-Tag
        val raw = "user:pass@server.com:443/#Node-Tag"
        val b64 = Base64.getEncoder().encodeToString(raw.toByteArray())
        val line = "https://$b64"
        val node = ProxyParser.parseLine(line)
        assertNotNull(node)
        assertEquals("Node-Tag", node!!.tag)
        assertEquals("http", node.protocol)
        assertEquals("server.com", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.rawJson.contains("\"username\":\"user\""))
        assertTrue(node.rawJson.contains("\"password\":\"pass\""))
    }
}

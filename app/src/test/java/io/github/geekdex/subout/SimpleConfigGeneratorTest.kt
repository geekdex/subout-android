package io.github.geekdex.subout

import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleInboundConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleConfigGeneratorTest {

    @Test
    fun testGenerateDefaultConfigStructure() {
        val cfg = SimpleConfig()
        val dummyNodes = listOf(
            Node(
                id = 1,
                subscriptionId = 1,
                tag = "HK-01",
                protocol = "trojan",
                server = "hk.example.com",
                serverPort = 443,
                rawJson = """{"type":"trojan","tag":"HK-01","server":"hk.example.com","server_port":443,"password":"pwd"}""",
                enabled = true
            )
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)

        // Must contain all core top-level sections
        assertNotNull(json.get("log"))
        assertNotNull(json.get("dns"))
        assertNotNull(json.get("inbounds"))
        assertNotNull(json.get("outbounds"))
        assertNotNull(json.get("route"))
        assertNotNull(json.get("experimental"))

        // Strategy must be ipv4_only
        val dns = json.getAsJsonObject("dns")
        assertEquals("ipv4_only", dns.get("strategy").asString)

        // Default DNS mode is preset_fakeip
        val servers = dns.getAsJsonArray("servers")
        assertTrue(servers.any { it.asJsonObject.get("tag").asString == "dns_fakeip" })

        // Default Inbound is TUN
        val inbounds = json.getAsJsonArray("inbounds")
        assertEquals("tun", inbounds[0].asJsonObject.get("type").asString)
        assertEquals("tun-in", inbounds[0].asJsonObject.get("tag").asString)

        // Outbounds contains selector and urltest
        val outbounds = json.getAsJsonArray("outbounds")
        val proxySelector = outbounds.firstOrNull { it.asJsonObject.get("tag")?.asString == "proxy" }
        assertNotNull(proxySelector)
        val autoTest = outbounds.firstOrNull { it.asJsonObject.get("tag")?.asString == "AUTO-Test" }
        assertNotNull(autoTest)

        // Route contains rules and rule_sets
        val route = json.getAsJsonObject("route")
        assertTrue(route.getAsJsonArray("rules").size() > 0)
        assertTrue(route.getAsJsonArray("rule_set").size() > 0)
    }

    @Test
    fun testMixedInboundAndDomesticForeignDns() {
        val cfg = SimpleConfig(
            dns = SimpleDnsConfig(mode = "preset_domestic_foreign", domestic_dns = "223.5.5.5", foreign_dns = "https://1.1.1.1/dns-query"),
            inbound = SimpleInboundConfig(inbound_type = "mixed", mixed_port = 2080, allow_lan = false),
            route = SimpleRouteConfig(mode = "smart")
        )

        val json = SimpleConfigGenerator.generate(cfg, emptyList())

        val inbounds = json.getAsJsonArray("inbounds")
        assertEquals("mixed", inbounds[0].asJsonObject.get("type").asString)
        assertEquals(2080, inbounds[0].asJsonObject.get("listen_port").asInt)
        assertEquals("127.0.0.1", inbounds[0].asJsonObject.get("listen").asString)

        val dns = json.getAsJsonObject("dns")
        val servers = dns.getAsJsonArray("servers")
        assertTrue(servers.any { it.asJsonObject.get("tag").asString == "dns_remote" })
    }

    @Test
    fun testDumpExampleConfigFile() {
        val cfg = SimpleConfig()
        val dummyNodes = listOf(
            Node(
                id = 1,
                subscriptionId = 1,
                tag = "🇭🇰 香港-01-IEPL",
                protocol = "trojan",
                server = "hk01.node.com",
                serverPort = 443,
                rawJson = """{"type":"trojan","tag":"🇭🇰 香港-01-IEPL","server":"hk01.node.com","server_port":443,"password":"secret_password","tls":{"enabled":true,"server_name":"hk01.node.com"}}""",
                enabled = true
            ),
            Node(
                id = 2,
                subscriptionId = 1,
                tag = "🇸🇬 新加坡-02-BGP",
                protocol = "vless",
                server = "sg02.node.com",
                serverPort = 443,
                rawJson = """{"type":"vless","tag":"🇸🇬 新加坡-02-BGP","server":"sg02.node.com","server_port":443,"uuid":"11111111-2222-3333-4444-555555555555","flow":"xtls-rprx-vision","tls":{"enabled":true,"server_name":"sg02.node.com","reality":{"enabled":true,"public_key":"example_pbk_12345"}},"packet_encoding":"xudp"}""",
                enabled = true
            ),
            Node(
                id = 3,
                subscriptionId = 1,
                tag = "🇯🇵 日本-03-CN2",
                protocol = "vmess",
                server = "jp03.node.com",
                serverPort = 443,
                rawJson = """{"type":"vmess","tag":"🇯🇵 日本-03-CN2","server":"jp03.node.com","server_port":443,"uuid":"22222222-3333-4444-5555-666666666666","alter_id":0,"security":"auto","tls":{"enabled":true,"server_name":"jp03.node.com"},"transport":{"type":"ws","path":"/ws","headers":{"Host":"jp03.node.com"}}}""",
                enabled = true
            )
        )

        val prettyJson = SimpleConfigGenerator.generatePrettyString(cfg, dummyNodes)
        val buildDir = java.io.File("build")
        if (!buildDir.exists()) buildDir.mkdirs()
        val targetFile = java.io.File(buildDir, "example-sing-box.json")
        targetFile.writeText(prettyJson)
        assertTrue(targetFile.exists())
    }
}

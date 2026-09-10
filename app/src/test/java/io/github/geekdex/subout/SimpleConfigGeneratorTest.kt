package io.github.geekdex.subout

import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.model.AppRulePresets
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleInboundConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        // DNS does not contain deprecated independent_cache
        val dns = json.getAsJsonObject("dns")
        assertEquals("ipv4_only", dns.get("strategy").asString)
        assertNull(dns.get("independent_cache"))

        // Top-level http_clients is configured
        val httpClients = json.getAsJsonArray("http_clients")
        assertNotNull(httpClients)
        assertTrue(httpClients.any { it.asJsonObject.get("tag").asString == "direct" })

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

        // Route contains rules, rule_sets, default_http_client, and uses http_client instead of download_detour
        val route = json.getAsJsonObject("route")
        assertEquals("direct", route.get("default_http_client").asString)
        val ruleSets = route.getAsJsonArray("rule_set")
        assertTrue(route.getAsJsonArray("rules").size() > 0)
        assertTrue(ruleSets.size() > 0)
        ruleSets.forEach { rs ->
            val obj = rs.asJsonObject
            assertNotNull("rule_set should have http_client", obj.get("http_client"))
            assertNull("rule_set should not have deprecated download_detour", obj.get("download_detour"))
        }
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
    fun testGoogleSocialAiPresetsAndQuicBlocking() {
        val cfg = SimpleConfig(route = SimpleRouteConfig(mode = "smart"))
        val dummyNodes = listOf(
            Node(
                id = 1,
                subscriptionId = 1,
                tag = "Node-01",
                protocol = "trojan",
                server = "node.com",
                serverPort = 443,
                rawJson = """{"type":"trojan","tag":"Node-01","server":"node.com","server_port":443,"password":"pwd"}""",
                enabled = true
            )
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val route = json.getAsJsonObject("route")
        val rules = route.getAsJsonArray("rules")
        val ruleSets = route.getAsJsonArray("rule_set")

        // 1. Verify QUIC (UDP 443) blocking rule exists
        val quicBlockRule = rules.firstOrNull {
            it.asJsonObject.get("port")?.asInt == 443 &&
                    it.asJsonObject.get("network")?.asString == "udp" &&
                    it.asJsonObject.get("outbound")?.asString == "block"
        }
        assertNotNull("QUIC UDP 443 block rule must exist to prevent Google Play timeouts", quicBlockRule)

        // 2. Verify Google, Social, and AI package_name rules exist
        val googlePkgRule = rules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.android.vending" }
        }
        assertNotNull("Google Play package_name rule must exist", googlePkgRule)

        val socialPkgRule = rules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.twitter.android" }
        }
        assertNotNull("Social package_name rule must exist", socialPkgRule)

        val aiPkgRule = rules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.openai.chatgpt" }
        }
        assertNotNull("AI package_name rule must exist", aiPkgRule)

        // 3. Verify rule sets for Google, Social, AI
        assertTrue("rule_set must include geosite-google", ruleSets.any { it.asJsonObject.get("tag")?.asString == "geosite-google" })
        assertTrue("rule_set must include geosite-youtube", ruleSets.any { it.asJsonObject.get("tag")?.asString == "geosite-youtube" })
        assertTrue("rule_set must include geosite-twitter", ruleSets.any { it.asJsonObject.get("tag")?.asString == "geosite-twitter" })
        assertTrue("rule_set must include geosite-openai", ruleSets.any { it.asJsonObject.get("tag")?.asString == "geosite-openai" })
        assertTrue("rule_set must include geosite-category-ai-chat-!cn", ruleSets.any { it.asJsonObject.get("tag")?.asString == "geosite-category-ai-chat-!cn" })

        // 4. Verify geosite-google and geosite-youtube rules appear BEFORE geosite-cn in route rules
        val googleRuleIndex = rules.indexOfFirst { it.asJsonObject.get("rule_set")?.asString == "geosite-google" }
        val youtubeRuleIndex = rules.indexOfFirst { it.asJsonObject.get("rule_set")?.asString == "geosite-youtube" }
        val cnRuleIndex = rules.indexOfFirst { it.asJsonObject.get("rule_set")?.asString == "geosite-cn" }
        assertTrue(googleRuleIndex != -1)
        assertTrue(youtubeRuleIndex != -1)
        assertTrue(cnRuleIndex != -1)
        assertTrue("geosite-google must precede geosite-cn in route rules", googleRuleIndex < cnRuleIndex)
        assertTrue("geosite-youtube must precede geosite-cn in route rules", youtubeRuleIndex < cnRuleIndex)
    }

    @Test
    fun testCustomOutboundAndToggling() {
        val cfg = SimpleConfig(
            route = SimpleRouteConfig(
                mode = "smart",
                block_quic = false,
                route_google = true,
                google_outbound = "HK-01",
                route_social = false,
                route_ai = true,
                ai_outbound = "US-01"
            )
        )
        val dummyNodes = listOf(
            Node(id = 1, subscriptionId = 1, tag = "HK-01", protocol = "trojan", server = "hk.com", serverPort = 443, rawJson = "{}", enabled = true),
            Node(id = 2, subscriptionId = 1, tag = "US-01", protocol = "vless", server = "us.com", serverPort = 443, rawJson = "{}", enabled = true)
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val route = json.getAsJsonObject("route")
        val rules = route.getAsJsonArray("rules")
        val ruleSets = route.getAsJsonArray("rule_set")

        // QUIC blocked is false -> no block rule
        val quicBlockRule = rules.firstOrNull {
            it.asJsonObject.get("port")?.asInt == 443 &&
                    it.asJsonObject.get("network")?.asString == "udp" &&
                    it.asJsonObject.get("outbound")?.asString == "block"
        }
        assertNull("QUIC block rule should NOT exist when block_quic is false", quicBlockRule)

        // Google outbound is HK-01
        val googlePkgRule = rules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.android.vending" }
        }
        assertNotNull(googlePkgRule)
        assertEquals("HK-01", googlePkgRule!!.asJsonObject.get("outbound").asString)

        // Social was disabled -> no twitter rules
        val socialPkgRule = rules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.twitter.android" }
        }
        assertNull("Social rules should not exist when route_social is false", socialPkgRule)

        // AI outbound is US-01
        val aiPkgRule = rules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.openai.chatgpt" }
        }
        assertNotNull(aiPkgRule)
        assertEquals("US-01", aiPkgRule!!.asJsonObject.get("outbound").asString)
    }

    @Test
    fun testCustomAppGroupsRouting() {
        val groupDirect = io.github.geekdex.subout.domain.model.CustomAppGroup(
            name = "直连应用组",
            outbound = "direct",
            package_names = listOf("com.tencent.mm", "com.eg.android.AlipayGphone"),
            enabled = true
        )
        val groupNode = io.github.geekdex.subout.domain.model.CustomAppGroup(
            name = "香港专线",
            outbound = "HK-01",
            package_names = listOf("com.netflix.mediaclient"),
            enabled = true
        )
        val groupDisabled = io.github.geekdex.subout.domain.model.CustomAppGroup(
            name = "禁用分组",
            outbound = "direct",
            package_names = listOf("com.example.unused"),
            enabled = false
        )

        val cfg = SimpleConfig(
            route = SimpleRouteConfig(
                mode = "smart",
                custom_groups = listOf(groupDirect, groupNode, groupDisabled)
            )
        )

        val dummyNodes = listOf(
            Node(1, 1, "HK-01", "shadowsocks", "1.1.1.1", 443, "{}", true),
            Node(2, 1, "US-01", "shadowsocks", "2.2.2.2", 443, "{}", true)
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val routeRules = json.getAsJsonObject("route").getAsJsonArray("rules")

        // 1. Direct group
        val directRule = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.tencent.mm" }
        }
        assertNotNull(directRule)
        assertEquals("direct", directRule!!.asJsonObject.get("outbound").asString)

        // 2. HK-01 group
        val hkRule = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.netflix.mediaclient" }
        }
        assertNotNull(hkRule)
        assertEquals("HK-01", hkRule!!.asJsonObject.get("outbound").asString)

        // 3. Disabled group
        val disabledRule = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.example.unused" }
        }
        assertNull("Disabled custom group should not produce rules", disabledRule)

        // 4. DNS rules check
        val dnsRules = json.getAsJsonObject("dns").getAsJsonArray("rules")
        val directDns = dnsRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.tencent.mm" }
        }
        assertNotNull(directDns)
        assertEquals("dns_local", directDns!!.asJsonObject.get("server").asString)

        val hkDns = dnsRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.netflix.mediaclient" }
        }
        assertNotNull(hkDns)
        assertEquals("dns_fakeip", hkDns!!.asJsonObject.get("server").asString)
    }

    @Test
    fun testAiCustomOutboundAndDnsSyncInGfwMode() {
        val targetNodeTag = "美国洛杉矶[CM]"
        val cfg = SimpleConfig(
            route = SimpleRouteConfig(
                mode = "gfw",
                route_ai = true,
                ai_outbound = targetNodeTag
            )
        )
        val dummyNodes = listOf(
            Node(1, 1, targetNodeTag, "vless", "us.com", 443, "{}", true),
            Node(2, 1, "HK-01", "trojan", "hk.com", 443, "{}", true)
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val route = json.getAsJsonObject("route")
        val routeRules = route.getAsJsonArray("rules")
        val dns = json.getAsJsonObject("dns")
        val dnsRules = dns.getAsJsonArray("rules")

        // 1. In GFW mode, AI package rule must route to custom node
        val aiPkgRoute = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.openai.chatgpt" }
        }
        assertNotNull("AI package route rule must exist in GFW mode", aiPkgRoute)
        assertEquals(targetNodeTag, aiPkgRoute!!.asJsonObject.get("outbound").asString)

        // 2. AI geosite rule sets must route to custom node
        val aiRuleSetRoute = routeRules.firstOrNull {
            it.asJsonObject.get("rule_set")?.asString == "geosite-openai"
        }
        assertNotNull("geosite-openai route rule must exist in GFW mode", aiRuleSetRoute)
        assertEquals(targetNodeTag, aiRuleSetRoute!!.asJsonObject.get("outbound").asString)

        // 3. DNS rules must be synchronously configured to fakeip/remote DNS
        val aiPkgDns = dnsRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.openai.chatgpt" }
        }
        assertNotNull("AI package DNS rule must exist", aiPkgDns)
        assertEquals("dns_fakeip", aiPkgDns!!.asJsonObject.get("server").asString)

        val aiRuleSetDns = dnsRules.firstOrNull {
            it.asJsonObject.get("rule_set")?.asString == "geosite-openai"
        }
        assertNotNull("geosite-openai DNS rule must exist", aiRuleSetDns)
        assertEquals("dns_fakeip", aiRuleSetDns!!.asJsonObject.get("server").asString)
    }

    @Test
    fun testPresetDirectAndBlockOutbounds() {
        val cfg = SimpleConfig(
            route = SimpleRouteConfig(
                mode = "smart",
                route_google = true,
                google_outbound = "direct",
                route_social = true,
                social_outbound = "block"
            )
        )
        val dummyNodes = listOf(
            Node(1, 1, "HK-01", "trojan", "hk.com", 443, "{}", true)
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val routeRules = json.getAsJsonObject("route").getAsJsonArray("rules")
        val dnsRules = json.getAsJsonObject("dns").getAsJsonArray("rules")

        // Google -> direct
        val googlePkgRoute = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.android.vending" }
        }
        assertNotNull(googlePkgRoute)
        assertEquals("direct", googlePkgRoute!!.asJsonObject.get("outbound").asString)

        val googleDns = dnsRules.firstOrNull {
            it.asJsonObject.get("rule_set")?.asString == "geosite-google"
        }
        assertNotNull(googleDns)
        assertEquals("dns_local", googleDns!!.asJsonObject.get("server").asString)

        // Social -> block
        val socialPkgRoute = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.twitter.android" }
        }
        assertNotNull(socialPkgRoute)
        assertEquals("block", socialPkgRoute!!.asJsonObject.get("outbound").asString)

        val socialDns = dnsRules.firstOrNull {
            it.asJsonObject.get("rule_set")?.asString == "geosite-twitter"
        }
        assertNotNull(socialDns)
        assertEquals("dns_local", socialDns!!.asJsonObject.get("server").asString)
    }

    @Test
    fun testCustomOutboundFallbackWhenNodeDisabledOrMissing() {
        val cfg = SimpleConfig(
            route = SimpleRouteConfig(
                mode = "smart",
                route_ai = true,
                ai_outbound = "Non-Existent-Node"
            )
        )
        val dummyNodes = listOf(
            Node(1, 1, "HK-01", "trojan", "hk.com", 443, "{}", true)
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val routeRules = json.getAsJsonObject("route").getAsJsonArray("rules")

        val aiPkgRoute = routeRules.firstOrNull {
            val pkgs = it.asJsonObject.getAsJsonArray("package_name")
            pkgs != null && pkgs.any { p -> p.asString == "com.openai.chatgpt" }
        }
        assertNotNull(aiPkgRoute)
        // Fallback to targetProxy ("AUTO-Test" when nodes are present)
        assertEquals("AUTO-Test", aiPkgRoute!!.asJsonObject.get("outbound").asString)
    }

    @Test
    fun testCustomDomainGroupsGeneration() {
        val cfg = SimpleConfig(
            dns = SimpleDnsConfig(mode = "preset_fakeip"),
            route = SimpleRouteConfig(
                mode = "smart",
                custom_domain_groups = listOf(
                    CustomDomainGroup(
                        id = "domain-g1",
                        name = "海外开发者",
                        outbound = "direct",
                        domain_suffixes = listOf("github.com", "githubusercontent.com"),
                        enabled = true
                    ),
                    CustomDomainGroup(
                        id = "domain-g2",
                        name = "游戏与加速",
                        outbound = "🇸🇬 新加坡-02-BGP",
                        domain_suffixes = listOf("steamcommunity.com", "epicgames.com"),
                        enabled = true
                    )
                )
            )
        )
        val dummyNodes = listOf(
            Node(1, 1, "🇭🇰 香港-01-IEPL", "trojan", "hk.com", 443, "{}", true),
            Node(2, 1, "🇸🇬 新加坡-02-BGP", "vless", "sg.com", 443, "{}", true)
        )

        val json = SimpleConfigGenerator.generate(cfg, dummyNodes)
        val dnsRules = json.getAsJsonObject("dns").getAsJsonArray("rules")
        val routeRules = json.getAsJsonObject("route").getAsJsonArray("rules")

        // 1. Group 1: direct -> route is direct, DNS is dns_local
        val g1Route = routeRules.firstOrNull {
            val suffixes = it.asJsonObject.getAsJsonArray("domain_suffix")
            suffixes != null && suffixes.any { s -> s.asString == "github.com" }
        }
        assertNotNull("Group 1 domain_suffix route rule must exist", g1Route)
        assertEquals("direct", g1Route!!.asJsonObject.get("outbound").asString)

        val g1Dns = dnsRules.firstOrNull {
            val suffixes = it.asJsonObject.getAsJsonArray("domain_suffix")
            suffixes != null && suffixes.any { s -> s.asString == "github.com" }
        }
        assertNotNull("Group 1 domain_suffix DNS rule must exist", g1Dns)
        assertEquals("dns_local", g1Dns!!.asJsonObject.get("server").asString)

        // 2. Group 2: specific proxy node -> route is 🇸🇬 新加坡-02-BGP, DNS is dns_fakeip
        val g2Route = routeRules.firstOrNull {
            val suffixes = it.asJsonObject.getAsJsonArray("domain_suffix")
            suffixes != null && suffixes.any { s -> s.asString == "steamcommunity.com" }
        }
        assertNotNull("Group 2 domain_suffix route rule must exist", g2Route)
        assertEquals("🇸🇬 新加坡-02-BGP", g2Route!!.asJsonObject.get("outbound").asString)

        val g2Dns = dnsRules.firstOrNull {
            val suffixes = it.asJsonObject.getAsJsonArray("domain_suffix")
            suffixes != null && suffixes.any { s -> s.asString == "steamcommunity.com" }
        }
        assertNotNull("Group 2 domain_suffix DNS rule must exist", g2Dns)
        assertEquals("dns_fakeip", g2Dns!!.asJsonObject.get("server").asString)
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
                rawJson = """{"type":"vless","tag":"🇸🇬 新加坡-02-BGP","server":"sg02.node.com","server_port":443,"uuid":"11111111-2222-3333-4444-555555555555","flow":"xtls-rprx-vision","tls":{"enabled":true,"server_name":"sg02.node.com","reality":{"enabled":true,"public_key":"MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA"},"utls":{"enabled":true,"fingerprint":"chrome"}},"packet_encoding":"xudp"}""",
                enabled = true
            )
        )

        val prettyJson = SimpleConfigGenerator.generatePrettyString(cfg, dummyNodes)
        val buildDir = java.io.File("build")
        if (!buildDir.exists()) buildDir.mkdirs()
        val targetFile = java.io.File(buildDir, "example-sing-box.json")
        targetFile.writeText(prettyJson)
        assertTrue(targetFile.exists())

        // Also update root example-sing-box.json for documentation
        val rootExample = listOf(java.io.File("example-sing-box.json"), java.io.File("../example-sing-box.json")).firstOrNull { it.exists() }
        rootExample?.writeText(prettyJson)
    }
}

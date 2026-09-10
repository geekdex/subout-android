package io.github.geekdex.subout

import com.google.gson.JsonParser
import io.github.geekdex.subout.domain.model.CustomAppGroup
import io.github.geekdex.subout.domain.model.ManualHysteria2Config
import io.github.geekdex.subout.domain.model.ManualNodeType
import io.github.geekdex.subout.domain.model.ManualShadowsocksConfig
import io.github.geekdex.subout.domain.model.ManualTrojanConfig
import io.github.geekdex.subout.domain.model.ManualVlessConfig
import io.github.geekdex.subout.domain.model.ManualVmessConfig
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualNodeTest {

    @Test
    fun testManualVlessConfigMatchesUserSpec() {
        val vless = ManualVlessConfig(
            tag = "us_self",
            server = "170.106.76.11",
            serverPort = 443,
            uuid = "c04d6b79-0803-4cf1-8d76-3cc759ef79a0",
            flow = "xtls-rprx-vision",
            tlsEnabled = true,
            serverName = "www.bing.com",
            utlsEnabled = true,
            utlsFingerprint = "chrome",
            realityEnabled = true,
            realityPublicKey = "hDeF0usWcAOgKqZiygeQG8yPQGurjEhXXRNewBVkuy8",
            realityShortId = "b803b45ccaced487"
        )

        val json = vless.toJson()
        assertEquals("vless", json.get("type").asString)
        assertEquals("us_self", json.get("tag").asString)
        assertEquals("170.106.76.11", json.get("server").asString)
        assertEquals(443, json.get("server_port").asInt)
        assertEquals("c04d6b79-0803-4cf1-8d76-3cc759ef79a0", json.get("uuid").asString)
        assertEquals("xtls-rprx-vision", json.get("flow").asString)

        val tls = json.getAsJsonObject("tls")
        assertNotNull(tls)
        assertTrue(tls.get("enabled").asBoolean)
        assertEquals("www.bing.com", tls.get("server_name").asString)

        val utls = tls.getAsJsonObject("utls")
        assertNotNull(utls)
        assertTrue(utls.get("enabled").asBoolean)
        assertEquals("chrome", utls.get("fingerprint").asString)

        val reality = tls.getAsJsonObject("reality")
        assertNotNull(reality)
        assertTrue(reality.get("enabled").asBoolean)
        assertEquals("hDeF0usWcAOgKqZiygeQG8yPQGurjEhXXRNewBVkuy8", reality.get("public_key").asString)
        assertEquals("b803b45ccaced487", reality.get("short_id").asString)

        // Verify conversion to database entity Node
        val node = vless.toNode()
        assertNull(node.subscriptionId)
        assertEquals("us_self", node.tag)
        assertEquals("vless", node.protocol)
        assertEquals("170.106.76.11", node.server)
        assertEquals(443, node.serverPort)
        assertTrue(node.enabled)

        // RawJson can be re-parsed as identical JsonObject
        val parsedJson = JsonParser.parseString(node.rawJson).asJsonObject
        assertEquals("vless", parsedJson.get("type").asString)
        assertEquals("us_self", parsedJson.get("tag").asString)
    }

    @Test
    fun testExtensibleManualNodeTypes() {
        val vmess = ManualVmessConfig("vmess-01", "vm.example.com", 443, "uuid-123")
        assertEquals(ManualNodeType.VMESS, vmess.type)
        assertEquals("vmess", vmess.toJson().get("type").asString)

        val trojan = ManualTrojanConfig("trojan-01", "tr.example.com", 443, "pwd-123")
        assertEquals(ManualNodeType.TROJAN, trojan.type)
        assertEquals("trojan", trojan.toJson().get("type").asString)

        val ss = ManualShadowsocksConfig("ss-01", "ss.example.com", 8388, "2022-blake3-aes-128-gcm", "key")
        assertEquals(ManualNodeType.SHADOWSOCKS, ss.type)
        assertEquals("shadowsocks", ss.toJson().get("type").asString)

        val hy2 = ManualHysteria2Config("hy2-01", "hy2.example.com", 443, "pwd")
        assertEquals(ManualNodeType.HYSTERIA2, hy2.type)
        assertEquals("hysteria2", hy2.toJson().get("type").asString)
    }

    @Test
    fun testNodeUsageDetectionAndReset() {
        val targetNodeTag = "us_self"
        val otherNodeTag = "hk_other"

        val initialConfig = SimpleConfig(
            route = SimpleRouteConfig(
                ai_outbound = targetNodeTag,
                google_outbound = otherNodeTag,
                custom_groups = listOf(
                    CustomAppGroup(name = "专线分组", outbound = targetNodeTag)
                )
            )
        )

        // When targetNodeTag is deleted:
        val tagsToDelete = setOf(targetNodeTag)
        val route = initialConfig.route

        val resetList = mutableListOf<String>()
        var newAi = route.ai_outbound
        if (route.ai_outbound in tagsToDelete) {
            newAi = "direct"
            resetList.add("热门 AI 应用出站")
        }

        var newGoogle = route.google_outbound
        if (route.google_outbound in tagsToDelete) {
            newGoogle = "direct"
            resetList.add("Google 全家桶出站")
        }

        val newGroups = route.customGroups.map { group ->
            if (group.outbound in tagsToDelete) {
                resetList.add("应用分组「${group.name}」")
                group.copy(outbound = "direct")
            } else {
                group
            }
        }

        // Verify AI and custom group are reset to "direct", but Google is unchanged
        assertEquals("direct", newAi)
        assertEquals(otherNodeTag, newGoogle)
        assertEquals("direct", newGroups[0].outbound)
        assertEquals(2, resetList.size)
        assertTrue(resetList.contains("热门 AI 应用出站"))
        assertTrue(resetList.contains("应用分组「专线分组」"))
    }

    @Test
    fun testValidateAndCleanMissingNodesLogic() {
        val standardOutbounds = setOf("", "AUTO-Test", "proxy", "direct", "block")
        val availableTags = setOf("node_survived", "node_new")
        val initialConfig = SimpleConfig(
            route = SimpleRouteConfig(
                ai_outbound = "stale_sub_node",
                google_outbound = "AUTO-Test",
                social_outbound = "direct",
                default_outbound = "node_survived",
                custom_groups = listOf(
                    CustomAppGroup(name = "媒体组", outbound = "stale_sub_node")
                )
            )
        )

        val route = initialConfig.route
        val resetList = mutableListOf<String>()

        var newAi = route.ai_outbound
        if (route.ai_outbound !in standardOutbounds && route.ai_outbound !in availableTags) {
            newAi = "direct"
            resetList.add("热门 AI 应用出站")
        }

        var newDefault = route.default_outbound
        if (route.default_outbound !in standardOutbounds && route.default_outbound !in availableTags) {
            newDefault = "direct"
            resetList.add("默认出站")
        }

        val newGroups = route.customGroups.map { group ->
            if (group.outbound !in standardOutbounds && group.outbound !in availableTags) {
                resetList.add("应用分组「${group.name}」")
                group.copy(outbound = "direct")
            } else {
                group
            }
        }

        assertEquals("direct", newAi)
        assertEquals("node_survived", newDefault)
        assertEquals("direct", newGroups[0].outbound)
        assertEquals(2, resetList.size)
        assertTrue(resetList.contains("热门 AI 应用出站"))
        assertTrue(resetList.contains("应用分组「媒体组」"))
    }
}

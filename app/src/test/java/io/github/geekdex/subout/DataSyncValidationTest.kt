package io.github.geekdex.subout

import io.github.geekdex.subout.domain.model.CustomAppGroup
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import io.github.geekdex.subout.domain.model.validateAndCleanOutbounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSyncValidationTest {

    @Test
    fun testPreservesMatchingNamesAndResetsMissingNamesToDirect() {
        val originalConfig = SimpleConfig(
            route = SimpleRouteConfig(
                default_outbound = "Old-Default-Node",
                google_outbound = "HK-Shared-Node",       // 同名项保留
                social_outbound = "US-Deleted-Node",     // 无同名项 -> direct
                ai_outbound = "Dynamic-Group-AI",        // 有效分组名保留
                custom_groups = listOf(
                    CustomAppGroup(id = "1", name = "Telegram", outbound = "TG-Dedicated-Node"), // 无同名项 -> direct
                    CustomAppGroup(id = "2", name = "Discord", outbound = "HK-Shared-Node")       // 同名项保留
                ),
                custom_domain_groups = listOf(
                    CustomDomainGroup(id = "1", name = "Github", outbound = "Github-Node"),      // 无同名项 -> direct
                    CustomDomainGroup(id = "2", name = "Notion", outbound = "Dynamic-Group-AI")   // 有效分组保留
                )
            )
        )

        val newAvailableNodeTags = setOf(
            "HK-Shared-Node",
            "SG-New-Node",
            "JP-New-Node"
        )
        val validGroupNames = setOf(
            "Dynamic-Group-AI"
        )

        val (cleanedConfig, resetList) = originalConfig.validateAndCleanOutbounds(
            availableNodeTags = newAvailableNodeTags,
            availableGroupNames = validGroupNames
        )

        // 1. 同名项保留验证
        assertEquals("HK-Shared-Node", cleanedConfig.route.google_outbound)
        assertEquals("Dynamic-Group-AI", cleanedConfig.route.ai_outbound)
        assertEquals("HK-Shared-Node", cleanedConfig.route.customGroups.first { it.id == "2" }.outbound)
        assertEquals("Dynamic-Group-AI", cleanedConfig.route.customDomainGroups.first { it.id == "2" }.outbound)

        // 2. 无同名项自动重置为 direct 验证
        assertEquals("direct", cleanedConfig.route.default_outbound)
        assertEquals("direct", cleanedConfig.route.social_outbound)
        assertEquals("direct", cleanedConfig.route.customGroups.first { it.id == "1" }.outbound)
        assertEquals("direct", cleanedConfig.route.customDomainGroups.first { it.id == "1" }.outbound)

        // 3. 重置提示列表验证
        assertTrue(resetList.contains("默认出站"))
        assertTrue(resetList.contains("常用海外社交出站"))
        assertTrue(resetList.contains("应用分组「Telegram」"))
        assertTrue(resetList.contains("域名分组「Github」"))
        assertEquals(4, resetList.size)
    }

    @Test
    fun testSystemOutboundsAndEmptyTagsArePreserved() {
        val originalConfig = SimpleConfig(
            route = SimpleRouteConfig(
                default_outbound = "AUTO-Test",
                google_outbound = "",
                social_outbound = "proxy",
                ai_outbound = "direct"
            )
        )

        val (cleanedConfig, resetList) = originalConfig.validateAndCleanOutbounds(
            availableNodeTags = emptySet(),
            availableGroupNames = emptySet()
        )

        assertEquals("AUTO-Test", cleanedConfig.route.default_outbound)
        assertEquals("", cleanedConfig.route.google_outbound)
        assertEquals("proxy", cleanedConfig.route.social_outbound)
        assertEquals("direct", cleanedConfig.route.ai_outbound)
        assertTrue(resetList.isEmpty())
    }

    @Test
    fun testAllNodesReplacedOnSubscriptionSync() {
        // 模拟订阅A全量替换：原节点全被冲掉，新数据替换
        val oldNodeTag = "Old-SubscriptionA-Node"
        val newNodeTag = "New-SubscriptionA-Node"

        val originalConfig = SimpleConfig(
            route = SimpleRouteConfig(
                google_outbound = oldNodeTag
            )
        )

        // 旧节点被清空，仅有新节点
        val (cleanedConfig, resetList) = originalConfig.validateAndCleanOutbounds(
            availableNodeTags = setOf(newNodeTag),
            availableGroupNames = emptySet()
        )

        assertEquals("direct", cleanedConfig.route.google_outbound)
        assertEquals(listOf("Google 全家桶出站"), resetList)
    }
}

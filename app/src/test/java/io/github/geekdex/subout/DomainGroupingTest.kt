package io.github.geekdex.subout

import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.model.AppRulePresets
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.DomainUtils
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainGroupingTest {

    @Test
    fun testSubdomainConflictLogic() {
        // Parent domain covers child domain
        assertTrue(DomainUtils.isSubdomain("api.github.com", "github.com"))
        assertTrue(DomainUtils.isSubdomain("sub.api.github.com", "github.com"))
        assertTrue(DomainUtils.isSubdomain("github.com", "github.com"))

        // Unrelated domains
        assertFalse(DomainUtils.isSubdomain("mygithub.com", "github.com"))
        assertFalse(DomainUtils.isSubdomain("github.com.cn", "github.com"))
        assertFalse(DomainUtils.isSubdomain("github.com", "api.github.com"))
    }

    @Test
    fun testPresetDomainOverlapDetection() {
        val googlePresetDomains = AppRulePresets.google.domainSuffixes.map { DomainUtils.normalize(it) }
        assertTrue(googlePresetDomains.contains("google.com"))
        assertTrue(googlePresetDomains.contains("youtube.com"))

        // Candidate domain: google.com or play.google.com should be detected as belonging to Google preset
        val candidate1 = "play.google.com"
        val isCoveredByGoogle = googlePresetDomains.any { DomainUtils.isSubdomain(candidate1, it) }
        assertTrue("play.google.com should be covered by google.com preset", isCoveredByGoogle)

        val candidate2 = "github.com"
        val isCoveredByGoogle2 = googlePresetDomains.any { DomainUtils.isSubdomain(candidate2, it) }
        assertFalse("github.com should not be covered by google preset", isCoveredByGoogle2)
    }

    @Test
    fun testDomainRuleOrderInDnsAndRoute() {
        val domainGroup1 = CustomDomainGroup(
            id = "g1",
            name = "开发工具",
            outbound = "direct",
            domain_suffixes = listOf("github.com", "gitlab.com"),
            enabled = true
        )
        val domainGroup2 = CustomDomainGroup(
            id = "g2",
            name = "特殊代理",
            outbound = "🇭🇰 香港-01-IEPL",
            domain_suffixes = listOf("anthropic.com", "custom-service.io"),
            enabled = true
        )

        val config = SimpleConfig(
            dns = SimpleDnsConfig(mode = "preset_fakeip"),
            route = SimpleRouteConfig(
                mode = "smart",
                custom_domain_groups = listOf(domainGroup1, domainGroup2)
            )
        )

        val nodes = listOf(
            Node(1, 1, "🇭🇰 香港-01-IEPL", "trojan", "hk.com", 443, "{}", true)
        )

        val json = SimpleConfigGenerator.generate(config, nodes)
        val routeRules = json.getAsJsonObject("route").getAsJsonArray("rules")
        val dnsRules = json.getAsJsonObject("dns").getAsJsonArray("rules")

        // Check route rules for g1
        val g1Route = routeRules.firstOrNull {
            val arr = it.asJsonObject.getAsJsonArray("domain_suffix")
            arr != null && arr.any { s -> s.asString == "github.com" }
        }
        assertNotNull(g1Route)
        assertEquals("direct", g1Route!!.asJsonObject.get("outbound").asString)

        // Check DNS rules for g1 (direct -> dns_local)
        val g1Dns = dnsRules.firstOrNull {
            val arr = it.asJsonObject.getAsJsonArray("domain_suffix")
            arr != null && arr.any { s -> s.asString == "github.com" }
        }
        assertNotNull(g1Dns)
        assertEquals("dns_local", g1Dns!!.asJsonObject.get("server").asString)

        // Check route rules for g2 (node -> 🇭🇰 香港-01-IEPL)
        val g2Route = routeRules.firstOrNull {
            val arr = it.asJsonObject.getAsJsonArray("domain_suffix")
            arr != null && arr.any { s -> s.asString == "anthropic.com" }
        }
        assertNotNull(g2Route)
        assertEquals("🇭🇰 香港-01-IEPL", g2Route!!.asJsonObject.get("outbound").asString)

        // Check DNS rules for g2 (fakeip mode -> dns_fakeip)
        val g2Dns = dnsRules.firstOrNull {
            val arr = it.asJsonObject.getAsJsonArray("domain_suffix")
            arr != null && arr.any { s -> s.asString == "anthropic.com" }
        }
        assertNotNull(g2Dns)
        assertEquals("dns_fakeip", g2Dns!!.asJsonObject.get("server").asString)
    }
}

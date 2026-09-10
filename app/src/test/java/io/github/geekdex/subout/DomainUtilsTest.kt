package io.github.geekdex.subout

import io.github.geekdex.subout.domain.model.DomainUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUtilsTest {

    @Test
    fun testNormalize() {
        // Basic normalization
        assertEquals("google.com", DomainUtils.normalize("google.com"))
        assertEquals("google.com", DomainUtils.normalize("  GOOGLE.COM  "))
        assertEquals("google.com", DomainUtils.normalize(".google.com"))
        assertEquals("google.com", DomainUtils.normalize("*.google.com"))
        assertEquals("google.com", DomainUtils.normalize("...google.com."))

        // URL parsing and stripping
        assertEquals("openai.com", DomainUtils.normalize("https://openai.com/v1/chat/completions"))
        assertEquals("api.github.com", DomainUtils.normalize("http://api.github.com:443/repos/octocat?tab=stars#readme"))
        assertEquals("github.com", DomainUtils.normalize("https://github.com/"))
    }

    @Test
    fun testIsValidDomain() {
        assertTrue(DomainUtils.isValidDomain("google.com"))
        assertTrue(DomainUtils.isValidDomain("api.openai.com"))
        assertTrue(DomainUtils.isValidDomain("sub.domain.co.uk"))
        assertTrue(DomainUtils.isValidDomain("my-site-123.org"))

        assertFalse(DomainUtils.isValidDomain(""))
        assertFalse(DomainUtils.isValidDomain("   "))
        assertFalse(DomainUtils.isValidDomain("localhost"))
        assertFalse(DomainUtils.isValidDomain("invalid domain.com"))
        assertFalse(DomainUtils.isValidDomain("http://google.com")) // Raw URL before normalization is not a clean domain
        assertFalse(DomainUtils.isValidDomain("google..com"))
        assertFalse(DomainUtils.isValidDomain("-google.com"))
    }

    @Test
    fun testIsSubdomain() {
        // Same domain
        assertTrue(DomainUtils.isSubdomain("google.com", "google.com"))
        assertTrue(DomainUtils.isSubdomain(".google.com", "google.com"))

        // True child
        assertTrue(DomainUtils.isSubdomain("mail.google.com", "google.com"))
        assertTrue(DomainUtils.isSubdomain("sub.api.github.com", "github.com"))
        assertTrue(DomainUtils.isSubdomain("sub.api.github.com", "api.github.com"))

        // False relationships
        assertFalse(DomainUtils.isSubdomain("google.com", "mail.google.com"))
        assertFalse(DomainUtils.isSubdomain("mygoogle.com", "google.com"))
        assertFalse(DomainUtils.isSubdomain("google.com.cn", "google.com"))
        assertFalse(DomainUtils.isSubdomain("github.com", "gitlab.com"))
    }

    @Test
    fun testParseDomains() {
        val rawInput = """
            google.com
            https://api.github.com/v1
            *.openai.com,   claude.ai;
            notion.so   steamcommunity.com
            google.com
            INVALID DOMAIN
        """.trimIndent()

        val parsed = DomainUtils.parseDomains(rawInput)
        assertEquals(
            listOf("google.com", "api.github.com", "openai.com", "claude.ai", "notion.so", "steamcommunity.com"),
            parsed
        )
    }
}

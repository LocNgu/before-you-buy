package io.github.locngu.beforeyoubuy.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `no tag gives the dev version`() {
        assertEquals(AppVersion.DEV, AppVersion.fromTag(null))
        assertEquals(AppVersion.DEV, AppVersion.fromTag(""))
    }

    @Test
    fun `a tag gives the name without v and the code MAJOR 10000 + MINOR 100 + PATCH`() {
        assertEquals(AppVersion("0.1.0", 100), AppVersion.fromTag("v0.1.0"))
        assertEquals(AppVersion("1.2.3", 10_203), AppVersion.fromTag("v1.2.3"))
        assertEquals(AppVersion("12.99.99", 129_999), AppVersion.fromTag("v12.99.99"))
    }

    @Test
    fun `codes only increase from one release to the next`() {
        val tags = listOf("v0.0.2", "v0.1.0", "v0.1.1", "v0.1.99", "v0.2.0", "v0.99.99", "v1.0.0", "v1.0.1", "v2.0.0")
        val codes = tags.map { AppVersion.fromTag(it).code }

        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it > AppVersion.DEV.code })
    }

    @Test
    fun `the largest major still fits Google Play's versionCode limit`() {
        assertEquals(2_100_000_000, AppVersion.fromTag("v210000.0.0").code)
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("v210000.0.1") }
    }

    @Test
    fun `tags that aren't vX_Y_Z are rejected`() {
        listOf(
            "0.1.0",
            "v0.1",
            "v1.2.3.4",
            "v1.2.3-rc.1",
            "v1.100.0",
            "v1.0.100",
            "v01.2.3",
            "v1.02.3",
            "V1.2.3",
            "v0.0.0",
            "v0.0.1",
            "vfoo"
        ).forEach { tag ->
            assertThrows(tag, IllegalArgumentException::class.java) { AppVersion.fromTag(tag) }
        }
    }
}

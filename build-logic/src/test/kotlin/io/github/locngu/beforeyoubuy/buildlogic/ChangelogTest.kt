package io.github.locngu.beforeyoubuy.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogTest {
    private val changelog =
        """
        # Changelog

        All notable user-visible changes.

        ## [Unreleased]

        ### Added

        - Something new.

        ## [0.2.0] - 2026-11-01

        ### Changed

        - A change.

        ## [0.1.0] - 2026-10-12

        ### Added

        - The first release.

        [Unreleased]: https://example.com/compare/v0.2.0...HEAD
        [0.2.0]: https://example.com/compare/v0.1.0...v0.2.0
        """.trimIndent()

    @Test
    fun `a section runs to the next version heading`() {
        assertEquals("### Changed\n\n- A change.", Changelog.section(changelog, "0.2.0"))
    }

    @Test
    fun `the last section stops at the link definitions`() {
        assertEquals("### Added\n\n- The first release.", Changelog.section(changelog, "0.1.0"))
    }

    @Test
    fun `a heading without a date is found`() {
        assertEquals("- Fixed.", Changelog.section("## [1.0.0]\n\n- Fixed.\n", "1.0.0"))
    }

    @Test
    fun `Windows line endings are handled`() {
        assertEquals("- Fixed.", Changelog.section("## [1.0.0] - 2026-10-12\r\n\r\n- Fixed.\r\n", "1.0.0"))
    }

    @Test
    fun `a missing version gives null`() {
        assertNull(Changelog.section(changelog, "0.3.0"))
    }

    @Test
    fun `a similar version doesn't match`() {
        assertNull(Changelog.section(changelog, "0.1"))
        assertNull(Changelog.section(changelog, "0x2.0"))
    }

    @Test
    fun `an empty section gives null`() {
        assertNull(Changelog.section("## [1.0.0] - 2026-10-12\n\n## [0.9.0]\n\n- Old.\n", "1.0.0"))
    }
}

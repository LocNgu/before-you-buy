package io.github.locngu.beforeyoubuy.core.domain.item

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemImagesTest {
    private val cutoff = Instant.parse("2026-10-19T10:00:00Z")
    private val old = cutoff.minusSeconds(60)
    private val recent = cutoff.plusSeconds(60)

    @Test
    fun `old photos no item names are deleted`() {
        val stored = mapOf("kept.jpg" to old, "orphan.jpg" to old)

        assertEquals(setOf("orphan.jpg"), unreferencedImages(stored, setOf("kept.jpg"), cutoff))
    }

    @Test
    fun `a photo written after the cutoff survives, it may belong to an unsaved edit`() {
        assertEquals(emptySet<String>(), unreferencedImages(mapOf("new.jpg" to recent), emptySet(), cutoff))
    }

    @Test
    fun `a referenced photo is kept however old`() {
        assertEquals(emptySet<String>(), unreferencedImages(mapOf("a.jpg" to Instant.EPOCH), setOf("a.jpg"), cutoff))
    }
}

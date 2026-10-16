package io.github.locngu.beforeyoubuy.core.data.item

import io.github.locngu.beforeyoubuy.core.domain.item.DecisionSource
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `item round trips with every field set`() {
        assertEquals(FULL_ITEM, FULL_ITEM.toEntity().toDomain())
    }

    @Test
    fun `item round trips with only the required fields`() {
        val item = newItem().copy(id = ItemId(7))

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun `item stores money as minor units and currency, instants as epoch millis`() {
        val entity = FULL_ITEM.toEntity()

        assertEquals(89_900L, entity.priceMinor)
        assertEquals("EUR", entity.currency)
        assertEquals(ADDED.toEpochMilli(), entity.addedAt)
        assertEquals("2026-12-24", entity.targetDate)
        assertEquals("NEED", entity.kind)
        assertEquals("ACTIVE", entity.status)
    }

    @Test
    fun `instants keep millisecond precision only`() {
        val item = newItem().copy(addedAt = Instant.parse("2026-10-01T10:00:00.123456789Z"))

        assertEquals(Instant.parse("2026-10-01T10:00:00.123Z"), item.toEntity().toDomain().addedAt)
    }

    @Test
    fun `unknown item enums and an unreadable date fall back`() {
        val entity = FULL_ITEM.toEntity().copy(kind = "GIFT", status = "ARCHIVED", targetDate = "next spring")

        val item = entity.toDomain()

        assertEquals(ItemKind.WANT, item.kind)
        assertEquals(ItemStatus.ACTIVE, item.status)
        assertNull(item.targetDate)
    }

    @Test
    fun `reflection round trips with every answer`() {
        assertEquals(FULL_REFLECTION, FULL_REFLECTION.toEntity(ItemId(3)).toDomain())
    }

    @Test
    fun `reflection round trips with no answers`() {
        assertEquals(Reflection(), Reflection().toEntity(ItemId(3)).toDomain())
    }

    @Test
    fun `reasons against are a JSON array`() {
        val entity = Reflection(reasonsAgainst = listOf("Price", "Space")).toEntity(ItemId(3))

        assertEquals(3L, entity.itemId)
        assertEquals("""["Price","Space"]""", entity.reasonsAgainst)
    }

    @Test
    fun `unreadable reflection values read as not answered`() {
        val entity = FULL_REFLECTION.toEntity(ItemId(3))
            .copy(usageFrequency = "HOURLY", upgradeOrNew = "SIDEGRADE", reasonsAgainst = "not json")

        val reflection = entity.toDomain()

        assertNull(reflection.usageFrequency)
        assertNull(reflection.upgradeOrNew)
        assertEquals(emptyList<String>(), reflection.reasonsAgainst)
    }

    @Test
    fun `every decision type round trips`() {
        EVERY_DECISION.forEach { decision ->
            val entity = decision.toEntity(ItemId(5))

            assertEquals(5L, entity.itemId)
            assertEquals(decision, entity.toDomain())
        }
    }

    @Test
    fun `a decision of an unknown type is skipped, an unknown source reads as the app`() {
        assertNull(reject().toEntity(ItemId(5)).copy(type = "GIFT").toDomain())
        assertEquals(DecisionSource.APP, reject().toEntity(ItemId(5)).copy(source = "WIDGET").toDomain()?.source)
    }
}

package io.github.locngu.beforeyoubuy

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.FakeItemImageStore
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCleanupTest {
    @Test
    fun `keeps every item's photo and anything written in the last hour`() = runTest {
        val now = Instant.parse("2026-10-19T10:00:00Z")
        val items = FakeItemRepository()
        val images = FakeItemImageStore()
        val item = Item(ItemId.NEW, "Camera", Money(12_000, "EUR"), now, now)
        items.add(item.copy(imageRef = "a.jpg"))
        items.add(item)

        ImageCleanup(items, images, MutableClock(now)).run()

        assertEquals(setOf("a.jpg") to Instant.parse("2026-10-19T09:00:00Z"), images.lastSweep)
    }
}

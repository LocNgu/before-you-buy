package io.github.locngu.beforeyoubuy.feature.money

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** What app start and the daily worker run: the clock's instant and zone, and never a month twice. */
class PoolAccrualTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `credits what is due in the device's zone, once`() = runTest {
        val pool = FakePoolRepository()
        pool.setUp(Money(30_000, "EUR"), 1, Money(0, "EUR"), Instant.parse("2026-10-23T10:00:00Z"), berlin)
        // Still 31 Oct in UTC, already 1 Nov in Berlin.
        val accrual = PoolAccrual(pool, MutableClock(Instant.parse("2026-10-31T23:30:00Z"), berlin))

        assertEquals(1, accrual.run())
        assertEquals(0, accrual.run())
    }
}

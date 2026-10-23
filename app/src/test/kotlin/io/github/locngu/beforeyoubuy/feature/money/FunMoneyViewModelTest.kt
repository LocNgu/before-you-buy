package io.github.locngu.beforeyoubuy.feature.money

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolEntry
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FunMoneyViewModelTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val clock = MutableClock(now)
    private val pool = FakePoolRepository()
    private val german = Locale.GERMANY

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel() = FunMoneyViewModel(pool, FakePolicyRepository(), clock).apply { setLocale(german) }

    private fun eur(minor: Long) = Money(minor, "EUR")

    private suspend fun setUpPool(startingBalance: Long = 10_000) =
        pool.setUp(eur(30_000), 1, eur(startingBalance), now, clock.zone)

    @Test
    fun `setting up starts empty on the 1st and shows when the first amount comes`() {
        val state = viewModel().uiState.value

        assertEquals(FunMoney.NotSetUp, state.money)
        assertEquals(FunMoneyForm(), state.form)
        assertEquals(LocalDate.parse("2026-11-01"), state.firstAccrual)
        assertFalse(state.canSave)
        // Nothing typed yet, so no error yet (ADR-0035).
        assertNull(state.monthlyError)
    }

    @Test
    fun `setting up stores the amount, day and starting balance and closes`() = runTest {
        val viewModel = viewModel()
        viewModel.saved.test {
            viewModel.onMonthlyChange("300")
            viewModel.onAccrualDayChange(23)
            viewModel.onStartingBalanceChange("50,50")
            assertEquals(LocalDate.parse("2026-10-23"), viewModel.uiState.value.firstAccrual)

            viewModel.save()

            awaitItem()
        }
        assertEquals(AllowanceSettings(eur(30_000), 23, LocalDate.parse("2026-10-23")), pool.allowance.value)
        // Set up on the accrual day: October is credited at once.
        assertEquals(
            listOf(
                PoolEntry.StartingBalance(eur(5_050), now),
                PoolEntry.Accrual(YearMonth.of(2026, 10), eur(30_000), now)
            ),
            pool.entries.value
        )
    }

    @Test
    fun `an empty starting balance is zero, an unreadable one blocks saving`() {
        val viewModel = viewModel()
        viewModel.onMonthlyChange("300")
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onStartingBalanceChange("abc")
        assertEquals(MoneyParseResult.Invalid, viewModel.uiState.value.startingBalanceError)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `an emptied monthly amount says so`() {
        val viewModel = viewModel()
        viewModel.onMonthlyChange("3")
        viewModel.onMonthlyChange("")

        assertEquals(MoneyParseResult.Empty, viewModel.uiState.value.monthlyError)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `once set up the form holds the stored values, the starting amount included`() = runTest {
        setUpPool()
        val state = viewModel().uiState.value

        assertEquals(FunMoneyForm(monthly = "300", accrualDay = 1, startingBalance = "100"), state.form)
        assertNull(state.firstAccrual)
        val money = state.money as FunMoney.SetUp
        assertEquals(eur(10_000), money.balance.pool)
        assertEquals(LocalDate.parse("2026-11-01"), money.nextAccrual)
    }

    @Test
    fun `a change keeps the start date and applies from the next accrual`() = runTest {
        setUpPool()
        val viewModel = viewModel()
        viewModel.saved.test {
            viewModel.onMonthlyChange("450,00")
            viewModel.save()
            awaitItem()
        }
        assertEquals(AllowanceSettings(eur(45_000), 1, LocalDate.parse("2026-10-23")), pool.allowance.value)
        // The starting amount wasn't touched, so it stays.
        assertEquals(listOf(PoolEntry.StartingBalance(eur(10_000), now)), pool.entries.value)
    }

    @Test
    fun `the starting amount stays editable after setup`() = runTest {
        setUpPool()
        clock.advance(Duration.ofDays(3))
        val viewModel = viewModel()
        viewModel.saved.test {
            viewModel.onStartingBalanceChange("80")
            viewModel.save()
            awaitItem()
        }
        // Replaced, not added to: still dated when it was first entered.
        assertEquals(listOf(PoolEntry.StartingBalance(eur(8_000), now)), pool.entries.value)
        assertEquals(eur(8_000), (viewModel.uiState.value.money as FunMoney.SetUp).balance.pool)
    }

    @Test
    fun `adding money appends an adjustment with its note and clears the fields`() = runTest {
        setUpPool()
        val viewModel = viewModel()
        viewModel.onAdjustmentChange("25")
        viewModel.onNoteChange("Birthday")
        assertTrue(viewModel.uiState.value.canAdjust)

        viewModel.add()

        assertEquals(listOf(PoolEntry.Adjustment(eur(2_500), "Birthday", now)), pool.adjustments)
        assertEquals("", viewModel.uiState.value.form?.adjustment)
        assertEquals("", viewModel.uiState.value.form?.note)
        assertEquals(eur(12_500), (viewModel.uiState.value.money as FunMoney.SetUp).balance.pool)
    }

    @Test
    fun `taking out what is there needs no confirmation`() = runTest {
        setUpPool()
        val viewModel = viewModel()
        viewModel.onAdjustmentChange("100")

        viewModel.takeOut()

        assertNull(viewModel.uiState.value.confirmTakeOut)
        assertEquals(listOf(eur(-10_000)), pool.adjustments.map { it.change })
    }

    @Test
    fun `taking out more than is there asks first and writes nothing until confirmed`() = runTest {
        setUpPool()
        val viewModel = viewModel()
        viewModel.onAdjustmentChange("150")

        viewModel.takeOut()

        assertEquals(TakeOut(eur(15_000), eur(-5_000)), viewModel.uiState.value.confirmTakeOut)
        assertEquals(emptyList<PoolEntry.Adjustment>(), pool.adjustments)

        viewModel.dismissTakeOut()
        assertNull(viewModel.uiState.value.confirmTakeOut)
        assertEquals(emptyList<PoolEntry.Adjustment>(), pool.adjustments)

        viewModel.takeOut()
        viewModel.confirmTakeOut()

        assertEquals(listOf(eur(-15_000)), pool.adjustments.map { it.change })
        assertEquals(eur(-5_000), (viewModel.uiState.value.money as FunMoney.SetUp).balance.unallocated)
    }

    @Test
    fun `adjusting needs an amount above zero and the allowance set up`() = runTest {
        val notSetUp = viewModel()
        notSetUp.onAdjustmentChange("10")
        assertFalse(notSetUp.uiState.value.canAdjust)

        setUpPool()
        val viewModel = viewModel()
        viewModel.onAdjustmentChange("0")
        assertFalse(viewModel.uiState.value.canAdjust)
        viewModel.onAdjustmentChange("1.2.3,4,5")
        assertEquals(MoneyParseResult.Invalid, viewModel.uiState.value.adjustmentError)
        viewModel.add()
        viewModel.takeOut()
        assertEquals(emptyList<PoolEntry.Adjustment>(), pool.adjustments)
    }
}

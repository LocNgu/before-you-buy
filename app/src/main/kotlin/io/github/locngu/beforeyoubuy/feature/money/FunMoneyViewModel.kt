package io.github.locngu.beforeyoubuy.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.formatForInput
import io.github.locngu.beforeyoubuy.core.domain.money.parseMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf
import io.github.locngu.beforeyoubuy.core.domain.pool.needsConfirmation
import io.github.locngu.beforeyoubuy.core.domain.pool.nextAccrualDate
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.time.today
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the fun money screen shows (brief §4.5, §6.7).
 *
 * @property money null until loaded. [FunMoney.NotSetUp] shows the setup form; [FunMoney.SetUp] the balance, the
 *   same form (the starting amount stays editable, ADR-0045) and "Adjust the amount".
 * @property form null until the stored values and the UI's locale are loaded.
 * @property firstAccrual when setting up: the day the first monthly amount would be added.
 * @property confirmTakeOut a take-out that would leave less than nothing not set aside, waiting for a confirmation.
 */
data class FunMoneyUiState(
    val money: FunMoney? = null,
    val currency: String? = null,
    val form: FunMoneyForm? = null,
    val monthlyError: MoneyParseResult? = null,
    val startingBalanceError: MoneyParseResult? = null,
    val firstAccrual: LocalDate? = null,
    val canSave: Boolean = false,
    val adjustmentError: MoneyParseResult? = null,
    val canAdjust: Boolean = false,
    val confirmTakeOut: TakeOut? = null
)

/**
 * The fields as typed. An empty [startingBalance] is zero. [wantsNeedFunding] is the "Wants need their price set
 * aside" switch, on unless turned off (ADR-0046).
 */
data class FunMoneyForm(
    val monthly: String = "",
    val accrualDay: Int = AllowanceSettings.DEFAULT_ACCRUAL_DAY,
    val startingBalance: String = "",
    val wantsNeedFunding: Boolean = true,
    val adjustment: String = "",
    val note: String = ""
)

/** Taking out [amount] would leave [leaves] (below zero) not set aside. */
data class TakeOut(val amount: Money, val leaves: Money)

/** What the user can do on the fun money screen. */
interface FunMoneyActions {
    fun onMonthlyChange(text: String)

    fun onAccrualDayChange(day: Int)

    fun onStartingBalanceChange(text: String)

    fun onWantsNeedFundingChange(on: Boolean)

    fun save()

    fun onAdjustmentChange(text: String)

    fun onNoteChange(text: String)

    fun add()

    /** Takes the amount out, or asks to confirm first if that leaves less than nothing ([confirmTakeOut]). */
    fun takeOut()

    fun confirmTakeOut()

    fun dismissTakeOut()
}

/**
 * Sets up or changes the monthly fun money and adjusts its balance (issue #22). The rules are in `:core:domain`
 * `pool/`; changes apply from the next accrual ([PoolRepository.change]). Saving the monthly amount closes the
 * screen ([saved]); an adjustment stays on it and clears its fields.
 */
@HiltViewModel
class FunMoneyViewModel @Inject constructor(
    private val pool: PoolRepository,
    items: ItemRepository,
    policies: PolicyRepository,
    private val clock: Clock
) : ViewModel(),
    FunMoneyActions {
    private val editor = MutableStateFlow(Editor())
    private val locale = MutableStateFlow<Locale?>(null)
    private val money: StateFlow<FunMoney?> =
        combine(pool.allowance, pool.entries, observeSavings(items, pool), ::funMoneyOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val currency: StateFlow<String?> =
        policies.policy.map { it.currency }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val savedEvents = Channel<Unit>(Channel.BUFFERED)

    val uiState: StateFlow<FunMoneyUiState> =
        combine(editor, money, currency, locale, ::uiStateOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, FunMoneyUiState())

    /** One event per stored setup or change; the screen closes. */
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    init {
        // Fill the form once, from the allowance as first loaded; later changes don't overwrite typing.
        viewModelScope.launch {
            val loaded = money.filterNotNull().first()
            val locale = locale.filterNotNull().first()
            editor.update {
                if (it.form != null) return@update it
                val form = when (loaded) {
                    FunMoney.NotSetUp -> FunMoneyForm()

                    is FunMoney.SetUp -> FunMoneyForm(
                        monthly = loaded.allowance.monthlyAmount.formatForInput(locale),
                        accrualDay = loaded.allowance.accrualDay,
                        // No starting amount stays an empty, optional field.
                        startingBalance = loaded.startingBalance.takeIf { s -> s.minorUnits > 0 }
                            ?.formatForInput(locale)
                            .orEmpty(),
                        wantsNeedFunding = loaded.allowance.wantsNeedFunding
                    )
                }
                it.copy(form = form)
            }
        }
    }

    /** The UI's locale, which decides how amounts are shown and read (ADR-0027). */
    fun setLocale(locale: Locale) {
        this.locale.value = locale
    }

    override fun onMonthlyChange(text: String) = edit {
        it.copy(form = it.form?.copy(monthly = text), monthlyTyped = true)
    }

    override fun onAccrualDayChange(day: Int) = edit { it.copy(form = it.form?.copy(accrualDay = day)) }

    override fun onStartingBalanceChange(text: String) = edit { it.copy(form = it.form?.copy(startingBalance = text)) }

    override fun onWantsNeedFundingChange(on: Boolean) = edit { it.copy(form = it.form?.copy(wantsNeedFunding = on)) }

    override fun onAdjustmentChange(text: String) =
        edit { it.copy(form = it.form?.copy(adjustment = text), adjustmentTyped = true) }

    override fun onNoteChange(text: String) = edit { it.copy(form = it.form?.copy(note = text)) }

    override fun save() {
        val state = uiState.value
        // The form and busy come from the editor itself: uiState may not have caught up with a tap a moment ago.
        val form = editor.value.form ?: return
        if (!state.canSave || editor.value.busy) return
        val monthly = (parse(form.monthly) as? MoneyParseResult.Success)?.money ?: return
        val starting = startingBalanceOf(form) ?: return
        val setUp = state.money is FunMoney.SetUp
        editor.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                // Set up twice at once (two screens): the second writes nothing, which is fine to close on.
                if (setUp) {
                    pool.change(monthly, form.accrualDay, starting, form.wantsNeedFunding, clock.instant(), clock.zone)
                } else {
                    pool.setUp(monthly, form.accrualDay, starting, form.wantsNeedFunding, clock.instant(), clock.zone)
                }
            } finally {
                editor.update { it.copy(busy = false) }
            }
            savedEvents.send(Unit)
        }
    }

    override fun add() {
        val amount = adjustmentAmount() ?: return
        adjust(amount)
    }

    override fun takeOut() {
        val amount = adjustmentAmount() ?: return
        val balance = (money.value as? FunMoney.SetUp)?.balance ?: return
        val change = Money(-amount.minorUnits, amount.currency)
        if (needsConfirmation(balance, change)) {
            editor.update { it.copy(confirmTakeOut = TakeOut(amount, balance.unallocated + change)) }
        } else {
            adjust(change)
        }
    }

    override fun confirmTakeOut() {
        val pending = editor.value.confirmTakeOut ?: return
        editor.update { it.copy(confirmTakeOut = null) }
        adjust(Money(-pending.amount.minorUnits, pending.amount.currency))
    }

    override fun dismissTakeOut() = editor.update { it.copy(confirmTakeOut = null) }

    private fun adjust(change: Money) {
        val note = editor.value.form?.note
        editor.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                pool.adjust(change, note, clock.instant())
                editor.update {
                    it.copy(form = it.form?.copy(adjustment = "", note = ""), adjustmentTyped = false)
                }
            } finally {
                editor.update { it.copy(busy = false) }
            }
        }
    }

    /** The typed adjustment if it can be applied now: above zero, the screen set up and idle. */
    private fun adjustmentAmount(): Money? {
        if (!uiState.value.canAdjust || editor.value.busy) return null
        val amount = (parse(editor.value.form?.adjustment ?: return null) as? MoneyParseResult.Success)?.money
        return amount?.takeIf { it.minorUnits > 0 }
    }

    private fun edit(change: (Editor) -> Editor) = editor.update { if (it.busy || it.form == null) it else change(it) }

    private fun parse(text: String): MoneyParseResult {
        val currency = currency.value ?: return MoneyParseResult.Invalid
        val locale = locale.value ?: return MoneyParseResult.Invalid
        return parseMoney(text, currency, locale)
    }

    /** Empty is zero; anything else must read. Null if it doesn't. */
    private fun startingBalanceOf(form: FunMoneyForm): Money? {
        val currency = currency.value ?: return null
        if (form.startingBalance.isBlank()) return Money(0, currency)
        return (parse(form.startingBalance) as? MoneyParseResult.Success)?.money
    }

    private fun uiStateOf(editor: Editor, money: FunMoney?, currency: String?, locale: Locale?): FunMoneyUiState {
        val form = editor.form
        if (money == null || currency == null || locale == null || form == null) return FunMoneyUiState(money = money)
        val monthly = parseMoney(form.monthly, currency, locale)
        val setUp = money as? FunMoney.SetUp
        val starting = if (form.startingBalance.isBlank()) {
            null
        } else {
            parseMoney(form.startingBalance, currency, locale).takeIf { it !is MoneyParseResult.Success }
        }
        val adjustment = parseMoney(form.adjustment, currency, locale)
        val adjustmentValid = adjustment is MoneyParseResult.Success && adjustment.money.minorUnits > 0
        return FunMoneyUiState(
            money = money,
            currency = currency,
            form = form,
            // Like quick add: an error shows once the field has held text (ADR-0035); a stored amount has.
            monthlyError = monthly.takeIf { it !is MoneyParseResult.Success && (editor.monthlyTyped || setUp != null) },
            startingBalanceError = starting,
            firstAccrual = if (setUp == null) {
                nextAccrualDate(AllowanceSettings(Money(0, currency), form.accrualDay, clock.today()), null)
            } else {
                null
            },
            canSave = !editor.busy && monthly is MoneyParseResult.Success && starting == null,
            adjustmentError = adjustment.takeIf {
                editor.adjustmentTyped && it !is MoneyParseResult.Success && it !is MoneyParseResult.Empty
            },
            canAdjust = setUp != null && !editor.busy && adjustmentValid,
            confirmTakeOut = editor.confirmTakeOut
        )
    }

    /** The screen's own state. [monthlyTyped]/[adjustmentTyped]: the field has held text, so errors show. */
    private data class Editor(
        val form: FunMoneyForm? = null,
        val monthlyTyped: Boolean = false,
        val adjustmentTyped: Boolean = false,
        val busy: Boolean = false,
        val confirmTakeOut: TakeOut? = null
    )
}

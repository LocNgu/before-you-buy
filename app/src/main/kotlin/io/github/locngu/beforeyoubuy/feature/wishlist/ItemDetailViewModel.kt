package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind
import io.github.locngu.beforeyoubuy.core.domain.decision.OPPORTUNITY_COST_DETAIL_LINES
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine
import io.github.locngu.beforeyoubuy.core.domain.decision.opportunityCostOf
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemImageStore
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.formatForInput
import io.github.locngu.beforeyoubuy.core.domain.money.parseMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.AllocationResult
import io.github.locngu.beforeyoubuy.core.domain.pool.AmountProblem
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.checkAmount
import io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf
import io.github.locngu.beforeyoubuy.core.domain.pool.quickAmounts
import io.github.locngu.beforeyoubuy.core.domain.pool.restToSetAside
import io.github.locngu.beforeyoubuy.core.domain.pool.setAsideLimit
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.wishlist.ItemDetail
import io.github.locngu.beforeyoubuy.core.domain.wishlist.canMarkAsNeed
import io.github.locngu.beforeyoubuy.core.domain.wishlist.itemDetailOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.feature.priorities.makePriority
import io.github.locngu.beforeyoubuy.feature.priorities.removePriority
import java.io.File
import java.time.Clock
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What item detail shows (brief §6.3). [detail] is null until loaded and while [gone]: the item was ended (e.g.
 * "Let it go" from a notification) or deleted. [photo] is the item's photo file, if it has one. [opportunityCost] is
 * the compact "What else this money could do" (brief §7, at most [OPPORTUNITY_COST_DETAIL_LINES]). [sheet] is the
 * open "Set aside" / "Take back" sheet, if any. [canSetAside]: something isn't set aside yet, so "Set aside" is offered.
 */
data class ItemDetailUiState(
    val detail: ItemDetail? = null,
    val gone: Boolean = false,
    val photo: File? = null,
    val opportunityCost: List<OpportunityCostLine> = emptyList(),
    val sheet: SavingsSheet? = null,
    val canSetAside: Boolean = false
)

/** Which way the savings sheet moves money (brief §4.6). */
enum class SavingsMode { SET_ASIDE, TAKE_BACK }

/**
 * The open savings sheet. [limit] is the most it can move: what isn't set aside yet ([setAsideLimit]) or what the
 * item holds; an amount that reads the same moves exactly that (`withinShown`). [rest] is what the "The rest" (up to
 * the price) / "All" chip fills in, [quickAmounts] the amount chips that fit; null or empty when there is nothing.
 * [error] shows once the field has held text (ADR-0035).
 */
data class SavingsSheet(
    val mode: SavingsMode,
    val amount: String,
    val limit: Money,
    val rest: Money?,
    val quickAmounts: List<Money> = emptyList(),
    val error: SavingsError? = null,
    val canConfirm: Boolean = false
)

/** Why the typed amount can't be moved. */
sealed interface SavingsError {
    /** It doesn't read as an amount; [result] says how. */
    data class Unreadable(val result: MoneyParseResult) : SavingsError

    data object Zero : SavingsError

    /** More than [SavingsSheet.limit]. */
    data object MoreThanLimit : SavingsError
}

/** A message for the snackbar. */
enum class ItemDetailMessage {
    /** "Mark as need" found the item changed meanwhile and wrote nothing. */
    CHANGED_MEANWHILE,

    /** Setting aside or taking back found the balance or item changed meanwhile and wrote nothing. */
    SAVINGS_CHANGED_MEANWHILE
}

/** What the user can do with an item's savings (brief §4.6). */
interface SavingsActions {
    fun openSetAside()

    fun openTakeBack()

    fun onSavingsAmountChange(text: String)

    /** A quick chip: [amount] fills the field. */
    fun onSavingsChip(amount: Money)

    fun confirmSavings()

    fun dismissSavings()
}

/**
 * One item's detail (brief §6.3, ADR-0013): the derived stage and countdown come from `itemDetailOf` in
 * `:core:domain`, recomputed when its cooling-off ends or the date changes (ADR-0018), like the wishlist. Money is
 * set aside and taken back here (brief §4.6); the limits are checked again when it's stored
 * ([PoolRepository.setAside]).
 */
@HiltViewModel(assistedFactory = ItemDetailViewModel.Factory::class)
class ItemDetailViewModel @AssistedInject constructor(
    @Assisted itemId: Long,
    private val items: ItemRepository,
    private val pool: PoolRepository,
    private val policies: PolicyRepository,
    private val images: ItemImageStore,
    private val clock: Clock
) : ViewModel(),
    SavingsActions {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): ItemDetailViewModel
    }

    private val id = ItemId(itemId)
    private val messages = Channel<ItemDetailMessage>(Channel.BUFFERED)
    private val sheetEditor = MutableStateFlow<SheetEditor?>(null)
    private val locale = MutableStateFlow<Locale?>(null)
    private val savings = observeSavings(items, pool)

    // Only while the screen shows it: the sheet reads it through uiState.
    private val funMoney: StateFlow<FunMoney?> = combine(pool.allowance, pool.entries, savings, ::funMoneyOf)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val details: Flow<ItemDetailUiState> =
        combine(
            items.observeById(id),
            items.observeReflection(id),
            items.observeActive(),
            savings,
            funMoney.filterNotNull(),
            ::Inputs
        )
            .flatMapLatest { (item, reflection, active, savings, money) ->
                flow {
                    if (item == null || item.status != ItemStatus.ACTIVE) {
                        emit(ItemDetailUiState(gone = true))
                        return@flow
                    }
                    while (true) {
                        val now = clock.instant()
                        emit(
                            ItemDetailUiState(
                                detail = itemDetailOf(item, reflection, savings, now, clock.zone),
                                photo = item.imageRef?.let(images::fileFor),
                                opportunityCost = opportunityCostOf(item, active, savings, money)
                                    .take(OPPORTUNITY_COST_DETAIL_LINES)
                            )
                        )
                        delay(untilWishlistChanges(listOf(item), now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }

    val uiState: StateFlow<ItemDetailUiState> =
        combine(details, sheetEditor, funMoney, locale) { state, editor, money, locale ->
            val balance = (money as? FunMoney.SetUp)?.balance
            val currency = state.detail?.savings?.price?.currency
            state.copy(
                sheet = editor?.let { sheetOf(it, state.detail, money, locale) },
                canSetAside = balance != null && currency != null && setAsideLimit(balance, currency).minorUnits > 0
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    /** One-shot messages for the snackbar. */
    val message: Flow<ItemDetailMessage> = messages.receiveAsFlow()

    private val priorityChannel = Channel<PriorityEvent>(Channel.BUFFERED)

    /** What "Make priority" / "Remove from priorities" did: a message, or opening the head-to-head. */
    val priorityEvents: Flow<PriorityEvent> = priorityChannel.receiveAsFlow()

    // One priority write at a time: a double tap would otherwise find its own change and report a conflict.
    private var writingPriorities = false

    /** "Make priority" (brief §4.7): a free slot at once, full slots through the head-to-head. */
    fun makePriority() = writePriorities { makePriority(id, items, policies) }

    /** "Remove from priorities" (ADR-0047): the ones below move up. */
    fun removePriority() = writePriorities { removePriority(id, items) }

    private fun writePriorities(write: suspend () -> PriorityEvent) {
        if (writingPriorities) return
        writingPriorities = true
        viewModelScope.launch {
            try {
                priorityChannel.send(write())
            } finally {
                writingPriorities = false
            }
        }
    }

    /** The UI's locale, which decides how amounts are read (ADR-0027). */
    fun setLocale(locale: Locale) {
        this.locale.value = locale
    }

    /**
     * Turns the want into a need (ADR-0008): it skips the rest of its wait and reflection, and history keeps that it
     * was marked as a need. What was set aside for it goes back to the fun money (ADR-0044); the screen says so
     * before. Does nothing for a need.
     */
    fun markAsNeed() {
        val item = uiState.value.detail?.item ?: return
        if (!item.canMarkAsNeed) return
        viewModelScope.launch {
            val rules = policies.policy.first().coolingOffRules
            // Only while a currency change is being written could they differ.
            if (rules.currency != item.price.currency) return@launch messages.send(ItemDetailMessage.CHANGED_MEANWHILE)
            val need = changeKind(item, ItemKind.NEED, clock.instant(), rules, clock.zone)
            if (!items.update(item, need)) messages.send(ItemDetailMessage.CHANGED_MEANWHILE)
        }
    }

    override fun openSetAside() = open(SavingsMode.SET_ASIDE)

    override fun openTakeBack() = open(SavingsMode.TAKE_BACK)

    private fun open(mode: SavingsMode) {
        val state = uiState.value
        val detail = state.detail ?: return
        if (!detail.funMoneySetUp || detail.savings == null) return
        if (mode == SavingsMode.SET_ASIDE && !state.canSetAside) return
        sheetEditor.value = SheetEditor(mode)
    }

    override fun onSavingsAmountChange(text: String) = sheetEditor.update {
        if (it == null || it.busy) it else it.copy(amount = text, typed = true)
    }

    override fun onSavingsChip(amount: Money) {
        val locale = locale.value ?: return
        onSavingsAmountChange(amount.formatForInput(locale))
    }

    override fun dismissSavings() = sheetEditor.update { if (it?.busy == true) it else null }

    override fun confirmSavings() {
        // The editor, not uiState, which may not have caught up with a keystroke a moment ago.
        val editor = sheetEditor.value ?: return
        val sheet = sheetOf(editor, uiState.value.detail, funMoney.value, locale.value) ?: return
        if (editor.busy || !sheet.canConfirm) return
        val amount = (parse(editor.amount, sheet.limit.currency) as? MoneyParseResult.Success)?.money ?: return
        sheetEditor.value = editor.copy(busy = true)
        viewModelScope.launch {
            val result = try {
                when (editor.mode) {
                    SavingsMode.SET_ASIDE -> pool.setAside(id, amount, clock.instant())
                    SavingsMode.TAKE_BACK -> pool.takeBack(id, amount, clock.instant())
                }
            } catch (_: IllegalArgumentException) {
                // The currency changed while the sheet was open: the amount no longer fits. Nothing was written.
                null
            } finally {
                sheetEditor.update { it?.copy(busy = false) }
            }
            if (result is AllocationResult.Allowed) {
                sheetEditor.value = null
            } else {
                messages.send(ItemDetailMessage.SAVINGS_CHANGED_MEANWHILE)
            }
        }
    }

    private fun parse(text: String, currency: String): MoneyParseResult {
        val locale = locale.value ?: return MoneyParseResult.Invalid
        return parseMoney(text, currency, locale)
    }

    private fun sheetOf(editor: SheetEditor, detail: ItemDetail?, money: FunMoney?, locale: Locale?): SavingsSheet? {
        val progress = detail?.savings ?: return null
        val balance = (money as? FunMoney.SetUp)?.balance ?: return null
        if (locale == null) return null
        val currency = progress.price.currency
        val limit = when (editor.mode) {
            SavingsMode.SET_ASIDE -> setAsideLimit(balance, currency)
            SavingsMode.TAKE_BACK -> progress.allocated
        }
        val parsed = parseMoney(editor.amount, currency, locale)
        val error = if (parsed is MoneyParseResult.Success) {
            when (checkAmount(parsed.money, limit)) {
                AmountProblem.ZERO -> SavingsError.Zero
                AmountProblem.MORE_THAN_LIMIT -> SavingsError.MoreThanLimit
                null -> null
            }
        } else {
            SavingsError.Unreadable(parsed)
        }
        return SavingsSheet(
            mode = editor.mode,
            amount = editor.amount,
            limit = limit,
            rest = when (editor.mode) {
                SavingsMode.SET_ASIDE -> restToSetAside(progress, limit)
                SavingsMode.TAKE_BACK -> progress.allocated.takeIf { it.minorUnits > 0 }
            },
            quickAmounts = if (editor.mode == SavingsMode.SET_ASIDE) quickAmounts(limit) else emptyList(),
            error = error.takeIf {
                editor.typed &&
                    !(it is SavingsError.Unreadable && it.result == MoneyParseResult.Empty)
            },
            canConfirm = error == null && !editor.busy
        )
    }

    private data class Inputs(
        val item: Item?,
        val reflection: Reflection?,
        val active: List<Item>,
        val savings: Savings,
        val funMoney: FunMoney
    )

    /** The sheet's own state. [typed]: the field has held text, so errors show. */
    private data class SheetEditor(
        val mode: SavingsMode,
        val amount: String = "",
        val typed: Boolean = false,
        val busy: Boolean = false
    )
}

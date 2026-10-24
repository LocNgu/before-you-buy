package io.github.locngu.beforeyoubuy.feature.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.decision.BuyPath
import io.github.locngu.beforeyoubuy.core.domain.decision.DecisionForm
import io.github.locngu.beforeyoubuy.core.domain.decision.DecisionOverview
import io.github.locngu.beforeyoubuy.core.domain.decision.PurchaseCheck
import io.github.locngu.beforeyoubuy.core.domain.decision.checkPurchase
import io.github.locngu.beforeyoubuy.core.domain.decision.decisionFormOf
import io.github.locngu.beforeyoubuy.core.domain.decision.decisionOverviewOf
import io.github.locngu.beforeyoubuy.core.domain.decision.earliestWaitDate
import io.github.locngu.beforeyoubuy.core.domain.decision.purchaseDates
import io.github.locngu.beforeyoubuy.core.domain.decision.purchaseDecision
import io.github.locngu.beforeyoubuy.core.domain.decision.rejectDecision
import io.github.locngu.beforeyoubuy.core.domain.decision.waitDecision
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf
import io.github.locngu.beforeyoubuy.core.domain.pool.leftAfterPurchase
import io.github.locngu.beforeyoubuy.core.domain.pool.poolPurchaseOf
import io.github.locngu.beforeyoubuy.core.domain.pool.purchaseNeedsConfirmation
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the decision screen shows: the overview, or one decision being confirmed (issue #18). */
enum class DecisionStep {
    OVERVIEW,

    /** Confirm the price paid and the date; on [BuyPath.EARLY] also the reason, days left and reasons against. */
    BUY,

    /** Pick when to look again. */
    WAIT,

    /** "Don't buy", with an optional "Why not?". */
    REJECT
}

/** How a decision ended the screen. */
sealed interface DecisionOutcome {
    data object Bought : DecisionOutcome

    data object Rejected : DecisionOutcome

    data class Waiting(val until: LocalDate) : DecisionOutcome
}

/** A message for the snackbar. */
enum class DecisionMessage {
    /** The item changed meanwhile (edited, decided from a notification); nothing was written. */
    CHANGED_MEANWHILE
}

/**
 * What the decision screen shows (brief §6.5).
 *
 * @property overview null until loaded and while [gone]: the item was ended (e.g. "Let it go" from a notification)
 *   or deleted.
 * @property buyPath how the BUY step buys: fixed when it opens, so an early purchase stays early even if cooling off
 *   ends while the reason is typed (ADR-0006: allowed "any time").
 * @property form null until the item and the UI's locale are loaded.
 * @property purchase how the BUY step's fields read; null without a [form].
 * @property purchaseDates the dates the BUY step's date picker offers.
 * @property earliestWait the first date the WAIT step offers (tomorrow).
 * @property confirmBelowZero a purchase that would leave less than nothing not set aside, waiting for a confirmation
 *   (issue #23).
 */
data class DecisionUiState(
    val overview: DecisionOverview? = null,
    val gone: Boolean = false,
    val step: DecisionStep = DecisionStep.OVERVIEW,
    val buyPath: BuyPath? = null,
    val form: DecisionForm? = null,
    val purchase: PurchaseCheck? = null,
    val purchaseDates: ClosedRange<LocalDate>? = null,
    val today: LocalDate? = null,
    val earliestWait: LocalDate? = null,
    val saving: Boolean = false,
    val confirmBelowZero: BelowZero? = null
)

/** Paying [paid] would leave [leaves] (below zero) not yet set aside. */
data class BelowZero(val paid: Money, val leaves: Money)

/** What the user can do on the decision screen. */
interface DecisionActions {
    /** Opens the BUY step: a plain Buy, a need, or "Decide early…" for an item that isn't Ready. */
    fun onBuy()

    fun onWait()

    fun onReject()

    /** From a step back to the overview, nothing decided. */
    fun onBackToOverview()

    fun onPriceChange(price: String)

    fun onBoughtOnChange(date: LocalDate)

    fun onEarlyReasonChange(reason: String)

    fun onWaitUntilChange(date: LocalDate)

    fun onNoteChange(note: String)

    /**
     * Records the open step's decision, if its inputs are valid. A purchase that would leave less than nothing not set
     * aside asks first ([DecisionUiState.confirmBelowZero]).
     */
    fun confirm()

    fun confirmBelowZero()

    fun dismissBelowZero()
}

/**
 * The decision screen for one item (brief §5 "Decisions", §6.5, issue #18): Buy, Wait or Reject, and "Decide early…"
 * behind friction while the item isn't Ready (ADR-0006). What's offered, the dates and the decisions themselves come
 * from `:core:domain` `decision/`; every decision is checked by `applyDecision` and stored with the compare-and-set
 * `recordDecision`, so one that raced with a notification or an edit is never written.
 */
@HiltViewModel(assistedFactory = DecisionViewModel.Factory::class)
class DecisionViewModel @AssistedInject constructor(
    @Assisted itemId: Long,
    private val items: ItemRepository,
    pool: PoolRepository,
    private val clock: Clock
) : ViewModel(),
    DecisionActions {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): DecisionViewModel
    }

    private val id = ItemId(itemId)
    private val editor = MutableStateFlow(Editor())
    private val outcomes = Channel<DecisionOutcome>(Channel.BUFFERED)
    private val messages = Channel<DecisionMessage>(Channel.BUFFERED)
    private val savings = observeSavings(items, pool)
    private val funMoney: StateFlow<FunMoney?> =
        combine(
            pool.allowance,
            pool.entries,
            savings,
            ::funMoneyOf
        ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val loaded: Flow<Loaded> =
        combine(items.observeById(id), items.observeReflection(id), items.observeActive(), savings, ::Inputs)
            .flatMapLatest { (item, reflection, active, savings) ->
                flow {
                    if (item == null || item.status != ItemStatus.ACTIVE) {
                        emit(Loaded.Gone)
                        return@flow
                    }
                    // Recomputed when cooling off ends, a wait's date comes or the date changes, like item detail.
                    while (true) {
                        val now = clock.instant()
                        emit(Loaded.Active(decisionOverviewOf(item, reflection, active, savings, now, clock.zone)))
                        delay(untilWishlistChanges(listOf(item), now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }

    val uiState: StateFlow<DecisionUiState> =
        combine(loaded, editor, ::Pair)
            // Once decided, the screen keeps showing what was decided on while it closes: the item has ended by
            // then, which would otherwise read as "gone".
            .runningFold(DecisionUiState()) { previous, (loaded, editor) ->
                if (editor.decided) previous.copy(saving = true) else uiStateOf(loaded, editor)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionUiState())

    /** One event once a decision is stored; the screen closes. */
    val outcome: Flow<DecisionOutcome> = outcomes.receiveAsFlow()

    /** One-shot messages for the snackbar. */
    val message: Flow<DecisionMessage> = messages.receiveAsFlow()

    /** The UI's locale, which decides how the price is shown and read (`1.234,56` vs `1,234.56`, ADR-0027). */
    fun setLocale(locale: Locale) = editor.update { it.copy(locale = locale) }

    override fun onBuy() {
        val overview = uiState.value.overview ?: return
        editor.update { it.copy(step = DecisionStep.BUY, buyPath = overview.buyPath) }
    }

    override fun onWait() {
        if (uiState.value.overview?.buyPath?.canWait != true) return
        editor.update { it.copy(step = DecisionStep.WAIT) }
    }

    override fun onReject() {
        if (uiState.value.overview == null) return
        editor.update { it.copy(step = DecisionStep.REJECT) }
    }

    override fun onBackToOverview() = editor.update { it.copy(step = DecisionStep.OVERVIEW) }

    override fun onPriceChange(price: String) = editForm { it.copy(price = price) }

    override fun onBoughtOnChange(date: LocalDate) = editForm { it.copy(boughtOn = date) }

    override fun onEarlyReasonChange(reason: String) = editForm { it.copy(earlyReason = reason) }

    override fun onWaitUntilChange(date: LocalDate) = editForm { it.copy(waitUntil = date) }

    override fun onNoteChange(note: String) = editForm { it.copy(note = note) }

    override fun confirm() {
        val state = uiState.value
        val overview = state.overview ?: return
        val current = editor.value
        val form = state.form ?: return
        val locale = current.locale ?: return
        if (current.saving || current.decided) return
        val now = clock.instant()
        val zone = clock.zone
        // The step on screen: one that no longer fits has fallen back to the overview there.
        val decision = when (state.step) {
            DecisionStep.OVERVIEW -> return

            DecisionStep.BUY -> {
                val path = current.buyPath ?: return
                if (state.purchase?.isValid != true) return
                purchaseDecision(form, overview.item, path, locale, now, zone)
            }

            DecisionStep.WAIT -> {
                if (form.waitUntil < earliestWaitDate(now.atZone(zone).toLocalDate())) return
                waitDecision(form, now, zone)
            }

            DecisionStep.REJECT -> rejectDecision(form, now)
        }
        // Until the fun money is loaded, a purchase can't be checked against it: never skip the question.
        if (decision.type.isPurchase && funMoney.value == null) return
        val belowZero = belowZeroOf(overview, decision)
        if (belowZero != null) {
            editor.update { it.copy(pending = Pending(decision, belowZero)) }
            return
        }
        record(overview, decision)
    }

    override fun confirmBelowZero() {
        val pending = editor.value.pending ?: return
        editor.update { it.copy(pending = null) }
        if (editor.value.saving || editor.value.decided) return
        // Checked against the item and its savings as they are now, not as when the question opened.
        record(uiState.value.overview ?: return, pending.decision)
    }

    override fun dismissBelowZero() = editor.update { it.copy(pending = null) }

    /**
     * What a want's purchase would leave not set aside, if that's below zero and fun money is set up; null otherwise.
     * Its own savings are spent first ([purchaseNeedsConfirmation]).
     */
    private fun belowZeroOf(overview: DecisionOverview, decision: Decision): BelowZero? {
        val paid = decision.actualPrice ?: return null
        val balance = (funMoney.value as? FunMoney.SetUp)?.balance ?: return null
        if (poolPurchaseOf(overview.item, decision, funMoneySetUp = true) == null) return null
        val allocated = overview.funding.allocated
        if (!purchaseNeedsConfirmation(balance, allocated, paid)) return null
        return BelowZero(paid, leftAfterPurchase(balance, allocated, paid))
    }

    private fun record(overview: DecisionOverview, decision: Decision) {
        val item = overview.item
        editor.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = applyDecision(item, overview.reflection, overview.funding, decision)
            val stored = result is DecisionResult.Applied && items.recordDecision(item, decision, result.item)
            if (!stored) {
                editor.update { it.copy(saving = false, step = DecisionStep.OVERVIEW) }
                messages.send(DecisionMessage.CHANGED_MEANWHILE)
                return@launch
            }
            editor.update { it.copy(saving = false, decided = true) }
            outcomes.send(
                when (decision.type) {
                    DecisionType.REJECT -> DecisionOutcome.Rejected

                    DecisionType.WAIT -> DecisionOutcome.Waiting(
                        checkNotNull(decision.waitUntil).atZone(clock.zone).toLocalDate()
                    )

                    else -> DecisionOutcome.Bought
                }
            )
        }
    }

    /** Changes the form as shown: the first change starts from the one derived from the item. */
    private fun editForm(change: (DecisionForm) -> DecisionForm) {
        val shown = uiState.value.form ?: return
        editor.update { current -> current.copy(form = change(current.form ?: shown)) }
    }

    private fun uiStateOf(loaded: Loaded, editor: Editor): DecisionUiState {
        if (loaded is Loaded.Gone) return DecisionUiState(gone = true)
        val overview = (loaded as Loaded.Active).overview
        val locale = editor.locale
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        // Until the first change the form follows the item (its exact price, today); after it, typing is kept.
        val form = editor.form ?: locale?.let { decisionFormOf(overview.item, today, locale) }
        val path = editor.buyPath ?: overview.buyPath
        // A step whose action no longer fits (Wait on an item that stopped being Ready) falls back to the overview.
        val step = if (editor.step == DecisionStep.WAIT &&
            !overview.buyPath.canWait
        ) {
            DecisionStep.OVERVIEW
        } else {
            editor.step
        }
        return DecisionUiState(
            overview = overview,
            step = step,
            buyPath = path,
            form = form,
            purchase = if (form != null && locale != null) {
                checkPurchase(form, overview.item, path, locale, today, clock.zone)
            } else {
                null
            },
            purchaseDates = purchaseDates(overview.item, path, today, clock.zone),
            today = today,
            earliestWait = earliestWaitDate(today),
            saving = editor.saving,
            confirmBelowZero = editor.pending?.belowZero
        )
    }

    private data class Inputs(
        val item: Item?,
        val reflection: Reflection?,
        val active: List<Item>,
        val savings: Savings
    )

    /** A purchase waiting for "Go below zero?". */
    private data class Pending(val decision: Decision, val belowZero: BelowZero)

    private sealed interface Loaded {
        data object Gone : Loaded

        data class Active(val overview: DecisionOverview) : Loaded
    }

    /** What the user did on the screen so far. */
    private data class Editor(
        val step: DecisionStep = DecisionStep.OVERVIEW,
        val buyPath: BuyPath? = null,
        val form: DecisionForm? = null,
        val locale: Locale? = null,
        val saving: Boolean = false,
        val decided: Boolean = false,
        val pending: Pending? = null
    )
}

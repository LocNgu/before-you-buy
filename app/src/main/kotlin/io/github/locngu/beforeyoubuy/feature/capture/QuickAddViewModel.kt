package io.github.locngu.beforeyoubuy.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.capture.capturedItem
import io.github.locngu.beforeyoubuy.core.domain.capture.checkCapture
import io.github.locngu.beforeyoubuy.core.domain.capture.coolingOffDays
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Values quick add opens with, e.g. from the share sheet (#15): text as the user would have typed it. */
data class QuickAddPrefill(val name: String = "", val price: String = "", val link: String = "")

/** The field that has the cursor when the sheet opens. */
enum class QuickAddField { NAME, PRICE }

/** An item was added: "Added. You can decide in 30 days." for a want, "Added as a need." for a need. */
data class QuickAddSaved(val kind: ItemKind, val coolingOffDays: Int)

/**
 * What the quick-add sheet shows (brief §6.2).
 *
 * @property showNameMissing the name was typed and then cleared. An untouched field shows no error.
 * @property priceError why the typed price can't be read, once something was typed into it; null if it reads.
 * @property currency the app's currency, null until it's loaded.
 * @property today the earliest target date the picker offers.
 * @property startField where the cursor goes on open: the price when a share filled in the name but not the price
 *   (it can't be read offline, #15), else the name.
 */
data class QuickAddUiState(
    val visible: Boolean = false,
    val form: CaptureForm = CaptureForm(),
    val detailsExpanded: Boolean = false,
    val showNameMissing: Boolean = false,
    val priceError: MoneyParseResult? = null,
    val canSave: Boolean = false,
    val currency: String? = null,
    val today: LocalDate? = null,
    val startField: QuickAddField = QuickAddField.NAME
)

/** What the user can do in the quick-add sheet. */
interface QuickAddActions : ItemFormActions {
    fun onDetailsExpandedChange(expanded: Boolean)

    fun save()
}

/**
 * Quick add: capture a want in ≤ 10 seconds, name and price (brief §6.2). No reflection questions; those come
 * after capture (ADR-0009). The rules are in `:core:domain` `capture/`; this only keeps the form and stores it.
 *
 * Each [open] starts with an empty (or prefilled) form; closing the sheet without saving discards it.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val items: ItemRepository,
    policies: PolicyRepository,
    private val clock: Clock
) : ViewModel(),
    QuickAddActions {
    private val editor = MutableStateFlow(Editor())
    private val policy: StateFlow<Policy?> = policies.policy.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val savedEvents = Channel<QuickAddSaved>(Channel.BUFFERED)
    private var opened = false

    val uiState: StateFlow<QuickAddUiState> =
        combine(editor, policy) { editor, policy -> editor.toUiState(policy) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, QuickAddUiState())

    /** One event per stored item, for the snackbar. */
    val saved: Flow<QuickAddSaved> = savedEvents.receiveAsFlow()

    /** Opens the sheet with a fresh form, filled with [prefill]. */
    fun open(prefill: QuickAddPrefill = QuickAddPrefill()) {
        opened = true
        val form = CaptureForm(name = prefill.name, price = prefill.price, link = prefill.link)
        val startField = if (form.name.isNotBlank() && form.price.isEmpty()) QuickAddField.PRICE else QuickAddField.NAME
        editor.update {
            Editor(
                visible = true,
                form = form,
                nameTouched = form.name.isNotEmpty(),
                priceTouched = form.price.isNotEmpty(),
                today = clock.today(),
                locale = it.locale,
                startField = startField
            )
        }
    }

    /**
     * Opens the sheet with [prefill] the first time it's called on this view model; later calls (the screen was
     * recreated, e.g. rotated) keep what's there. True while the sheet is open, false once it was saved or closed.
     */
    fun openOnce(prefill: QuickAddPrefill): Boolean {
        if (!opened) open(prefill)
        return editor.value.visible
    }

    /**
     * Closes the sheet without saving. Ignored while saving: the save closes it when it's stored, and closing first
     * would let the share activity finish and cancel the save with it.
     */
    fun dismiss() = editor.update { if (it.saving) it else it.copy(visible = false) }

    /** The UI's locale, which decides how a typed price reads (`1.234,56` vs `1,234.56`, ADR-0027). */
    fun setLocale(locale: Locale) = editor.update { it.copy(locale = locale) }

    override fun onNameChange(name: String) = editForm(nameTouched = name.isNotEmpty()) { it.copy(name = name) }

    override fun onPriceChange(price: String) = editForm(priceTouched = price.isNotEmpty()) { it.copy(price = price) }

    override fun onLinkChange(link: String) = editForm { it.copy(link = link) }

    override fun onNeedChange(need: Boolean) = editForm { it.copy(kind = if (need) ItemKind.NEED else ItemKind.WANT) }

    override fun onDetailsExpandedChange(expanded: Boolean) = editor.update { it.copy(detailsExpanded = expanded) }

    override fun onCategoryChange(category: String) = editForm { it.copy(category = category) }

    override fun onNotesChange(notes: String) = editForm { it.copy(notes = notes) }

    override fun onTargetDateChange(date: LocalDate?) = editForm { it.copy(targetDate = date) }

    /** Stores the item and closes the sheet. Does nothing while the form is invalid or already being saved. */
    override fun save() {
        val current = editor.value
        val policy = policy.value ?: return
        val locale = current.locale ?: return
        if (!current.visible || current.saving || !checkCapture(current.form, policy.currency, locale).isValid) return
        editor.value = current.copy(saving = true)
        viewModelScope.launch {
            val item = capturedItem(current.form, policy, locale, clock.instant(), clock.zone)
            try {
                items.add(item)
                // The form stays until the next open, so the sheet doesn't empty while it slides away.
                editor.update { it.copy(visible = false) }
            } finally {
                // Also if storing fails, so the form never stays locked.
                editor.update { it.copy(saving = false) }
            }
            savedEvents.send(QuickAddSaved(item.kind, coolingOffDays(item, clock.zone)))
        }
    }

    private fun editForm(
        nameTouched: Boolean = false,
        priceTouched: Boolean = false,
        change: (CaptureForm) -> CaptureForm
    ) = editor.update {
        if (it.saving) {
            it
        } else {
            it.copy(
                form = change(it.form),
                nameTouched = it.nameTouched || nameTouched,
                priceTouched = it.priceTouched || priceTouched
            )
        }
    }

    /** The sheet's own state. A field counts as touched once it held text, so an empty form shows no errors. */
    private data class Editor(
        val visible: Boolean = false,
        val form: CaptureForm = CaptureForm(),
        val detailsExpanded: Boolean = false,
        val nameTouched: Boolean = false,
        val priceTouched: Boolean = false,
        val saving: Boolean = false,
        val today: LocalDate? = null,
        val locale: Locale? = null,
        val startField: QuickAddField = QuickAddField.NAME
    ) {
        fun toUiState(policy: Policy?): QuickAddUiState {
            val check = if (policy != null && locale != null) checkCapture(form, policy.currency, locale) else null
            return QuickAddUiState(
                visible = visible,
                form = form,
                detailsExpanded = detailsExpanded,
                showNameMissing = nameTouched && form.name.isBlank(),
                priceError = check?.price?.takeIf { priceTouched && it !is MoneyParseResult.Success },
                canSave = check?.isValid == true && !saving,
                currency = policy?.currency,
                today = today,
                startField = startField
            )
        }
    }
}

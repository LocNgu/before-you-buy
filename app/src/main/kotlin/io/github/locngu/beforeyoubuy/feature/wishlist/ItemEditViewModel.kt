package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.capture.checkCapture
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.time.today
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WaitChange
import io.github.locngu.beforeyoubuy.core.domain.wishlist.editFormOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.editedItem
import io.github.locngu.beforeyoubuy.core.domain.wishlist.waitChange
import io.github.locngu.beforeyoubuy.feature.capture.ItemFormActions
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the edit screen shows (brief §6.3).
 *
 * @property form null until the item and the UI's locale are loaded; then filled with the stored values.
 * @property gone the item was ended or deleted meanwhile, so there's nothing to edit.
 * @property waitNote how saving would change the wait, shown under the price ("Waiting period extended to …").
 * @property conflict the last save found the item changed meanwhile and wrote nothing.
 */
data class ItemEditUiState(
    val form: CaptureForm? = null,
    val gone: Boolean = false,
    val showNameMissing: Boolean = false,
    val priceError: MoneyParseResult? = null,
    val canSave: Boolean = false,
    val currency: String? = null,
    val today: LocalDate? = null,
    val waitNote: WaitNote? = null,
    val conflict: Boolean = false
)

/** How saving would change the wait, with the end date in the device's zone (from the injected clock). */
sealed interface WaitNote {
    data class Extended(val endsOn: LocalDate) : WaitNote

    data class Starts(val endsOn: LocalDate) : WaitNote
}

/** What the user can do on the edit screen. */
interface ItemEditActions : ItemFormActions {
    fun save()
}

/**
 * Edits one item: name, price, link, kind, category, notes, target date (brief §6.3). The rules are `editedItem`
 * and `waitChange` in `:core:domain`: a lower price never shortens the wait, a higher one can extend it (ADR-0005).
 * Leaving without saving discards the changes.
 */
@HiltViewModel(assistedFactory = ItemEditViewModel.Factory::class)
class ItemEditViewModel @AssistedInject constructor(
    @Assisted itemId: Long,
    private val items: ItemRepository,
    policies: PolicyRepository,
    private val clock: Clock
) : ViewModel(),
    ItemEditActions {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): ItemEditViewModel
    }

    private val id = ItemId(itemId)
    private val editor = MutableStateFlow(Editor())
    private val locale = MutableStateFlow<Locale?>(null)
    private val stored: StateFlow<Loaded?> =
        items.observeById(id).map(::Loaded).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val policy: StateFlow<Policy?> = policies.policy.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val savedEvents = Channel<Unit>(Channel.BUFFERED)

    val uiState: StateFlow<ItemEditUiState> =
        combine(
            editor,
            stored,
            policy,
            locale,
            ::uiStateOf
        ).stateIn(viewModelScope, SharingStarted.Eagerly, ItemEditUiState())

    /** One event per stored edit; the screen closes. */
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    init {
        // Fill the form once, from the item as first loaded; later changes to the item don't overwrite typing.
        viewModelScope.launch {
            val (loaded, locale) = combine(stored, locale) { s, l -> s to l }.first { (s, l) -> s != null && l != null }
            val item = loaded!!.item?.takeIf { it.status == ItemStatus.ACTIVE } ?: return@launch
            editor.update { if (it.form == null) it.copy(form = editFormOf(item, locale!!), base = item) else it }
        }
    }

    /** The UI's locale, which decides how the price is shown and read (`1.234,56` vs `1,234.56`, ADR-0027). */
    fun setLocale(locale: Locale) {
        this.locale.value = locale
    }

    override fun onNameChange(name: String) = editForm { it.copy(name = name) }

    override fun onPriceChange(price: String) = editForm { it.copy(price = price) }

    override fun onLinkChange(link: String) = editForm { it.copy(link = link) }

    override fun onNeedChange(need: Boolean) = editForm { it.copy(kind = if (need) ItemKind.NEED else ItemKind.WANT) }

    override fun onCategoryChange(category: String) = editForm { it.copy(category = category) }

    override fun onNotesChange(notes: String) = editForm { it.copy(notes = notes) }

    override fun onTargetDateChange(date: LocalDate?) = editForm { it.copy(targetDate = date) }

    /**
     * Stores the edit and emits [saved]. It's written only if the item is still as it was when the form was filled
     * (or when the last conflict was shown): a change made elsewhere meanwhile (e.g. "Mark as need") is never
     * silently undone. Then nothing is written and [ItemEditUiState.conflict] says so; saving again applies the
     * form to the newer item.
     */
    override fun save() {
        val current = editor.value
        val form = current.form ?: return
        val item = current.base ?: return
        val policy = policy.value ?: return
        val locale = locale.value ?: return
        if (current.saving || !canEdit(item, form, policy, locale)) return
        editor.value = current.copy(saving = true, conflict = false)
        viewModelScope.launch {
            val written = try {
                items.update(item, editedItem(item, form, policy, locale, clock.instant(), clock.zone))
            } finally {
                editor.update { it.copy(saving = false) }
            }
            if (written) {
                savedEvents.send(Unit)
            } else {
                editor.update { it.copy(conflict = true, base = stored.value?.item ?: it.base) }
            }
        }
    }

    private fun editForm(change: (CaptureForm) -> CaptureForm) = editor.update {
        if (it.saving || it.form == null) it else it.copy(form = change(it.form))
    }

    private fun canEdit(item: Item, form: CaptureForm, policy: Policy, locale: Locale): Boolean =
        item.status == ItemStatus.ACTIVE &&
            // Only while a currency change is being written could they differ; wait for it to finish.
            item.price.currency == policy.currency &&
            checkCapture(form, policy.currency, locale).isValid

    private fun uiStateOf(editor: Editor, stored: Loaded?, policy: Policy?, locale: Locale?): ItemEditUiState {
        val latest = stored?.item
        if (stored != null &&
            (latest == null || latest.status != ItemStatus.ACTIVE)
        ) {
            return ItemEditUiState(gone = true)
        }
        // What saving is checked against, so the note says what saving would do.
        val item = editor.base
        val form = editor.form
        if (form == null || item == null || policy == null || locale == null) return ItemEditUiState()
        val check = checkCapture(form, policy.currency, locale)
        val editable = canEdit(item, form, policy, locale)
        val now = clock.instant()
        return ItemEditUiState(
            form = form,
            showNameMissing = check.nameMissing,
            priceError = check.price.takeIf { it !is MoneyParseResult.Success },
            canSave = editable && !editor.saving,
            currency = policy.currency,
            today = clock.today(),
            waitNote = if (editable) {
                waitNoteOf(
                    waitChange(item, editedItem(item, form, policy, locale, now, clock.zone), now)
                )
            } else {
                null
            },
            conflict = editor.conflict
        )
    }

    private fun waitNoteOf(change: WaitChange): WaitNote? = when (change) {
        WaitChange.None -> null
        is WaitChange.Extended -> WaitNote.Extended(change.endsAt.atZone(clock.zone).toLocalDate())
        is WaitChange.Starts -> WaitNote.Starts(change.endsAt.atZone(clock.zone).toLocalDate())
    }

    /** The item as observed; [item] null once it no longer exists. */
    private data class Loaded(val item: Item?)

    /**
     * The form's own state. Unlike quick add, every field starts filled, so errors show as soon as they occur.
     * [base] is the item the form is saved against: as first loaded, then as seen when a conflict was shown.
     */
    private data class Editor(
        val form: CaptureForm? = null,
        val base: Item? = null,
        val saving: Boolean = false,
        val conflict: Boolean = false
    )
}

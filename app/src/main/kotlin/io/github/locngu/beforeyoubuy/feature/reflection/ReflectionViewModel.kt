package io.github.locngu.beforeyoubuy.feature.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionForm
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionPage
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionStep
import io.github.locngu.beforeyoubuy.core.domain.reflection.reflectionFormOf
import io.github.locngu.beforeyoubuy.core.domain.reflection.reflectionOf
import io.github.locngu.beforeyoubuy.core.domain.reflection.reflectionPageOf
import io.github.locngu.beforeyoubuy.core.domain.reflection.startStep
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the reflection flow shows (brief §6.4).
 *
 * @property form the answers as typed; null until loaded and while [gone].
 * @property page the question on screen, whether it's required and what the forward button does; null with [form].
 * @property gone the item was ended or deleted meanwhile, so there's nothing to reflect on.
 */
data class ReflectionUiState(
    val itemName: String? = null,
    val form: ReflectionForm? = null,
    val page: ReflectionPage? = null,
    val gone: Boolean = false
)

/** What the user can do in the reflection flow. */
interface ReflectionActions {
    fun onWhyChange(why: String)

    fun onUsageChange(usage: UsageFrequency)

    fun onReasonChange(index: Int, reason: String)

    fun onAddReason()

    fun onRemoveReason(index: Int)

    fun onProblemSolvedChange(problemSolved: String)

    fun onCurrentAlternativeChange(currentAlternative: String)

    fun onReplacesChange(replaces: String)

    fun onWhyNowChange(whyNow: String)

    /** Chooses [choice], or clears it if it was chosen: the question is optional. */
    fun onUpgradeOrNewTap(choice: UpgradeOrNew)

    /** To the next question; on the last one, finishes. A required question needs its answer first. */
    fun next()

    /** To the previous question. */
    fun back()

    /** Leaves the flow at once; every answer given so far stays. */
    fun close()
}

/**
 * The reflection questions for one item, one per step (brief §4.3, §6.4, ADR-0009). The steps, what counts as an
 * answer and where the flow opens come from `:core:domain` `reflection/`.
 *
 * Every change is stored as it's made (ADR-0039), so leaving at any point keeps the answers given so far: a write
 * that has started finishes, with the newest answers, even if the screen is closed meanwhile. Storing answers never
 * touches the item: its cooling-off stays as it is. Whether it's reflected, and so its stage, is derived from the
 * stored answers by `isReflected`.
 */
@HiltViewModel(assistedFactory = ReflectionViewModel.Factory::class)
class ReflectionViewModel @AssistedInject constructor(@Assisted itemId: Long, private val items: ItemRepository) :
    ViewModel(),
    ReflectionActions {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): ReflectionViewModel
    }

    private val id = ItemId(itemId)
    private val editor = MutableStateFlow<Editor?>(null)
    private val stored: StateFlow<Loaded?> =
        items.observeById(id).map(::Loaded).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val finishedEvents = Channel<Unit>(Channel.BUFFERED)

    // One write at a time, each of the newest answers: the last write always stores the last change.
    private val writeLock = Mutex()
    private var written: Reflection? = null
    private var finishing = false

    val uiState: StateFlow<ReflectionUiState> =
        combine(editor, stored, ::uiStateOf).stateIn(viewModelScope, SharingStarted.Eagerly, ReflectionUiState())

    /** One event when the flow is done or closed, once the answers are stored; the screen closes. */
    val finished: Flow<Unit> = finishedEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            // The form is filled once, from the answers as first loaded; later changes don't overwrite typing. Item
            // and answers load together, so the first question shows after one round trip.
            val (loaded, reflection) = combine(stored.filterNotNull(), items.observeReflection(id), ::Pair).first()
            if (loaded.item?.status != ItemStatus.ACTIVE) return@launch
            val form = reflectionFormOf(reflection)
            written = reflectionOf(form)
            editor.value = Editor(form, startStep(reflection, loaded.item.kind))
            // A StateFlow drops states it can't deliver in time: while a write runs, only the newest change waits.
            editor.collect { writeLatest() }
        }
    }

    override fun onWhyChange(why: String) = editForm { it.copy(why = why) }

    override fun onUsageChange(usage: UsageFrequency) = editForm { it.copy(usageFrequency = usage) }

    override fun onReasonChange(index: Int, reason: String) = editForm { it.withReason(index, reason) }

    override fun onAddReason() = editForm { it.withNewReason() }

    override fun onRemoveReason(index: Int) = editForm { it.withoutReason(index) }

    override fun onProblemSolvedChange(problemSolved: String) = editForm { it.copy(problemSolved = problemSolved) }

    override fun onCurrentAlternativeChange(currentAlternative: String) =
        editForm { it.copy(currentAlternative = currentAlternative) }

    override fun onReplacesChange(replaces: String) = editForm { it.copy(replaces = replaces) }

    override fun onWhyNowChange(whyNow: String) = editForm { it.copy(whyNow = whyNow) }

    override fun onUpgradeOrNewTap(choice: UpgradeOrNew) = editForm { it.withUpgradeOrNewTapped(choice) }

    override fun next() {
        val current = editor.value ?: return
        val kind = stored.value?.item?.kind ?: return
        if (!reflectionPageOf(current.step, current.form, kind).canGoOn) return
        val next = current.step.next
        if (next == null) finish() else editor.value = current.copy(step = next)
    }

    override fun back() = editor.update { current -> current?.copy(step = current.step.previous ?: current.step) }

    override fun close() = finish()

    /** Stores the newest answers, waiting for a write still running, then says the flow is done. Only once. */
    private fun finish() {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            writeLatest()
            finishedEvents.send(Unit)
        }
    }

    /**
     * Writes until the stored answers are the newest ones. Not cancellable once started: back pops the screen and
     * clears this view model at once, and the answers typed just before must still be stored.
     */
    private suspend fun writeLatest() = writeLock.withLock {
        withContext(NonCancellable) {
            while (true) {
                val reflection = editor.value?.form?.let(::reflectionOf) ?: break
                if (reflection == written) break
                // False only once the item is deleted; the screen shows it's gone and nothing more is written.
                if (!items.setReflection(id, reflection)) break
                written = reflection
            }
        }
    }

    private fun editForm(change: (ReflectionForm) -> ReflectionForm) =
        editor.update { current -> current?.copy(form = change(current.form)) }

    private fun uiStateOf(editor: Editor?, stored: Loaded?): ReflectionUiState {
        val item = stored?.item
        if (stored != null && (item == null || item.status != ItemStatus.ACTIVE)) return ReflectionUiState(gone = true)
        if (editor == null || item == null) return ReflectionUiState()
        return ReflectionUiState(
            itemName = item.name,
            form = editor.form,
            page = reflectionPageOf(editor.step, editor.form, item.kind)
        )
    }

    /** The item as observed; [item] null once it no longer exists. */
    private data class Loaded(val item: Item?)

    /** The answers as typed and the question on screen. */
    private data class Editor(val form: ReflectionForm, val step: ReflectionStep)
}

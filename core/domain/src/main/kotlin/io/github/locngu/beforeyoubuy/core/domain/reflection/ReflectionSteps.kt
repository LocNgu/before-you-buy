package io.github.locngu.beforeyoubuy.core.domain.reflection

import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency

/**
 * The reflection questions, one per step, in the order the flow asks them (brief §4.3, §6.4, ADR-0009). The
 * [required] ones are what [io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected] checks for Ready, and a
 * want can't skip them ([isRequired]); the others can always be skipped.
 */
enum class ReflectionStep(val required: Boolean) {
    WHY(required = true),
    USAGE(required = true),
    REASONS_AGAINST(required = true),
    PROBLEM_SOLVED(required = false),
    CURRENT_ALTERNATIVE(required = false),
    REPLACES(required = false),
    WHY_NOW(required = false),
    UPGRADE_OR_NEW(required = false);

    /** The step after this one, or null on the last. */
    val next: ReflectionStep? get() = entries.getOrNull(ordinal + 1)

    /** The step before this one, or null on the first. */
    val previous: ReflectionStep? get() = entries.getOrNull(ordinal - 1)
}

/**
 * The reflection answers as the user typed them. [reasonsAgainst] holds one text field per reason and is never
 * empty: without reasons it's one empty field. Store it as [reflectionOf].
 */
data class ReflectionForm(
    val why: String = "",
    val usageFrequency: UsageFrequency? = null,
    val reasonsAgainst: List<String> = listOf(""),
    val problemSolved: String = "",
    val currentAlternative: String = "",
    val replaces: String = "",
    val whyNow: String = "",
    val upgradeOrNew: UpgradeOrNew? = null
) {
    init {
        require(reasonsAgainst.isNotEmpty()) { "There's always a field for a reason against" }
    }

    /** "Add another reason" is offered once every field holds a reason, so empty fields don't pile up. */
    val canAddReason: Boolean get() = reasonsAgainst.none { it.isBlank() }

    /** The reason at [index] changed to [reason]. */
    fun withReason(index: Int, reason: String): ReflectionForm =
        copy(reasonsAgainst = reasonsAgainst.mapIndexed { i, old -> if (i == index) reason else old })

    /** An empty field for another reason at the end, if [canAddReason]; otherwise unchanged. */
    fun withNewReason(): ReflectionForm = if (canAddReason) copy(reasonsAgainst = reasonsAgainst + "") else this

    /** The optional "upgrade or new" after tapping [choice]: chosen, or cleared when it was already the answer. */
    fun withUpgradeOrNewTapped(choice: UpgradeOrNew): ReflectionForm =
        copy(upgradeOrNew = choice.takeIf { it != upgradeOrNew })

    /** Without the reason at [index]. Removing the only field empties it instead: there's always one. */
    fun withoutReason(index: Int): ReflectionForm {
        if (index !in reasonsAgainst.indices) return this
        val rest = reasonsAgainst.filterIndexed { i, _ -> i != index }
        return copy(reasonsAgainst = rest.ifEmpty { listOf("") })
    }
}

/** The form for the answers stored so far, or an empty one before any (ADR-0009: reflection comes after capture). */
fun reflectionFormOf(reflection: Reflection?): ReflectionForm {
    if (reflection == null) return ReflectionForm()
    return ReflectionForm(
        why = reflection.why.orEmpty(),
        usageFrequency = reflection.usageFrequency,
        reasonsAgainst = reflection.reasonsAgainst.ifEmpty { listOf("") },
        problemSolved = reflection.problemSolved.orEmpty(),
        currentAlternative = reflection.currentAlternative.orEmpty(),
        replaces = reflection.replaces.orEmpty(),
        whyNow = reflection.whyNow.orEmpty(),
        upgradeOrNew = reflection.upgradeOrNew
    )
}

/** The answers to store for [form]: text trimmed, blank answers left out, blank reasons against dropped. */
fun reflectionOf(form: ReflectionForm): Reflection = Reflection(
    why = form.why.answer(),
    usageFrequency = form.usageFrequency,
    reasonsAgainst = form.reasonsAgainst.mapNotNull { it.answer() },
    problemSolved = form.problemSolved.answer(),
    currentAlternative = form.currentAlternative.answer(),
    replaces = form.replaces.answer(),
    whyNow = form.whyNow.answer(),
    upgradeOrNew = form.upgradeOrNew
)

/**
 * Whether [reflection] answers [step]: non-blank text, a choice, or at least one non-blank reason against. A want is
 * reflected once every [ReflectionStep.required] step is answered
 * ([io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected]).
 */
fun isAnswered(step: ReflectionStep, reflection: Reflection): Boolean = when (step) {
    ReflectionStep.WHY -> !reflection.why.isNullOrBlank()
    ReflectionStep.USAGE -> reflection.usageFrequency != null
    ReflectionStep.REASONS_AGAINST -> reflection.reasonsAgainst.any { it.isNotBlank() }
    ReflectionStep.PROBLEM_SOLVED -> !reflection.problemSolved.isNullOrBlank()
    ReflectionStep.CURRENT_ALTERNATIVE -> !reflection.currentAlternative.isNullOrBlank()
    ReflectionStep.REPLACES -> !reflection.replaces.isNullOrBlank()
    ReflectionStep.WHY_NOW -> !reflection.whyNow.isNullOrBlank()
    ReflectionStep.UPGRADE_OR_NEW -> reflection.upgradeOrNew != null
}

/**
 * Whether [step] must be answered before moving on, for an item of [kind]. A need skips reflection (ADR-0008), so
 * its answers are all optional.
 */
fun isRequired(step: ReflectionStep, kind: ItemKind): Boolean = step.required && kind == ItemKind.WANT

/** What the forward button does on a step (ADR-0039). */
enum class Onward {
    /** To the next question; disabled while a required one is unanswered. */
    NEXT,

    /** Past an optional question left unanswered. */
    SKIP,

    /** The last question: the flow is done. */
    DONE
}

/**
 * What the flow shows on [step]: whether it's required for this item, whether it may move on, and what the forward
 * button does. See [reflectionPageOf].
 */
data class ReflectionPage(val step: ReflectionStep, val required: Boolean, val canGoOn: Boolean, val onward: Onward)

/**
 * The page for [step] with [form], for an item of [kind]: a required question needs an answer before moving on, an
 * unanswered optional one is skipped, and the last one is done.
 */
fun reflectionPageOf(step: ReflectionStep, form: ReflectionForm, kind: ItemKind): ReflectionPage {
    val required = isRequired(step, kind)
    val answered = isAnswered(step, reflectionOf(form))
    val onward = when {
        step.next == null -> Onward.DONE
        !answered && !required -> Onward.SKIP
        else -> Onward.NEXT
    }
    return ReflectionPage(step = step, required = required, canGoOn = answered || !required, onward = onward)
}

/**
 * Where the flow opens for an item of [kind] (ADR-0039): at the first required question still unanswered, so coming
 * back continues where the required answers stop. Once they're all there (and always for a need), at the first
 * question, to go through the answers again.
 */
fun startStep(reflection: Reflection?, kind: ItemKind): ReflectionStep {
    val answers = reflection ?: Reflection()
    return ReflectionStep.entries.firstOrNull { isRequired(it, kind) && !isAnswered(it, answers) }
        ?: ReflectionStep.entries.first()
}

private fun String.answer(): String? = trim().ifEmpty { null }

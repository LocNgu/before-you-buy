package io.github.locngu.beforeyoubuy.core.domain.reflection

import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionStepsTest {
    private val all = Reflection(
        why = "Better photos on hikes",
        usageFrequency = UsageFrequency.WEEKLY,
        reasonsAgainst = listOf("Heavy", "My phone is fine"),
        problemSolved = "Blurry photos",
        currentAlternative = "My phone",
        replaces = "Nothing",
        whyNow = "Trip in spring",
        upgradeOrNew = UpgradeOrNew.NEW
    )

    @Test
    fun `the required questions come first, in the brief's order`() {
        assertEquals(
            listOf(ReflectionStep.WHY, ReflectionStep.USAGE, ReflectionStep.REASONS_AGAINST),
            ReflectionStep.entries.take(3)
        )
        assertEquals(ReflectionStep.entries.take(3), ReflectionStep.entries.filter { it.required })
    }

    @Test
    fun `steps know their neighbours, and the ends have none`() {
        assertNull(ReflectionStep.WHY.previous)
        assertEquals(ReflectionStep.USAGE, ReflectionStep.WHY.next)
        assertEquals(ReflectionStep.WHY, ReflectionStep.USAGE.previous)
        assertNull(ReflectionStep.UPGRADE_OR_NEW.next)
    }

    @Test
    fun `a new form has one empty reason field`() {
        val form = reflectionFormOf(null)

        assertEquals(listOf(""), form.reasonsAgainst)
        assertEquals(Reflection(), reflectionOf(form))
    }

    @Test
    fun `stored answers fill the form and come back unchanged`() {
        val form = reflectionFormOf(all)

        assertEquals("Better photos on hikes", form.why)
        assertEquals(listOf("Heavy", "My phone is fine"), form.reasonsAgainst)
        assertEquals(all, reflectionOf(form))
    }

    @Test
    fun `answers without reasons against still get one field`() {
        assertEquals(listOf(""), reflectionFormOf(Reflection(why = "Hikes")).reasonsAgainst)
    }

    @Test
    fun `stored answers are trimmed, and blank ones left out`() {
        val form = ReflectionForm(
            why = "  Hikes \n",
            reasonsAgainst = listOf(" Heavy ", "   ", ""),
            problemSolved = " ",
            whyNow = "\tTrip "
        )

        assertEquals(
            Reflection(why = "Hikes", reasonsAgainst = listOf("Heavy"), whyNow = "Trip"),
            reflectionOf(form)
        )
    }

    @Test
    fun `a form without reason fields can't exist`() {
        assertThrows(IllegalArgumentException::class.java) { ReflectionForm(reasonsAgainst = emptyList()) }
    }

    @Test
    fun `another reason can be added once every field holds one`() {
        val empty = ReflectionForm()
        assertFalse(empty.canAddReason)
        assertSame(empty, empty.withNewReason())

        val one = empty.withReason(0, "Heavy")
        assertTrue(one.canAddReason)
        val two = one.withNewReason()
        assertEquals(listOf("Heavy", ""), two.reasonsAgainst)
        assertFalse(two.canAddReason)

        assertEquals(listOf("Heavy", "Price"), two.withReason(1, "Price").reasonsAgainst)
    }

    @Test
    fun `removing a reason keeps the others, and the only field is emptied instead`() {
        val form = ReflectionForm(reasonsAgainst = listOf("Heavy", "Price", "Space"))

        assertEquals(listOf("Heavy", "Space"), form.withoutReason(1).reasonsAgainst)
        assertEquals(listOf(""), ReflectionForm(reasonsAgainst = listOf("Heavy")).withoutReason(0).reasonsAgainst)
        assertSame(form, form.withoutReason(3))
    }

    @Test
    fun `the optional choice is chosen by a tap and cleared by tapping it again`() {
        val upgrade = ReflectionForm().withUpgradeOrNewTapped(UpgradeOrNew.UPGRADE)
        assertEquals(UpgradeOrNew.UPGRADE, upgrade.upgradeOrNew)

        assertEquals(UpgradeOrNew.NEW, upgrade.withUpgradeOrNewTapped(UpgradeOrNew.NEW).upgradeOrNew)
        assertNull(upgrade.withUpgradeOrNewTapped(UpgradeOrNew.UPGRADE).upgradeOrNew)
    }

    @Test
    fun `a want can't skip required questions, optional ones are skipped`() {
        val empty = ReflectionForm()
        ReflectionStep.entries.forEach { step ->
            val page = reflectionPageOf(step, empty, ItemKind.WANT)
            assertEquals(step.name, step.required, page.required)
            assertEquals(step.name, !step.required, page.canGoOn)
        }
        assertEquals(Onward.NEXT, reflectionPageOf(ReflectionStep.WHY, empty, ItemKind.WANT).onward)
        assertEquals(Onward.SKIP, reflectionPageOf(ReflectionStep.PROBLEM_SOLVED, empty, ItemKind.WANT).onward)
        assertEquals(Onward.DONE, reflectionPageOf(ReflectionStep.UPGRADE_OR_NEW, empty, ItemKind.WANT).onward)
    }

    @Test
    fun `answered questions go on with next, and the last one is done`() {
        val answered = reflectionFormOf(all)
        ReflectionStep.entries.forEach { step ->
            val page = reflectionPageOf(step, answered, ItemKind.WANT)
            assertTrue(step.name, page.canGoOn)
            assertEquals(step.name, if (step.next == null) Onward.DONE else Onward.NEXT, page.onward)
        }
    }

    @Test
    fun `a need has no required questions`() {
        ReflectionStep.entries.forEach { step ->
            val page = reflectionPageOf(step, ReflectionForm(), ItemKind.NEED)
            assertFalse(step.name, page.required)
            assertTrue(step.name, page.canGoOn)
        }
        assertEquals(Onward.SKIP, reflectionPageOf(ReflectionStep.WHY, ReflectionForm(), ItemKind.NEED).onward)
    }

    @Test
    fun `blank text doesn't answer a question`() {
        fun canGoOn(step: ReflectionStep, form: ReflectionForm) = reflectionPageOf(step, form, ItemKind.WANT).canGoOn
        assertFalse(canGoOn(ReflectionStep.WHY, ReflectionForm(why = "   ")))
        assertFalse(canGoOn(ReflectionStep.REASONS_AGAINST, ReflectionForm(reasonsAgainst = listOf(" ", ""))))
        assertTrue(canGoOn(ReflectionStep.REASONS_AGAINST, ReflectionForm(reasonsAgainst = listOf("", "Heavy"))))
        assertTrue(canGoOn(ReflectionStep.USAGE, ReflectionForm(usageFrequency = UsageFrequency.ONCE)))
        assertEquals(
            Onward.SKIP,
            reflectionPageOf(ReflectionStep.WHY_NOW, ReflectionForm(whyNow = " "), ItemKind.WANT).onward
        )
    }

    @Test
    fun `each question is answered by its own field`() {
        assertEquals(
            ReflectionStep.entries.toList(),
            ReflectionStep.entries.filter { isAnswered(it, all) }
        )
        assertEquals(emptyList<ReflectionStep>(), ReflectionStep.entries.filter { isAnswered(it, Reflection()) })
        assertEquals(
            listOf(ReflectionStep.WHY_NOW, ReflectionStep.UPGRADE_OR_NEW),
            ReflectionStep.entries.filter {
                isAnswered(it, Reflection(whyNow = "Trip", upgradeOrNew = UpgradeOrNew.UPGRADE))
            }
        )
    }

    @Test
    fun `the flow opens at the first required question still missing`() {
        fun start(reflection: Reflection?) = startStep(reflection, ItemKind.WANT)
        assertEquals(ReflectionStep.WHY, start(null))
        assertEquals(ReflectionStep.WHY, start(Reflection(usageFrequency = UsageFrequency.DAILY)))
        assertEquals(ReflectionStep.USAGE, start(Reflection(why = "Hikes", reasonsAgainst = listOf("Heavy"))))
        assertEquals(
            ReflectionStep.REASONS_AGAINST,
            start(Reflection(why = "Hikes", usageFrequency = UsageFrequency.DAILY, problemSolved = "Blur"))
        )
    }

    @Test
    fun `once the required answers are there it opens at the first question`() {
        assertEquals(ReflectionStep.WHY, startStep(COMPLETE_REFLECTION, ItemKind.WANT))
        assertEquals(ReflectionStep.WHY, startStep(all, ItemKind.WANT))
    }

    @Test
    fun `a need always opens at the first question`() {
        assertEquals(ReflectionStep.WHY, startStep(Reflection(why = "Fridge broke"), ItemKind.NEED))
    }
}

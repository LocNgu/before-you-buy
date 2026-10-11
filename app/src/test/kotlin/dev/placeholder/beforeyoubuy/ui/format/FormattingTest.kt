package dev.placeholder.beforeyoubuy.ui.format

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class FormattingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 10, 3)

    @Test
    fun `dates use the locale's medium style`() {
        assertEquals("03.10.2026", formatDate(date, Locale.forLanguageTag("de-DE")))
        assertEquals("3 Oct 2026", formatDate(date, Locale.forLanguageTag("en-GB")))
        assertEquals("Oct 3, 2026", formatDate(date, Locale.forLanguageTag("en-US")))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `relative strings in English`() {
        assertEquals(
            listOf("Today", "Yesterday", "2 days ago", "61 days ago", "1 day", "61 days", "Oct 3, 2026"),
            render()
        )
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `relative strings in German`() {
        assertEquals(
            listOf("Heute", "Gestern", "vor 2 Tagen", "vor 61 Tagen", "1 Tag", "61 Tage", "03.10.2026"),
            render()
        )
    }

    private fun render(): List<String> {
        var strings = emptyList<String>()
        composeRule.setContent {
            strings =
                listOf(
                    daysAgo(0),
                    daysAgo(1),
                    daysAgo(2),
                    daysAgo(61),
                    durationInDays(1),
                    durationInDays(61),
                    formattedDate(date)
                )
        }
        composeRule.waitForIdle()
        return strings
    }
}

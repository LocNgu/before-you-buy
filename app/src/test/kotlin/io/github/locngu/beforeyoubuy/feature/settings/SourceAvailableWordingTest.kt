package io.github.locngu.beforeyoubuy.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app is source available, not open source (ADR-0004): no string in either language says otherwise, and About
 * says what it is.
 */
@RunWith(AndroidJUnit4::class)
class SourceAvailableWordingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    // "Open source", "open-source", "Open-Source-…", German "quelloffen".
    private val forbidden = Regex("""open[\s -]*source|quelloffen""", RegexOption.IGNORE_CASE)

    private fun everyString(): Map<String, String> = R.string::class.java.fields.associate { field ->
        field.name to context.getString(field.getInt(null))
    }

    private fun assertNoOpenSource() {
        val strings = everyString()
        assertTrue("No strings read", strings.size > 100)
        val offending = strings.filterValues { forbidden.containsMatchIn(it) }
        assertEquals(emptyMap<String, String>(), offending)
    }

    @Test
    @Config(qualifiers = "en")
    fun `no English string says open source`() {
        assertNoOpenSource()
        assertEquals(
            "Source available, free for personal and non-commercial use",
            context.getString(R.string.settings_licence)
        )
        assertEquals("PolyForm Noncommercial License 1.0.0", context.getString(R.string.settings_licence_name))
    }

    @Test
    @Config(qualifiers = "de")
    fun `no German string says open source`() {
        assertNoOpenSource()
        assertEquals(
            "Quellcode einsehbar, kostenlos für private und nicht kommerzielle Nutzung",
            context.getString(R.string.settings_licence)
        )
    }

    @Test
    fun `the licence link is the PolyForm Noncommercial licence`() {
        assertEquals("https://polyformproject.org/licenses/noncommercial/1.0.0/", LICENCE_URL)
    }
}

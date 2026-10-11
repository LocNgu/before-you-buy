package dev.placeholder.beforeyoubuy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * The system's per-app language setting offers exactly the languages the app is translated into (ADR-0016).
 * Robolectric doesn't read `android:localeConfig` from the manifest, so this reads the resource it points to.
 */
@RunWith(AndroidJUnit4::class)
class LocaleConfigTest {
    @Test
    fun `per-app language setting offers English and German`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locales = mutableListOf<String>()
        context.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    locales += parser.getAttributeValue(ANDROID_NS, "name")
                }
            }
        }
        assertEquals(listOf("en", "de"), locales)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

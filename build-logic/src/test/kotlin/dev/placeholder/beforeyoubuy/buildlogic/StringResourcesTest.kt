package dev.placeholder.beforeyoubuy.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StringResourcesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun values(
        folder: String,
        body: String,
        name: String = "strings.xml",
    ): File {
        val dir = File(temp.root, "res/$folder").apply { mkdirs() }
        return File(dir, name).apply { writeText("<resources>\n$body\n</resources>\n") }
    }

    @Test
    fun `matching strings, plurals and arrays pass`() {
        val body =
            """
            <string name="app_name">x</string>
            <plurals name="days_left"><item quantity="one">x</item><item quantity="other">x</item></plurals>
            <string-array name="tiers"><item>x</item></string-array>
            """
        val english = values("values", body)
        val german = values("values-de", body)

        assertEquals(emptyList<String>(), StringResources.parityProblems(listOf(english), listOf(german)))
    }

    @Test
    fun `a string missing in German is reported`() {
        val english = values("values", """<string name="app_name">x</string><string name="save">Save</string>""")
        val german = values("values-de", """<string name="app_name">x</string>""")

        assertEquals(
            listOf("string/save is in values/ but missing from values-de/"),
            StringResources.parityProblems(listOf(english), listOf(german)),
        )
    }

    @Test
    fun `a German-only plural is reported`() {
        val english = values("values", "")
        val german = values("values-de", """<plurals name="days"><item quantity="other">x</item></plurals>""")

        assertEquals(
            listOf("plurals/days is in values-de/ but not a translatable string in values/"),
            StringResources.parityProblems(listOf(english), listOf(german)),
        )
    }

    @Test
    fun `keys are compared across all files of a folder`() {
        val english =
            listOf(
                values("values", """<string name="a">x</string>"""),
                values("values", """<plurals name="b"><item quantity="other">x</item></plurals>""", "plurals.xml"),
            )
        val german =
            listOf(
                values("values-de", """<string name="a">x</string><plurals name="b"><item quantity="other">x</item></plurals>"""),
            )

        assertEquals(emptyList<String>(), StringResources.parityProblems(english, german))
    }

    @Test
    fun `the same name with a different type is a different key`() {
        val english = values("values", """<string name="days">x</string>""")
        val german = values("values-de", """<plurals name="days"><item quantity="other">x</item></plurals>""")

        assertEquals(
            listOf(
                "string/days is in values/ but missing from values-de/",
                "plurals/days is in values-de/ but not a translatable string in values/",
            ),
            StringResources.parityProblems(listOf(english), listOf(german)),
        )
    }

    @Test
    fun `English-only strings need no German entry but must not get one`() {
        val english = values("values", """<string name="brand" translatable="false">x</string>""")

        assertEquals(emptyList<String>(), StringResources.parityProblems(listOf(english), emptyList()))

        val german = values("values-de", """<string name="brand">x</string>""")
        assertEquals(
            listOf("string/brand is in values-de/ but not a translatable string in values/"),
            StringResources.parityProblems(listOf(english), listOf(german)),
        )
    }

    @Test
    fun `a missing values-de folder reports every string`() {
        val english = values("values", """<string name="a">x</string><string name="b">x</string>""")

        assertEquals(
            listOf("string/a is in values/ but missing from values-de/", "string/b is in values/ but missing from values-de/"),
            StringResources.parityProblems(listOf(english), emptyList()),
        )
    }

    @Test
    fun `other resource types are ignored`() {
        val english = values("values", """<color name="bg">#fff</color><dimen name="gap">4dp</dimen>""", "colors.xml")

        assertEquals(emptyList<String>(), StringResources.parityProblems(listOf(english), emptyList()))
    }
}

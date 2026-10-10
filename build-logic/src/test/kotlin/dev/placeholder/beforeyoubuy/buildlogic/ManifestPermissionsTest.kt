package dev.placeholder.beforeyoubuy.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManifestPermissionsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun manifest(body: String) = temp.newFile("AndroidManifest.xml").apply {
        writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="x">
            $body
                <application android:name=".App" />
            </manifest>
            """.trimIndent(),
        )
    }

    @Test
    fun `a manifest without INTERNET passes`() {
        val file =
            manifest(
                """
                <uses-permission android:name="android.permission.WAKE_LOCK" />
                <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
                """,
            )

        assertEquals(emptyList<RequestedPermission>(), ManifestPermissions.internet(file))
    }

    @Test
    fun `INTERNET is found in uses-permission`() {
        val file = manifest("""<uses-permission android:name="android.permission.INTERNET" />""")

        assertEquals(
            listOf(RequestedPermission("uses-permission", INTERNET_PERMISSION)),
            ManifestPermissions.internet(file),
        )
    }

    @Test
    fun `INTERNET is found in the sdk-specific permission elements`() {
        val file =
            manifest(
                """
                <uses-permission-sdk-23 android:name="android.permission.INTERNET" />
                <uses-permission-sdk-m android:name="android.permission.INTERNET" />
                """,
            )

        assertEquals(
            listOf("uses-permission-sdk-23", "uses-permission-sdk-m"),
            ManifestPermissions.internet(file).map { it.element },
        )
    }

    @Test
    fun `a name outside the android namespace is not a permission request`() {
        val file =
            manifest(
                """<uses-permission xmlns:other="urn:other" other:name="android.permission.INTERNET" />""",
            )

        assertEquals(emptyList<RequestedPermission>(), ManifestPermissions.internet(file))
    }

    @Test
    fun `the finding reads like the manifest line`() {
        assertEquals(
            """<uses-permission android:name="android.permission.INTERNET"/>""",
            RequestedPermission("uses-permission", INTERNET_PERMISSION).toString(),
        )
    }
}

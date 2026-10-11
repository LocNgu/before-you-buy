package io.github.locngu.beforeyoubuy.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseSigningTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun complete(storeFile: String) = mapOf(
        ReleaseSigning.STORE_FILE to storeFile,
        ReleaseSigning.STORE_PASSWORD to "store-secret",
        ReleaseSigning.KEY_ALIAS to "release",
        ReleaseSigning.KEY_PASSWORD to "key-secret"
    )

    @Test
    fun `no variables means an unsigned build`() {
        assertNull(ReleaseSigning.fromEnvironment(temp.root) { null })
    }

    @Test
    fun `empty variables count as not set`() {
        assertNull(ReleaseSigning.fromEnvironment(temp.root) { "" })
    }

    @Test
    fun `all variables give the signing config`() {
        val keystore = temp.newFile("release.jks")

        val signing = ReleaseSigning.fromEnvironment(temp.root, complete(keystore.path)::get)!!

        assertEquals(keystore, signing.storeFile)
        assertEquals("store-secret", signing.storePassword)
        assertEquals("release", signing.keyAlias)
        assertEquals("key-secret", signing.keyPassword)
    }

    @Test
    fun `a relative keystore path is resolved against the base directory`() {
        val keystore = temp.newFile("release.jks")

        val signing = ReleaseSigning.fromEnvironment(temp.root, complete("release.jks")::get)!!

        assertEquals(keystore, signing.storeFile)
    }

    @Test
    fun `a partial configuration fails and names what's missing`() {
        val keystore = temp.newFile("release.jks")
        val env = complete(keystore.path) - ReleaseSigning.KEY_PASSWORD

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                ReleaseSigning.fromEnvironment(temp.root, env::get)
            }

        assertTrue(error.message!!, error.message!!.contains("${ReleaseSigning.KEY_PASSWORD} not set"))
    }

    @Test
    fun `a missing keystore file fails`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseSigning.fromEnvironment(temp.root, complete("missing.jks")::get)
        }
    }

    @Test
    fun `passwords never appear in messages`() {
        val keystore = temp.newFile("release.jks")
        val signing = ReleaseSigning.fromEnvironment(temp.root, complete(keystore.path)::get)!!
        val partial =
            assertThrows(IllegalArgumentException::class.java) {
                ReleaseSigning.fromEnvironment(temp.root, (complete(keystore.path) - ReleaseSigning.KEY_ALIAS)::get)
            }

        listOf(signing.toString(), partial.message!!).forEach { text ->
            assertFalse(text, text.contains("store-secret") || text.contains("key-secret"))
        }
    }
}

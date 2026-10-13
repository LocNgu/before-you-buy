package io.github.locngu.beforeyoubuy.core.data.testing

import org.junit.Assert.assertTrue

/** `assertThrows(IllegalArgumentException::class.java)` for suspending code. */
internal suspend fun assertThrowsIllegalArgument(block: suspend () -> Unit) {
    val thrown = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected IllegalArgumentException, was $thrown", thrown is IllegalArgumentException)
}

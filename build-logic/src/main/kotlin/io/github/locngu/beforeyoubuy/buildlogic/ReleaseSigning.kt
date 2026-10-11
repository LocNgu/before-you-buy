package io.github.locngu.beforeyoubuy.buildlogic

import java.io.File

/**
 * The release key, read from environment variables (ADR-0027). It is never committed: CI decodes it from GitHub
 * Actions secrets, and a local build only has it if the owner sets the variables.
 */
internal class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
) {
    // No passwords in logs or error messages.
    override fun toString() = "ReleaseSigning(storeFile=$storeFile, keyAlias=$keyAlias)"

    companion object {
        const val STORE_FILE = "RELEASE_KEYSTORE_PATH"
        const val STORE_PASSWORD = "RELEASE_KEYSTORE_PASSWORD"
        const val KEY_ALIAS = "RELEASE_KEY_ALIAS"
        const val KEY_PASSWORD = "RELEASE_KEY_PASSWORD"
        val VARIABLES = listOf(STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

        /**
         * The signing config from [environment], or null if none of [VARIABLES] is set (the release build stays
         * unsigned). Fails if only some are set, or if the keystore isn't a file. A relative keystore path is
         * resolved against [baseDirectory].
         */
        fun fromEnvironment(baseDirectory: File, environment: (String) -> String?): ReleaseSigning? {
            val values = VARIABLES.associateWith { environment(it)?.takeIf(String::isNotEmpty) }
            if (values.values.all { it == null }) return null
            val missing = values.filterValues { it == null }.keys
            require(missing.isEmpty()) {
                "Release signing is only partly configured: ${missing.joinToString()} not set. Set all of " +
                    "${VARIABLES.joinToString()} to sign the release build, or none to build it unsigned " +
                    "(docs/releasing.md)."
            }
            val storeFile = baseDirectory.resolve(values.getValue(STORE_FILE)!!)
            require(storeFile.isFile) { "$STORE_FILE is set, but $storeFile isn't a file." }
            return ReleaseSigning(
                storeFile = storeFile,
                storePassword = values.getValue(STORE_PASSWORD)!!,
                keyAlias = values.getValue(KEY_ALIAS)!!,
                keyPassword = values.getValue(KEY_PASSWORD)!!
            )
        }
    }
}

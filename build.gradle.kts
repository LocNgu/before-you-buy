plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()

    // Prune build output while walking, so Spotless never reads directories another task is writing.
    fun sources(pattern: String) = fileTree(rootDir) {
        include(pattern)
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".git/**", ".claude/**")
    }
    kotlin {
        target(sources("**/*.kt"))
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target(sources("**/*.gradle.kts"))
        ktlint(ktlintVersion)
    }
}

// The one pre-push gate: exactly what CI runs (docs/architecture.md).
tasks.register("verify") {
    group = "verification"
    description = "Formatting, Android Lint, unit tests and screenshot verification. Run before every push."
    dependsOn(
        "spotlessCheck",
        ":app:lint",
        ":core:data:lint",
        ":core:domain:lint",
        ":app:testDebugUnitTest",
        ":core:data:testDebugUnitTest",
        ":core:domain:test",
        ":app:verifyRoborazziDebug"
    )
    // Issue #9 adds the hard-constraint checks here:
    //   ":app:checkNoInternetPermission" (no INTERNET in the merged release manifest, ADR-0003)
    //   "checkStringParity" (same keys in values/ and values-de/, ADR-0016)
}

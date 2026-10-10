// Pure Kotlin/JVM: all business rules live here (ADR-0017). No Android dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

val jvmTargetVersion = libs.versions.jvmTarget.get()

java {
    sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
}

kotlin {
    compilerOptions {
        jvmTarget =
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(jvmTargetVersion)
    }
}

lint {
    abortOnError = true
    warningsAsErrors = true
}

dependencies {
    testImplementation(libs.bundles.unit.test)
}

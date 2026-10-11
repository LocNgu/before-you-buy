package io.github.locngu.beforeyoubuy.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures

/** The Gradle property that carries the release tag, e.g. `-Papp.versionTag=v1.2.3`. */
internal const val VERSION_TAG_PROPERTY = "app.versionTag"

/**
 * Release setup for the Android application (ADR-0027):
 * - `versionName`/`versionCode` from the tag in [VERSION_TAG_PROPERTY] ([AppVersion]; a dev default without it).
 * - The `release` build type is signed if the [ReleaseSigning] environment variables are set, else unsigned. Signing
 *   needs `--no-configuration-cache`, so the passwords are never written to the configuration cache on disk.
 * - `releaseNotes` writes the tag's `CHANGELOG.md` section to `build/release/notes.md`.
 */
abstract class ReleasePlugin @Inject constructor(private val buildFeatures: BuildFeatures) : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val version = AppVersion.fromTag(project.providers.gradleProperty(VERSION_TAG_PROPERTY).orNull)
            val signing =
                ReleaseSigning.fromEnvironment(project.rootDir) {
                    project.providers.environmentVariable(it).orNull
                }
            val android = project.extensions.getByType(ApplicationExtension::class.java)
            android.defaultConfig.versionCode = version.code
            android.defaultConfig.versionName = version.name
            if (signing != null) {
                if (buildFeatures.configurationCache.requested.getOrElse(false)) {
                    throw GradleException(
                        "Signing the release build needs --no-configuration-cache, so the key's passwords aren't " +
                            "stored in the configuration cache (docs/releasing.md)."
                    )
                }
                val config =
                    android.signingConfigs.create("release") {
                        storeFile = signing.storeFile
                        storePassword = signing.storePassword
                        keyAlias = signing.keyAlias
                        keyPassword = signing.keyPassword
                    }
                android.buildTypes.getByName("release").signingConfig = config
            }
            project.tasks.register("releaseNotes", ReleaseNotesTask::class.java) {
                group = "distribution"
                description = "Writes the CHANGELOG.md section of -P$VERSION_TAG_PROPERTY to build/release/notes.md."
                changelog.set(project.rootProject.layout.projectDirectory.file("CHANGELOG.md"))
                versionName.set(version.name)
                notes.set(project.layout.buildDirectory.file("release/notes.md"))
            }
        }
    }
}

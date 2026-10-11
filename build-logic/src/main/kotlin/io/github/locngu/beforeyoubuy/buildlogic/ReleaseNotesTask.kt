package io.github.locngu.beforeyoubuy.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Writes the `CHANGELOG.md` section of the version being built: the GitHub Release's notes (ADR-0029). */
abstract class ReleaseNotesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:Input
    abstract val versionName: Property<String>

    @get:OutputFile
    abstract val notes: RegularFileProperty

    @TaskAction
    fun write() {
        val version = versionName.get()
        val section =
            Changelog.section(changelog.get().asFile.readText(), version)
                ?: run {
                    val message =
                        "CHANGELOG.md has no non-empty section '## [$version]' for the release notes. Move the " +
                            "[Unreleased] entries into it before tagging (docs/releasing.md). Builds without " +
                            "-P$VERSION_TAG_PROPERTY=vX.Y.Z are version ${AppVersion.DEV.name}."
                    annotateGitHubError("No changelog section for $version", message)
                    throw GradleException(message)
                }
        notes.get().asFile.writeText("$section\n")
    }
}

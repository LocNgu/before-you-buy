package io.github.locngu.beforeyoubuy.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Fails if a merged release manifest requests `android.permission.INTERNET` (ADR-0003). */
abstract class CheckNoInternetPermissionTask : DefaultTask() {
    /** The merged manifest of every release variant. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: ConfigurableFileCollection

    /** Base for the paths shown in messages: the repository root. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** AGP's manifest merger report, which names the source of every merged permission. */
    @get:Internal
    abstract val mergerReport: RegularFileProperty

    @get:OutputFile
    abstract val result: RegularFileProperty

    @TaskAction
    fun check() {
        val manifests = mergedManifests.files.sorted()
        if (manifests.isEmpty()) {
            throw GradleException("checkNoInternetPermission found no merged release manifest to inspect.")
        }
        val base = rootDirectory.get().asFile
        val found =
            manifests.flatMap { manifest ->
                ManifestPermissions.internet(manifest).map { "  ${manifest.relativeTo(base)}: $it" }
            }
        if (found.isNotEmpty()) {
            val message =
                buildString {
                    appendLine(
                        "The release manifest requests $INTERNET_PERMISSION. This app must never do that (ADR-0003)."
                    )
                    found.forEach(::appendLine)
                    appendLine("Remove the <uses-permission>. If a dependency adds it, find which one in")
                    appendLine("  ${mergerReport.get().asFile.relativeTo(base)}")
                    append(
                        "and drop the dependency (or remove the permission with tools:node=\"remove\" if it works offline)."
                    )
                }
            annotateGitHubError("INTERNET permission in release manifest", message)
            throw GradleException(message)
        }
        result.get().asFile.writeText(
            manifests.joinToString("\n", postfix = "\n") { "OK: no $INTERNET_PERMISSION in ${it.relativeTo(base)}" }
        )
    }
}

package dev.placeholder.beforeyoubuy.buildlogic

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
import java.io.File

/** Fails if a string resource exists in `values/` but not in `values-de/`, or the other way round (ADR-0016). */
abstract class CheckStringParityTask : DefaultTask() {
    /** XML files directly in `res/values/` and `res/values-de/` of any source set. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val valuesFiles: ConfigurableFileCollection

    /** Base for the paths shown in messages. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val result: RegularFileProperty

    @TaskAction
    fun check() {
        val base = rootDirectory.get().asFile
        // res directory -> (qualifier folder name -> files)
        val byResDir: Map<File, Map<String, List<File>>> =
            valuesFiles.files
                .groupBy { it.parentFile.parentFile }
                .mapValues { (_, files) -> files.groupBy { it.parentFile.name } }
        if (byResDir.values.none { it.containsKey("values") }) {
            throw GradleException("checkStringParity found no res/values/ files to compare.")
        }
        val problems =
            byResDir.toSortedMap().flatMap { (resDir, folders) ->
                StringResources
                    .parityProblems(folders["values"].orEmpty(), folders["values-de"].orEmpty())
                    .map { "  ${resDir.relativeTo(base)}: $it" }
            }
        if (problems.isNotEmpty()) {
            val message =
                buildString {
                    appendLine("English and German strings differ. Every user-facing string needs both (ADR-0016).")
                    problems.forEach(::appendLine)
                    append("Add the missing entries, or mark an English-only string translatable=\"false\".")
                }
            annotateGitHubError("String resources: values/ and values-de/ differ", message)
            throw GradleException(message)
        }
        result.get().asFile.writeText(
            byResDir.keys.sorted().joinToString("\n", postfix = "\n") { "OK: ${it.relativeTo(base)}" },
        )
    }
}

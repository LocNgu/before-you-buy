package io.github.locngu.beforeyoubuy.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Checks the licence notices that AboutLibraries generated into one variant's resources: every library on the
 * variant's classpath must be under an allowed licence (ADR-0023) and carry its licence text.
 */
abstract class CheckDependencyLicencesTask : DefaultTask() {
    @get:Input
    abstract val variantName: Property<String>

    /** The variant's resolved runtime classpath: what the APK contains. */
    @get:Input
    abstract val runtimeClasspath: Property<ResolvedComponentResult>

    /** The external modules' AARs and JARs on that classpath, searched for NOTICE files. */
    @get:Internal
    abstract val runtimeArtifacts: SetProperty<ResolvedArtifactResult>

    /** The same files, so Gradle sees when they change. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeArtifactFiles: ConfigurableFileCollection

    /** The variant's resource directories, generated ones included; one holds `raw/aboutlibraries.json`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDirectories: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowList: RegularFileProperty

    /** Base for the paths shown in messages: the repository root. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val result: RegularFileProperty

    @TaskAction
    fun check() {
        val variant = variantName.get()
        val base = rootDirectory.get().asFile
        val metadataFile = metadataFile(variant, base)
        val metadata = parse(metadataFile, base, DependencyLicences::parseMetadata)
        if (metadata.libraries.isEmpty()) {
            throw GradleException(
                "${metadataFile.relativeTo(base)} lists no libraries, so the $variant classpath wasn't collected. " +
                    "Check the aboutLibraries block in app/build.gradle.kts."
            )
        }
        val allowListFile = allowList.get().asFile
        val allowed = parse(allowListFile, base, DependencyLicences::parseAllowList)

        val problems = DependencyLicences.problems(metadata, allowed, shippedModules(runtimeClasspath.get()))
        if (problems.isNotEmpty()) {
            val message = message(variant, problems, allowListFile.relativeTo(base))
            annotateGitHubError("Dependency licences ($variant)", message)
            throw GradleException(message)
        }
        writeReport(variant, metadata, allowed, metadataFile.relativeTo(base))
    }

    /** Every library with its licences, for the PR's licence line and for spot checks. */
    private fun writeReport(variant: String, metadata: LicenceMetadata, allowed: List<AllowedArtifact>, source: File) {
        val unused = DependencyLicences.unusedEntries(metadata, allowed)
        val inEffect = (allowed - unused.toSet()).map { it.artifact }.toSet()
        result.get().asFile.writeText(
            buildString {
                appendLine("OK: ${metadata.libraries.size} libraries on the $variant classpath, from $source")
                metadata.libraries.sortedBy { it.coordinates }.forEach { library ->
                    val licences =
                        library.licenceKeys.map { key ->
                            val licence = metadata.licences[key]
                            when {
                                licence == null -> key
                                licence.isOwnNotice -> "${licence.spdxId} (own copy: $key)"
                                else -> licence.spdxId ?: "\"${licence.name}\""
                            }
                        }
                    val note = if (library.id in inEffect) " (allow-listed)" else ""
                    appendLine("${library.coordinates}: ${licences.joinToString()}$note")
                }
                if (unused.isNotEmpty()) {
                    appendLine("Allow-list entries without effect here: ${unused.joinToString { it.artifact }}")
                }
            }
        )
    }

    private fun metadataFile(variant: String, base: File): File {
        val found = resDirectories.files.map { File(it, "raw/aboutlibraries.json") }.filter { it.isFile }.distinct()
        return found.singleOrNull()
            ?: throw GradleException(
                if (found.isEmpty()) {
                    "No raw/aboutlibraries.json in the $variant resources. The AboutLibraries plugin generates it " +
                        "(com.mikepenz.aboutlibraries.plugin.android in app/build.gradle.kts)."
                } else {
                    "More than one raw/aboutlibraries.json in the $variant resources; only AboutLibraries " +
                        "may write it:\n" +
                        found.joinToString("\n") { "  ${it.relativeTo(base)}" }
                }
            )
    }

    /**
     * The external modules in the resolved graph, without BOMs (version constraints, no code) and without Kotlin
     * Multiplatform redirect modules, whose code is in the platform artifact they point at.
     */
    private fun shippedModules(root: ResolvedComponentResult): List<ShippedModule> {
        val seen = mutableSetOf<ComponentIdentifier>(root.id)
        val pending = ArrayDeque(listOf(root))
        val modules = mutableListOf<ModuleComponentIdentifier>()
        val publishedAs = mutableMapOf<String, String>()
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
                if (seen.add(it.selected.id)) pending.add(it.selected)
            }
            val id = component.id as? ModuleComponentIdentifier ?: continue
            if (component.isPlatform()) continue
            val targets = component.variants.map { it.externalVariant.orElse(null)?.owner }
            if (targets.isNotEmpty() && targets.all { it is ModuleComponentIdentifier }) {
                targets.forEach { target ->
                    target as ModuleComponentIdentifier
                    publishedAs["${target.group}:${target.module}"] = "${id.group}:${id.module}"
                }
            } else {
                modules += id
            }
        }
        val notices =
            runtimeArtifacts.get()
                .mapNotNull { artifact ->
                    val module = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    module?.let { "${it.group}:${it.module}" to artifact.file }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, files) ->
                    // A module published as an AAR also comes as its classes JAR; the AAR holds that JAR already.
                    val searched = files.filter { it.extension == "aar" }.ifEmpty { files }
                    searched.distinct().flatMap(DependencyLicences::noticeFiles).distinctBy { it.text }
                }
        return modules.map { "${it.group}:${it.module}" }.distinct().map {
            ShippedModule(it, publishedAs[it], notices[it].orEmpty())
        }
    }

    private fun ResolvedComponentResult.isPlatform() =
        variants.isNotEmpty() && variants.all { it.attributes.getAttribute(CATEGORY) in PLATFORMS }

    private fun <T> parse(file: File, base: File, parser: (String) -> T): T = try {
        parser(file.readText())
    } catch (e: IllegalArgumentException) {
        throw GradleException("${file.relativeTo(base)}: ${e.message}", e)
    } catch (e: groovy.json.JsonException) {
        throw GradleException("${file.relativeTo(base)} is not valid JSON: ${e.message}", e)
    }

    private fun message(variant: String, problems: List<LicenceProblem>, allowListPath: File) = buildString {
        val kinds = problems.map { it.kind }.toSet()
        appendLine("Libraries on the $variant classpath break the licence rule or lack their notices (ADR-0023).")
        problems.forEach { appendLine("  $it") }
        if (ProblemKind.NOT_LISTED in kinds) {
            appendLine(
                "AboutLibraries leaves out a library whose POM it couldn't download (it logs \"Failed to resolve POM " +
                    "batch\"; Maven Central often answers HTTP 429 on a cold cache). Run the build again."
            )
        }
        if (ProblemKind.NOT_ALLOWED in kinds) {
            appendLine(
                "Only ${ALLOWED_LICENCES.joinToString()} may ship (ADR-0004, ADR-0023). Remove or replace that " +
                    "dependency; only test and build tools may use other licences."
            )
        }
        if (ProblemKind.UNKNOWN in kinds || ProblemKind.MISSING in kinds) {
            appendLine(
                "Check that artifact's licence by hand (the LICENSE file in its source repository). If it is " +
                    "Apache-2.0, MIT or BSD, add it to $allowListPath with the licence and where you checked it:"
            )
            appendLine(
                """  {"artifact": "group:artifact", "licence": "MIT", "reason": "LICENSE at <link>, checked <date>"}"""
            )
        }
        if (kinds.any { it in NOTICE_PROBLEMS }) {
            appendLine(
                "The APK must carry each licence text, copyright line and NOTICE file. Add the missing text " +
                    "verbatim under ${allowListPath.parentFile}/ (licenses/, linked from libraries/)."
            )
        }
        append("How to: .claude/rules/build.md, section \"Dependency licences\".")
    }

    private companion object {
        // Resolution results carry attributes as strings (gradle/gradle#8854).
        val CATEGORY: Attribute<String> = Attribute.of(Category.CATEGORY_ATTRIBUTE.name, String::class.java)
        val PLATFORMS = setOf(Category.REGULAR_PLATFORM, Category.ENFORCED_PLATFORM)
        val NOTICE_PROBLEMS = setOf(ProblemKind.NO_TEXT, ProblemKind.NO_OWN_NOTICE, ProblemKind.NOTICE_NOT_INCLUDED)
    }
}

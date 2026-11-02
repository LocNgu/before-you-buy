package io.github.locngu.beforeyoubuy.buildlogic

import groovy.json.JsonSlurper
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal const val APACHE_2_0 = "Apache-2.0"

/** The licences shipped code may use, as SPDX ids (ADR-0004, ADR-0023). */
internal val ALLOWED_LICENCES = listOf(APACHE_2_0, "MIT", "BSD-2-Clause", "BSD-3-Clause")

/**
 * Licences whose text starts with the copyright holder's line. The generic SPDX text only has placeholders, so a
 * library under one of them also needs its own copy of the licence (its LICENSE file).
 */
internal val OWN_NOTICE_LICENCES = setOf("MIT", "BSD-2-Clause", "BSD-3-Clause")

/**
 * One licence in AboutLibraries' metadata. [key] is AboutLibraries' hash: the SPDX id for a recognised licence, a
 * name we chose for a library's own notice, or a checksum for a licence it couldn't recognise ([spdxId] is null).
 */
internal data class Licence(
    val key: String,
    val name: String,
    val url: String?,
    val spdxId: String?,
    val text: String?
) {
    val hasText get() = !text.isNullOrBlank()

    /** A library's own copy of a licence (its LICENSE or NOTICE file), not the generic SPDX text. */
    val isOwnNotice get() = spdxId != null && key != spdxId && hasText

    fun describe() = if (spdxId == null) "\"$name\"${url?.let { " ($it)" }.orEmpty()}" else "\"$name\" ($spdxId)"
}

/** One library in AboutLibraries' metadata: `group:artifact`, its version and the keys of its licences. */
internal data class LicensedLibrary(val id: String, val version: String?, val licenceKeys: List<String>) {
    val coordinates get() = if (version.isNullOrBlank()) id else "$id:$version"
}

/** AboutLibraries' `aboutlibraries.json`: what the APK says about its third-party code. */
internal data class LicenceMetadata(val libraries: List<LicensedLibrary>, val licences: Map<String, Licence>)

/** An allow-list entry: a person checked [artifact]'s licence by hand because its POM names none we recognise. */
internal data class AllowedArtifact(val artifact: String, val licence: String, val reason: String)

/**
 * A module (`group:artifact`) on a variant's runtime classpath. [publishedAs] is the module it is published under
 * when it is a Kotlin Multiplatform platform artifact (`androidx.collection:collection` for `collection-jvm`),
 * which is the name AboutLibraries lists it by. [notices] are the NOTICE files in its artifacts.
 */
internal data class ShippedModule(
    val id: String,
    val publishedAs: String? = null,
    val notices: List<NoticeFile> = emptyList()
)

/** A NOTICE file inside an artifact: its path there and its text. Apache-2.0 §4(d) says it must ship. */
internal data class NoticeFile(val path: String, val text: String)

internal enum class ProblemKind {
    /** On the classpath but not in the notices: AboutLibraries left it out (usually a failed POM download). */
    NOT_LISTED,

    /** A recognised licence that ADR-0023 doesn't allow in shipped code. */
    NOT_ALLOWED,

    /** The POM names a licence AboutLibraries couldn't map to an SPDX id. */
    UNKNOWN,

    /** The POM names no licence. */
    MISSING,

    /** A licence the library uses has no text in the metadata. */
    NO_TEXT,

    /** An MIT or BSD library without its own copy of the licence (the copyright line). */
    NO_OWN_NOTICE,

    /** The library's artifact contains a NOTICE file whose text isn't in its notices. */
    NOTICE_NOT_INCLUDED
}

internal data class LicenceProblem(val artifact: String, val kind: ProblemKind, val detail: String) {
    override fun toString() = "$artifact: $detail"
}

/** The licence rule for shipped dependencies (ADR-0023) and the notices the APK carries for them. */
internal object DependencyLicences {
    private val ALLOW_LIST_KEYS = setOf("artifact", "licence", "reason")
    private val ARTIFACT = Regex("[^:\\s]+:[^:\\s]+")
    private val NOTICE = Regex("(^|/)NOTICE(\\.(txt|md|markdown|html?))?$", RegexOption.IGNORE_CASE)
    private val LICENCE_KINDS = setOf(ProblemKind.NOT_ALLOWED, ProblemKind.UNKNOWN, ProblemKind.MISSING)
    private val WHITESPACE = Regex("\\s+")

    /**
     * The NOTICE files in a JAR or AAR: anywhere in the archive, and for an AAR also in its `classes.jar` and
     * `libs/` JARs. Paths inside nested JARs read `classes.jar!/META-INF/NOTICE`.
     */
    fun noticeFiles(artifact: File): List<NoticeFile> = ZipFile(artifact).use { zip ->
        noticeFiles(zip.entries().toList()) { zip.getInputStream(it) }
    }

    private fun noticeFiles(entries: List<ZipEntry>, open: (ZipEntry) -> InputStream): List<NoticeFile> =
        entries.filterNot { it.isDirectory }.flatMap { entry ->
            when {
                NOTICE.containsMatchIn(entry.name) ->
                    listOf(NoticeFile(entry.name, open(entry).use { it.readBytes().toString(Charsets.UTF_8) }))

                entry.name.endsWith(".jar") ->
                    open(entry).use { stream ->
                        val nested = ZipInputStream(stream)
                        generateSequence { nested.nextEntry }
                            .filter { !it.isDirectory && NOTICE.containsMatchIn(it.name) }
                            .map {
                                NoticeFile("${entry.name}!/${it.name}", nested.readBytes().toString(Charsets.UTF_8))
                            }
                            .toList()
                    }

                else -> emptyList()
            }
        }

    private fun normalized(text: String) = text.replace(WHITESPACE, " ").trim()

    /** Reads AboutLibraries' `aboutlibraries.json`. Throws [IllegalArgumentException] if it isn't that format. */
    fun parseMetadata(json: String): LicenceMetadata {
        val root = JsonSlurper().parseText(json) as? Map<*, *>
            ?: throw IllegalArgumentException("expected a JSON object with \"libraries\" and \"licenses\"")
        val licences =
            (root["licenses"] as? Map<*, *>).orEmpty().map { (key, value) ->
                val fields = value as? Map<*, *> ?: throw IllegalArgumentException("licence \"$key\" is not an object")
                Licence(
                    key = key.toString(),
                    name = fields["name"] as? String ?: key.toString(),
                    url = fields["url"] as? String,
                    spdxId = fields["spdxId"] as? String,
                    text = fields["content"] as? String
                )
            }
        val libraries =
            (root["libraries"] as? List<*>).orEmpty().map { item ->
                val fields = item as? Map<*, *> ?: throw IllegalArgumentException("a library is not an object")
                LicensedLibrary(
                    id = fields["uniqueId"] as? String ?: throw IllegalArgumentException("a library has no uniqueId"),
                    version = fields["artifactVersion"] as? String,
                    licenceKeys = (fields["licenses"] as? List<*>).orEmpty().map { it.toString() }
                )
            }
        return LicenceMetadata(libraries, licences.associateBy { it.key })
    }

    /**
     * Reads the allow-list: a JSON array of `{"artifact": "group:artifact", "licence": "<SPDX id>", "reason": "…"}`.
     * Throws [IllegalArgumentException] naming the entry if one is malformed or declares a licence ADR-0023 doesn't
     * allow.
     */
    fun parseAllowList(json: String): List<AllowedArtifact> {
        val root = JsonSlurper().parseText(json) as? List<*>
            ?: throw IllegalArgumentException("expected a JSON array of entries (an empty list is [])")
        val entries =
            root.mapIndexed { index, item ->
                val where = "entry ${index + 1}"
                val fields = item as? Map<*, *> ?: throw IllegalArgumentException("$where is not an object")
                val unexpected = fields.keys.map { it.toString() }.filterNot { it in ALLOW_LIST_KEYS }
                require(unexpected.isEmpty()) {
                    "$where has unexpected keys ${unexpected.sorted()}; use exactly ${ALLOW_LIST_KEYS.sorted()}"
                }
                fun text(key: String) = (fields[key] as? String)?.trim().orEmpty()
                val artifact = text("artifact")
                val licence = text("licence")
                val reason = text("reason")
                require(ARTIFACT.matches(artifact)) {
                    "$where: \"artifact\" must be group:artifact without a version, found \"$artifact\""
                }
                require(licence in ALLOWED_LICENCES) {
                    "$where ($artifact): \"licence\" must be one of ${ALLOWED_LICENCES.joinToString()}, found " +
                        "\"$licence\". ADR-0023 allows no other licence in shipped code, so it can't be allow-listed."
                }
                require(reason.isNotEmpty()) { "$where ($artifact): \"reason\" must say where the licence was checked" }
                AllowedArtifact(artifact, licence, reason)
            }
        val duplicates = entries.groupBy { it.artifact }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate entries for ${duplicates.sorted().joinToString()}" }
        return entries
    }

    /**
     * Every module in [shipped] that the notices leave out, and every library in [metadata] that breaks the licence
     * rule or lacks its notice. A library with a licence problem isn't also checked for notices.
     *
     * - Every shipped module must be listed, under its own name or the one it is published under.
     * - A recognised licence must be one of [ALLOWED_LICENCES]. The allow-list never changes that.
     * - A licence AboutLibraries couldn't recognise, or no licence at all, fails unless [allowList] has the artifact.
     *   An allow-listed library must still link to the text of the licence its entry declares.
     * - Every licence a library uses needs its text, and an MIT or BSD library its own copy (see
     *   [OWN_NOTICE_LICENCES]).
     * - The text of every NOTICE file in a shipped module's artifacts must be in one of its library's licences
     *   (whitespace aside).
     */
    fun problems(
        metadata: LicenceMetadata,
        allowList: List<AllowedArtifact>,
        shipped: List<ShippedModule>
    ): List<LicenceProblem> {
        val listed = metadata.libraries.map { it.id }.toSet()
        val notListed =
            shipped
                .filterNot { it.id in listed || it.publishedAs in listed }
                .map { it.id }
                .distinct()
                .sorted()
                .map { LicenceProblem(it, ProblemKind.NOT_LISTED, "is on the classpath but missing from the notices") }
        val libraryProblems = libraryProblems(metadata, allowList)
        val cantShip = libraryProblems.filter { it.kind in LICENCE_KINDS }.map { it.artifact }.toSet()
        return notListed + libraryProblems + noticeProblems(metadata, shipped).filterNot { it.artifact in cantShip }
    }

    /**
     * Allow-list entries that have no effect on [metadata]: their artifact isn't listed, or its licences are all
     * recognised, so it needs no entry. Safe to remove once no variant needs them.
     */
    fun unusedEntries(metadata: LicenceMetadata, allowList: List<AllowedArtifact>): List<AllowedArtifact> {
        val libraries = metadata.libraries.associateBy { it.id }
        return allowList.filter { entry ->
            val library = libraries[entry.artifact]
            library == null || !needsEntry(metadata.licencesOf(library))
        }
    }

    /** An allow-list entry only applies while AboutLibraries doesn't recognise a licence of the library, or finds none. */
    private fun needsEntry(licences: List<Licence>) = licences.isEmpty() || licences.any { it.spdxId == null }

    private fun LicenceMetadata.licencesOf(library: LicensedLibrary) =
        library.licenceKeys.map { key -> licences[key] ?: Licence(key, key, null, null, null) }

    private fun noticeProblems(metadata: LicenceMetadata, shipped: List<ShippedModule>): List<LicenceProblem> {
        val libraries = metadata.libraries.associateBy { it.id }
        return shipped.flatMap { module ->
            val library = libraries[module.id] ?: libraries[module.publishedAs] ?: return@flatMap emptyList()
            val texts = library.licenceKeys.mapNotNull { metadata.licences[it]?.text }.map(::normalized)
            module.notices
                .filter { notice -> texts.none { it.contains(normalized(notice.text)) } }
                .map {
                    val artifact = if (module.id == library.id) "its artifact" else "its artifact ${module.id}"
                    LicenceProblem(
                        library.coordinates,
                        ProblemKind.NOTICE_NOT_INCLUDED,
                        "$artifact contains ${it.path}, whose text isn't in its notices"
                    )
                }
        }.distinct().sortedBy { it.artifact }
    }

    private fun libraryProblems(metadata: LicenceMetadata, allowList: List<AllowedArtifact>): List<LicenceProblem> {
        val allowed = allowList.associateBy { it.artifact }
        return metadata.libraries.sortedBy { it.coordinates }.flatMap { library ->
            val licences = metadata.licencesOf(library)
            val entry = allowed[library.id]?.takeIf { needsEntry(licences) }
            fun problem(kind: ProblemKind, detail: String) = LicenceProblem(library.coordinates, kind, detail)

            val licenceProblems = buildList {
                licences.filter { it.spdxId != null && it.spdxId !in ALLOWED_LICENCES }.forEach {
                    add(problem(ProblemKind.NOT_ALLOWED, "licence ${it.describe()} is not allowed in shipped code"))
                }
                if (entry == null) {
                    if (licences.isEmpty()) add(problem(ProblemKind.MISSING, "its POM names no licence"))
                    licences.filter { it.spdxId == null }.forEach {
                        add(
                            problem(
                                ProblemKind.UNKNOWN,
                                "its POM names the licence ${it.describe()}, which isn't recognised"
                            )
                        )
                    }
                }
            }
            licenceProblems.ifEmpty {
                buildList {
                    licences.filter { it.spdxId != null && !it.hasText }.forEach {
                        add(problem(ProblemKind.NO_TEXT, "no text for its licence ${it.describe()}"))
                    }
                    // The notices must link an allow-listed library to the licence a person found, not only to the
                    // unrecognised one. (For MIT and BSD that is its own copy, checked below.)
                    if (entry != null && entry.licence !in OWN_NOTICE_LICENCES &&
                        licences.none { it.spdxId == entry.licence && it.hasText }
                    ) {
                        add(
                            problem(
                                ProblemKind.NO_TEXT,
                                "its notices don't link it to the text of ${entry.licence}, which its allow-list " +
                                    "entry declares"
                            )
                        )
                    }
                    val ids = licences.mapNotNull { it.spdxId } + listOfNotNull(entry?.licence)
                    ids.filter { it in OWN_NOTICE_LICENCES }.distinct().sorted().forEach { id ->
                        if (licences.none { it.spdxId == id && it.isOwnNotice }) {
                            add(
                                problem(
                                    ProblemKind.NO_OWN_NOTICE,
                                    "$id needs the library's own copy of the licence (its copyright line)"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

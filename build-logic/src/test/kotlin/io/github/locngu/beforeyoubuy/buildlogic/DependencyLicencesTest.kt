package io.github.locngu.beforeyoubuy.buildlogic

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DependencyLicencesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val apache =
        Licence(
            APACHE_2_0,
            "Apache License 2.0",
            "https://spdx.org/licenses/Apache-2.0.html",
            APACHE_2_0,
            "Apache text"
        )
    private val bsd3 = Licence("BSD-3-Clause", "BSD 3-Clause", null, "BSD-3-Clause", "Copyright (c) <year> <owner>.")
    private val protobufNotice =
        Licence("protobuf", "Protocol Buffers", null, "BSD-3-Clause", "Copyright 2008 Google Inc.")
    private val gpl = Licence("GPL-3.0-only", "GNU General Public License v3.0 only", null, "GPL-3.0-only", "GPL text")
    private val unknown = Licence("3f2a", "Odd Licence", "https://odd.example/licence", null, null)

    private fun metadata(vararg libraries: LicensedLibrary, licences: List<Licence> = listOf(apache)) =
        LicenceMetadata(libraries.toList(), licences.associateBy { it.key })

    private fun library(id: String, vararg licenceKeys: String) = LicensedLibrary(id, "1.0", licenceKeys.toList())

    private fun kinds(problems: List<LicenceProblem>) = problems.map { it.artifact to it.kind }

    /** Checks [metadata] against a classpath that holds exactly its libraries, unless [shipped] says otherwise. */
    private fun check(
        metadata: LicenceMetadata,
        allowList: List<AllowedArtifact> = emptyList(),
        shipped: List<ShippedModule> = metadata.libraries.map { ShippedModule(it.id) }
    ) = DependencyLicences.problems(metadata, allowList, shipped)

    @Test
    fun `Apache-2_0 libraries with the licence text pass`() {
        val metadata =
            metadata(library("androidx.room:room-runtime", APACHE_2_0), library("com.google.guava:guava", APACHE_2_0))

        assertEquals(emptyList<LicenceProblem>(), check(metadata))
    }

    @Test
    fun `a BSD library with its own copy of the licence passes`() {
        val metadata =
            metadata(
                library("androidx.datastore:datastore-preferences-external-protobuf", "BSD-3-Clause", "protobuf"),
                licences = listOf(apache, bsd3, protobufNotice)
            )

        assertEquals(emptyList<LicenceProblem>(), check(metadata))
    }

    @Test
    fun `a GPL library is not allowed`() {
        val metadata = metadata(library("org.example:copyleft", "GPL-3.0-only"), licences = listOf(apache, gpl))

        val problems = check(metadata)

        assertEquals(listOf("org.example:copyleft:1.0" to ProblemKind.NOT_ALLOWED), kinds(problems))
        assertEquals(
            "org.example:copyleft:1.0: licence \"GNU General Public License v3.0 only\" (GPL-3.0-only) is not allowed in shipped code",
            problems.single().toString()
        )
    }

    @Test
    fun `a licence that isn't recognised fails and names its POM name and URL`() {
        val metadata = metadata(library("org.example:odd", "3f2a"), licences = listOf(apache, unknown))

        val problems = check(metadata)

        assertEquals(listOf("org.example:odd:1.0" to ProblemKind.UNKNOWN), kinds(problems))
        assertEquals(
            "org.example:odd:1.0: its POM names the licence \"Odd Licence\" (https://odd.example/licence), which isn't recognised",
            problems.single().toString()
        )
    }

    @Test
    fun `a licence key without an entry counts as not recognised`() {
        val metadata = metadata(library("org.example:dangling", "no-such-key"))

        assertEquals(
            listOf("org.example:dangling:1.0" to ProblemKind.UNKNOWN),
            kinds(check(metadata))
        )
    }

    @Test
    fun `a library without a licence fails`() {
        val metadata = metadata(library("org.example:bare"))

        assertEquals(
            listOf("org.example:bare:1.0" to ProblemKind.MISSING),
            kinds(check(metadata))
        )
    }

    @Test
    fun `allow-listing accepts an unrecognised licence once the notices link the declared licence's text`() {
        // libraries/org.example.odd.json adds Apache-2.0 next to the licence AboutLibraries didn't recognise.
        val metadata = metadata(library("org.example:odd", "3f2a", APACHE_2_0), licences = listOf(apache, unknown))
        val allowList = listOf(AllowedArtifact("org.example:odd", APACHE_2_0, "LICENSE in the repository"))

        assertEquals(emptyList<LicenceProblem>(), check(metadata, allowList))
    }

    @Test
    fun `the allow-list never accepts a licence that isn't allowed`() {
        val metadata =
            metadata(library("org.example:copyleft", "GPL-3.0-only", "3f2a"), licences = listOf(apache, gpl, unknown))
        val allowList = listOf(AllowedArtifact("org.example:copyleft", "MIT", "dual licensed"))

        assertEquals(
            listOf("org.example:copyleft:1.0" to ProblemKind.NOT_ALLOWED),
            kinds(check(metadata, allowList))
        )
    }

    @Test
    fun `a licence without its text fails`() {
        val metadata =
            metadata(library("androidx.room:room-runtime", APACHE_2_0), licences = listOf(apache.copy(text = null)))

        assertEquals(
            listOf("androidx.room:room-runtime:1.0" to ProblemKind.NO_TEXT),
            kinds(check(metadata))
        )
    }

    @Test
    fun `an MIT or BSD library without its own copy of the licence fails`() {
        val metadata =
            metadata(
                library("androidx.datastore:datastore-preferences-external-protobuf", "BSD-3-Clause"),
                licences = listOf(apache, bsd3)
            )

        val problems = check(metadata)

        assertEquals(
            listOf("androidx.datastore:datastore-preferences-external-protobuf:1.0" to ProblemKind.NO_OWN_NOTICE),
            kinds(problems)
        )
    }

    @Test
    fun `an own notice for another licence doesn't count`() {
        val mitNotice = Licence("odd-mit", "Odd (MIT)", null, "MIT", "Copyright 2020 Someone")
        val metadata =
            metadata(
                library("org.example:mixed", "BSD-3-Clause", "odd-mit"),
                licences = listOf(apache, bsd3, mitNotice)
            )

        assertEquals(
            listOf("org.example:mixed:1.0" to ProblemKind.NO_OWN_NOTICE),
            kinds(check(metadata))
        )
    }

    @Test
    fun `an allow-listed MIT library still needs its own copy of the licence`() {
        val metadata = metadata(library("org.example:odd", "3f2a"), licences = listOf(apache, unknown))
        val allowList = listOf(AllowedArtifact("org.example:odd", "MIT", "LICENSE in the repository"))

        assertEquals(
            listOf("org.example:odd:1.0" to ProblemKind.NO_OWN_NOTICE),
            kinds(check(metadata, allowList))
        )
    }

    @Test
    fun `an allow-listed library whose notices don't link the declared licence's text fails`() {
        val metadata =
            metadata(
                library("org.example:odd", "3f2a"),
                library("org.example:bare"),
                licences = listOf(apache, unknown)
            )
        val allowList =
            listOf(
                AllowedArtifact("org.example:odd", APACHE_2_0, "LICENSE in the repository"),
                AllowedArtifact("org.example:bare", APACHE_2_0, "LICENSE in the repository")
            )

        val problems = check(metadata, allowList)

        assertEquals(
            listOf("org.example:bare:1.0" to ProblemKind.NO_TEXT, "org.example:odd:1.0" to ProblemKind.NO_TEXT),
            kinds(problems)
        )
        assertEquals(
            "org.example:odd:1.0: its notices don't link it to the text of Apache-2.0, which its allow-list entry declares",
            problems[1].toString()
        )
    }

    @Test
    fun `an allow-list entry has no effect once the licence is recognised, and is reported as unused`() {
        val metadata =
            metadata(
                library("org.example:fixed", APACHE_2_0),
                library("org.example:odd", "3f2a", APACHE_2_0),
                licences = listOf(apache, unknown)
            )
        val stale = AllowedArtifact("org.example:fixed", "MIT", "the POM had no licence before 2.0")
        val gone = AllowedArtifact("org.example:removed", APACHE_2_0, "LICENSE in the repository")
        val needed = AllowedArtifact("org.example:odd", APACHE_2_0, "LICENSE in the repository")
        val allowList = listOf(stale, gone, needed)

        // The stale MIT entry no longer demands an own MIT copy.
        assertEquals(emptyList<LicenceProblem>(), check(metadata, allowList))
        assertEquals(listOf(stale, gone), DependencyLicences.unusedEntries(metadata, allowList))
    }

    @Test
    fun `problems are sorted by library and a licence problem hides notice problems`() {
        val metadata =
            metadata(
                library("org.example:zeta", "3f2a"),
                library("org.example:alpha", "GPL-3.0-only", "BSD-3-Clause"),
                licences = listOf(apache, gpl, bsd3, unknown)
            )
        val shipped =
            listOf(
                ShippedModule("org.example:zeta"),
                ShippedModule("org.example:alpha", notices = listOf(NoticeFile("META-INF/NOTICE", "Alpha notice")))
            )

        assertEquals(
            listOf("org.example:alpha:1.0" to ProblemKind.NOT_ALLOWED, "org.example:zeta:1.0" to ProblemKind.UNKNOWN),
            kinds(check(metadata, shipped = shipped))
        )
    }

    @Test
    fun `a module on the classpath that the notices leave out fails`() {
        val metadata = metadata(library("androidx.room:room-runtime", APACHE_2_0))
        val shipped =
            listOf(ShippedModule("androidx.room:room-runtime"), ShippedModule("org.jetbrains.kotlin:kotlin-stdlib"))

        val problems = check(metadata, shipped = shipped)

        assertEquals(listOf("org.jetbrains.kotlin:kotlin-stdlib" to ProblemKind.NOT_LISTED), kinds(problems))
        assertEquals(
            "org.jetbrains.kotlin:kotlin-stdlib: is on the classpath but missing from the notices",
            problems.single().toString()
        )
    }

    @Test
    fun `a platform artifact counts as listed under the module it is published under`() {
        val metadata = metadata(library("androidx.collection:collection", APACHE_2_0))
        val shipped =
            listOf(ShippedModule("androidx.collection:collection-jvm", publishedAs = "androidx.collection:collection"))

        assertEquals(emptyList<LicenceProblem>(), check(metadata, shipped = shipped))
    }

    @Test
    fun `libraries only in the notices, such as compile-only ones, are fine`() {
        val metadata =
            metadata(library("androidx.room:room-runtime", APACHE_2_0), library("org.example:compile-only", APACHE_2_0))

        assertEquals(
            emptyList<LicenceProblem>(),
            check(metadata, shipped = listOf(ShippedModule("androidx.room:room-runtime")))
        )
    }

    @Test
    fun `a NOTICE file whose text is in the library's notices passes, whitespace aside`() {
        val notice =
            Licence(
                "jakarta-inject-notice",
                "Jakarta NOTICE",
                null,
                APACHE_2_0,
                "# Notices for\n\nJakarta   Injection\n"
            )
        val metadata =
            metadata(
                library("jakarta.inject:jakarta.inject-api", APACHE_2_0, "jakarta-inject-notice"),
                licences = listOf(apache, notice)
            )
        val shipped =
            listOf(
                ShippedModule(
                    "jakarta.inject:jakarta.inject-api",
                    notices = listOf(NoticeFile("META-INF/NOTICE.md", "# Notices for\nJakarta Injection"))
                )
            )

        assertEquals(emptyList<LicenceProblem>(), check(metadata, shipped = shipped))
    }

    @Test
    fun `a NOTICE file missing from the notices fails, also for a platform artifact`() {
        val metadata =
            metadata(library("jakarta.inject:jakarta.inject-api", APACHE_2_0), library("org.example:kmp", APACHE_2_0))
        val shipped =
            listOf(
                ShippedModule(
                    "jakarta.inject:jakarta.inject-api",
                    notices = listOf(NoticeFile("META-INF/NOTICE.md", "Jakarta"))
                ),
                ShippedModule(
                    "org.example:kmp-jvm",
                    publishedAs = "org.example:kmp",
                    notices = listOf(NoticeFile("NOTICE", "KMP"))
                )
            )

        val problems = check(metadata, shipped = shipped)

        assertEquals(
            listOf(
                "jakarta.inject:jakarta.inject-api:1.0" to ProblemKind.NOTICE_NOT_INCLUDED,
                "org.example:kmp:1.0" to ProblemKind.NOTICE_NOT_INCLUDED
            ),
            kinds(problems)
        )
        assertEquals(
            "org.example:kmp:1.0: its artifact org.example:kmp-jvm contains NOTICE, whose text isn't in its notices",
            problems[1].toString()
        )
    }

    @Test
    fun `NOTICE files are found in a JAR and in an AAR's root, classes jar and libs, and nothing else`() {
        val jar = zip(
            "lib.jar",
            "META-INF/NOTICE.md" to "jar notice",
            "META-INF/LICENSE.txt" to "licence",
            "a/B.class" to "x"
        )
        val classes = zipBytes("META-INF/NOTICE" to "classes notice")
        val inner = zipBytes("notice.txt" to "libs notice")
        val aar = zip(
            "lib.aar",
            "META-INF/androidx/x/NOTICE.txt" to "root notice",
            "classes.jar" to classes,
            "libs/inner.jar" to inner
        )

        assertEquals(listOf(NoticeFile("META-INF/NOTICE.md", "jar notice")), DependencyLicences.noticeFiles(jar))
        assertEquals(
            listOf(
                NoticeFile("META-INF/androidx/x/NOTICE.txt", "root notice"),
                NoticeFile("classes.jar!/META-INF/NOTICE", "classes notice"),
                NoticeFile("libs/inner.jar!/notice.txt", "libs notice")
            ),
            DependencyLicences.noticeFiles(aar)
        )
    }

    private fun zipBytes(vararg entries: Pair<String, Any>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(if (content is ByteArray) content else content.toString().toByteArray())
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun zip(name: String, vararg entries: Pair<String, Any>): File = File(temp.root, name).apply {
        writeBytes(zipBytes(*entries))
    }

    @Test
    fun `AboutLibraries metadata is read`() {
        val json =
            """
            {"libraries": [
               {"uniqueId": "androidx.room:room-runtime", "artifactVersion": "2.8.5", "name": "Room", "licenses": ["Apache-2.0"]},
               {"uniqueId": "org.example:bare", "developers": []}
             ],
             "licenses": {
               "Apache-2.0": {"name": "Apache License 2.0", "url": "https://spdx.org/licenses/Apache-2.0.html",
                              "content": "Apache text", "hash": "Apache-2.0", "spdxId": "Apache-2.0"},
               "3f2a": {"name": "Odd Licence", "hash": "3f2a"}
             }}
            """

        val metadata = DependencyLicences.parseMetadata(json)

        assertEquals(
            listOf(
                LicensedLibrary("androidx.room:room-runtime", "2.8.5", listOf(APACHE_2_0)),
                LicensedLibrary("org.example:bare", null, emptyList())
            ),
            metadata.libraries
        )
        assertEquals(apache, metadata.licences.getValue(APACHE_2_0))
        assertEquals(Licence("3f2a", "Odd Licence", null, null, null), metadata.licences.getValue("3f2a"))
        assertEquals("org.example:bare", metadata.libraries[1].coordinates)
    }

    @Test
    fun `metadata that isn't AboutLibraries' format is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DependencyLicences.parseMetadata("[]") }
        assertThrows(IllegalArgumentException::class.java) {
            DependencyLicences.parseMetadata("""{"libraries": [{"name": "no id"}]}""")
        }
    }

    @Test
    fun `the allow-list is read`() {
        val json =
            """
            [
              {"artifact": "org.example:odd", "licence": "MIT", "reason": "LICENSE at https://example.org, checked 2026-11-02"}
            ]
            """

        assertEquals(
            listOf(AllowedArtifact("org.example:odd", "MIT", "LICENSE at https://example.org, checked 2026-11-02")),
            DependencyLicences.parseAllowList(json)
        )
        assertEquals(emptyList<AllowedArtifact>(), DependencyLicences.parseAllowList("[]"))
    }

    @Test
    fun `an allow-list entry for a licence that isn't allowed is rejected`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                DependencyLicences.parseAllowList(
                    """[{"artifact": "org.example:x", "licence": "GPL-3.0-only", "reason": "r"}]"""
                )
            }

        assertTrue(error.message, error.message!!.contains("ADR-0023 allows no other licence"))
    }

    @Test
    fun `malformed allow-list entries are rejected with what is wrong`() {
        fun error(json: String) =
            assertThrows(IllegalArgumentException::class.java) { DependencyLicences.parseAllowList(json) }.message!!

        assertTrue(error("""{"artifact": "a:b"}""").contains("JSON array"))
        assertTrue(
            error("""[{"artifact": "a:b:1.0", "licence": "MIT", "reason": "r"}]""").contains("without a version")
        )
        assertTrue(
            error(
                """[{"artifact": "a:b", "licence": "MIT", "reason": " "}]"""
            ).contains("where the licence was checked")
        )
        assertTrue(
            error("""[{"artifact": "a:b", "license": "MIT", "reason": "r"}]""").contains("unexpected keys [license]")
        )
        val duplicate = """{"artifact": "a:b", "licence": "MIT", "reason": "r"}"""
        assertTrue(error("[$duplicate, $duplicate]").contains("duplicate entries for a:b"))
    }
}

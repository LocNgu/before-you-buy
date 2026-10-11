package dev.placeholder.beforeyoubuy.buildlogic

import java.io.File

/** Reads the keys of string resources and compares English (`values/`) with German (`values-de/`), ADR-0016. */
internal object StringResources {
    private val STRING_TYPES = setOf("string", "plurals", "string-array")

    /**
     * Keys (`string/app_name`, `plurals/days_left`, …) of the string resources in one `values*` XML file.
     * With [translatableOnly], strings marked `translatable="false"` are left out.
     */
    fun keys(file: File, translatableOnly: Boolean): Set<String> = parseXml(file)
        .childElements()
        .filter { it.tagName in STRING_TYPES }
        .filterNot { translatableOnly && it.getAttribute("translatable") == "false" }
        .map { "${it.tagName}/${it.getAttribute("name")}" }
        .toSet()

    /**
     * Problems for one resource directory, given the English files in `values/` and the German files in
     * `values-de/`. English-only strings (`translatable="false"`) need no German entry and must not have one.
     */
    fun parityProblems(english: Collection<File>, german: Collection<File>): List<String> {
        val englishKeys = english.flatMap { keys(it, translatableOnly = true) }.toSet()
        val germanKeys = german.flatMap { keys(it, translatableOnly = false) }.toSet()
        return (englishKeys - germanKeys).sorted().map { "$it is in values/ but missing from values-de/" } +
            (germanKeys - englishKeys).sorted().map { "$it is in values-de/ but not a translatable string in values/" }
    }
}

package io.github.locngu.beforeyoubuy.buildlogic

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

/** Parses [file] namespace-aware, with DTDs and external entities off. */
internal fun parseXml(file: File): Element {
    val factory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
    return factory.newDocumentBuilder().parse(file).documentElement
}

/** The direct child elements of this element. */
internal fun Element.childElements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

/** Prints a GitHub Actions error annotation when running there; a no-op elsewhere. */
internal fun annotateGitHubError(title: String, message: String) {
    if (System.getenv("GITHUB_ACTIONS") != "true") return
    fun escape(value: String) = value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    println("::error title=${escape(title).replace(",", "%2C").replace(":", "%3A")}::${escape(message)}")
}

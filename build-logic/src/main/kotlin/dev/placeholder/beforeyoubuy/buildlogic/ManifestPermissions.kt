package dev.placeholder.beforeyoubuy.buildlogic

import java.io.File

internal const val INTERNET_PERMISSION = "android.permission.INTERNET"

/** A `<uses-permission…>` element of a manifest: the element name and the permission it requests. */
internal data class RequestedPermission(val element: String, val name: String) {
    override fun toString() = "<$element android:name=\"$name\"/>"
}

internal object ManifestPermissions {
    // uses-permission-sdk-m is the old name of uses-permission-sdk-23; both still merge.
    private val PERMISSION_ELEMENTS = setOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")

    /** Every permission [manifest] requests, in document order. */
    fun requested(manifest: File): List<RequestedPermission> = parseXml(manifest)
        .childElements()
        .filter { it.tagName in PERMISSION_ELEMENTS }
        .map { RequestedPermission(it.tagName, it.getAttributeNS(ANDROID_NAMESPACE, "name")) }

    /** The elements in [manifest] that request `INTERNET` (ADR-0003). */
    fun internet(manifest: File) = requested(manifest).filter { it.name == INTERNET_PERMISSION }
}

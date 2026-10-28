package com.divineit.convertor.domain.extraction

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Shared ZIP + XML helpers for the Office/OpenDocument extractors.
 *  Uses javax.xml (available on both the JVM and Android). External
 *  entities and DTDs are disabled as far as the implementation allows,
 *  because these parsers read user-supplied files. */
internal fun readZipEntry(bytes: ByteArray, entryName: String): ByteArray? {
    return try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) return zip.readBytes()
                entry = zip.nextEntry
            }
            null
        }
    } catch (e: Exception) {
        null
    }
}

internal fun zipEntryNames(bytes: ByteArray): List<String> {
    return try {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names.add(entry.name)
                entry = zip.nextEntry
            }
        }
        names
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun parseXml(bytes: ByteArray): Document? {
    return try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        try {
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true
            )
        } catch (e: Exception) {
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            } catch (ignored: Exception) {
            }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    } catch (e: Exception) {
        null
    }
}

/** Local name without any namespace prefix ("w:p" -> "p"). */
internal fun Element.localName(): String = nodeName.substringAfter(':')

internal fun Element.childElements(): List<Element> {
    val out = mutableListOf<Element>()
    val children = childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child is Element) out.add(child)
    }
    return out
}

internal fun Element.descendants(): Sequence<Element> = sequence {
    for (child in childElements()) {
        yield(child)
        yieldAll(child.descendants())
    }
}

/** Collapse whitespace runs and trim, matching splitParagraphs behaviour. */
internal fun normaliseWhitespace(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()

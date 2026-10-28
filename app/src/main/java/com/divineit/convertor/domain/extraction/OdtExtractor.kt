package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat
import org.w3c.dom.Element
import org.w3c.dom.Node

/** ODT extraction via ODF zip/XML. Paragraphs (text:p), headings
 *  (text:h with text:outline-level) and tables (table:table) are
 *  collected in document order. */
class OdtExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        val xml = readZipEntry(bytes, "content.xml")
        val doc = xml?.let { parseXml(it) }
        if (doc == null) {
            return ExtractedDocument(fileName, DocumentFormat.ODT, emptyList(), emptyList())
        }
        val paragraphs = mutableListOf<DocParagraph>()
        val tables = mutableListOf<DocTable>()
        visit(doc.documentElement, paragraphs, tables)
        return ExtractedDocument(fileName, DocumentFormat.ODT, paragraphs, tables)
    }

    private fun visit(
        element: Element,
        paragraphs: MutableList<DocParagraph>,
        tables: MutableList<DocTable>
    ) {
        when (element.localName()) {
            "p" -> {
                val text = normaliseWhitespace(paragraphText(element))
                if (text.isNotEmpty()) {
                    paragraphs.add(DocParagraph(text, paragraphs.size))
                }
            }
            "h" -> {
                val text = normaliseWhitespace(paragraphText(element))
                if (text.isNotEmpty()) {
                    val level = element.getAttribute("text:outline-level")
                        .toIntOrNull()
                    paragraphs.add(DocParagraph(text, paragraphs.size, level))
                }
            }
            "table" -> tables.add(tableOf(element, tables.size))
            else -> element.childElements().forEach { visit(it, paragraphs, tables) }
        }
    }

    /** ODT text lives in plain text nodes, with line-break, tab and
     *  space elements between them. */
    private fun paragraphText(element: Element): String {
        val out = StringBuilder()
        fun walk(node: Node) {
            when {
                node.nodeType == Node.TEXT_NODE -> out.append(node.nodeValue)
                node is Element -> when (node.localName()) {
                    "line-break" -> out.append(' ')
                    "tab" -> out.append(' ')
                    "s" -> {
                        val count = node.getAttribute("text:c")
                            .toIntOrNull() ?: 1
                        repeat(count) { out.append(' ') }
                    }
                    else -> node.childNodes.let { children ->
                        for (i in 0 until children.length) walk(children.item(i))
                    }
                }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) walk(children.item(i))
        return out.toString()
    }

    private fun tableOf(table: Element, index: Int): DocTable {
        // Rows are direct children, except header rows, which ODT wraps
        // in a table-header-rows element.
        val rowElements = mutableListOf<Element>()
        for (child in table.childElements()) {
            when (child.localName()) {
                "table-row" -> rowElements.add(child)
                "table-header-rows" -> rowElements.addAll(
                    child.childElements().filter { it.localName() == "table-row" }
                )
            }
        }
        val rows = rowElements
            .map { row ->
                row.childElements()
                    .filter {
                        it.localName() == "table-cell" ||
                            it.localName() == "covered-table-cell"
                    }
                    .map { cell ->
                        val texts = mutableListOf<String>()
                        for (desc in cell.descendants()) {
                            if (desc.localName() == "p") {
                                val t = normaliseWhitespace(paragraphText(desc))
                                if (t.isNotEmpty()) texts.add(t)
                            }
                        }
                        texts.joinToString(" ")
                    }
            }
        return DocTable(rows, index)
    }
}

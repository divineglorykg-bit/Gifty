package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat
import org.w3c.dom.Element

/** DOCX extraction via OOXML zip/XML (no POI dependency, by design).
 *  Paragraphs (w:p) and tables (w:tbl) are collected in document order.
 *  Field codes (w:instrText) are skipped. Nested tables inside table
 *  cells are not expanded — a documented limitation. */
class DocxExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        val xml = readZipEntry(bytes, "word/document.xml")
        val doc = xml?.let { parseXml(it) }
        if (doc == null) {
            return ExtractedDocument(fileName, DocumentFormat.DOCX, emptyList(), emptyList())
        }
        val paragraphs = mutableListOf<DocParagraph>()
        val tables = mutableListOf<DocTable>()
        visit(doc.documentElement, paragraphs, tables)
        val appXml = readZipEntry(bytes, "docProps/app.xml")
        val pages = appXml?.let {
            Regex("<Pages>(\\d+)</Pages>")
                .find(it.toString(Charsets.UTF_8))
                ?.groupValues?.get(1)?.toIntOrNull()
        }
        return ExtractedDocument(fileName, DocumentFormat.DOCX, paragraphs, tables, pages)
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
                    paragraphs.add(
                        DocParagraph(text, paragraphs.size, headingLevel(element))
                    )
                }
            }
            "tbl" -> tables.add(tableOf(element, tables.size))
            else -> element.childElements().forEach { visit(it, paragraphs, tables) }
        }
    }

    private fun paragraphText(p: Element): String {
        val out = StringBuilder()
        for (node in p.descendants()) {
            when (node.localName()) {
                "t" -> out.append(node.textContent)
                "tab" -> out.append(' ')
                "br" -> out.append(' ')
            }
        }
        return out.toString()
    }

    private fun headingLevel(p: Element): Int? {
        val pPr = p.childElements().firstOrNull { it.localName() == "pPr" }
            ?: return null
        val style = pPr.childElements().firstOrNull { it.localName() == "pStyle" }
            ?: return null
        val value = style.getAttribute("w:val")
        if (value.startsWith("Heading")) {
            return value.removePrefix("Heading").toIntOrNull()
        }
        return null
    }

    private fun tableOf(tbl: Element, index: Int): DocTable {
        val rows = tbl.childElements()
            .filter { it.localName() == "tr" }
            .map { tr ->
                tr.childElements()
                    .filter { it.localName() == "tc" }
                    .map { tc ->
                        val cellParagraphs = mutableListOf<String>()
                        for (desc in tc.descendants()) {
                            if (desc.localName() == "p") {
                                val t = normaliseWhitespace(paragraphText(desc))
                                if (t.isNotEmpty()) cellParagraphs.add(t)
                            }
                        }
                        cellParagraphs.joinToString(" ")
                    }
            }
        return DocTable(rows, index)
    }
}

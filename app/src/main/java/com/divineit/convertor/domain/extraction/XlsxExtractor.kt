package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat
import org.w3c.dom.Element

/** XLSX extraction via OOXML zip/XML: shared strings plus one table per
 *  worksheet, in sheet order. Cells are emitted in document order per
 *  row; sparse rows are NOT gap-filled (a documented limitation — a
 *  missing cell and an empty cell are indistinguishable here). Formulas
 *  are reported by their cached values, never evaluated. */
class XlsxExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        val shared = sharedStrings(bytes)
        val sheetNames = zipEntryNames(bytes)
            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { Regex("sheet(\\d+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
        val tables = sheetNames.mapIndexed { index, name ->
            val xml = readZipEntry(bytes, name)
            val doc = xml?.let { parseXml(it) }
            val rows = mutableListOf<List<String>>()
            if (doc != null) {
                for (row in doc.documentElement.descendants()) {
                    if (row.localName() != "row") continue
                    val cells = row.childElements()
                        .filter { it.localName() == "c" }
                        .map { cellText(it, shared) }
                    rows.add(cells)
                }
            }
            DocTable(rows, index)
        }
        return ExtractedDocument(fileName, DocumentFormat.XLSX, emptyList(), tables)
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val xml = readZipEntry(bytes, "xl/sharedStrings.xml") ?: return emptyList()
        val doc = parseXml(xml) ?: return emptyList()
        val out = mutableListOf<String>()
        for (si in doc.documentElement.descendants()) {
            if (si.localName() != "si") continue
            val text = StringBuilder()
            for (t in si.descendants()) {
                if (t.localName() == "t") text.append(t.textContent)
            }
            out.add(text.toString())
        }
        return out
    }

    private fun cellText(cell: Element, shared: List<String>): String {
        val type = cell.getAttribute("t")
        val valueElement = cell.childElements().firstOrNull { it.localName() == "v" }
        return when (type) {
            "s" -> {
                val index = valueElement?.textContent?.toIntOrNull()
                if (index != null && index in shared.indices) shared[index] else ""
            }
            "inlineStr" -> {
                val text = StringBuilder()
                val isElement = cell.childElements()
                    .firstOrNull { it.localName() == "is" }
                if (isElement != null) {
                    for (t in isElement.descendants()) {
                        if (t.localName() == "t") text.append(t.textContent)
                    }
                }
                text.toString()
            }
            else -> valueElement?.textContent ?: ""
        }
    }
}

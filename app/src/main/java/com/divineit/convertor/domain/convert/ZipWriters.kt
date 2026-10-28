package com.divineit.convertor.domain.convert

import com.divineit.convertor.domain.extraction.ExtractedDocument
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zip-package writers (Phase 5): ODT and XLSX renderings of an
 * [ExtractedDocument], built directly as zip/XML like the existing
 * DOCX writer. Both writers round-trip through this app's own ODT and
 * XLSX extractors, which the Phase 5 tests prove.
 */

internal fun xmlEscape(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

private fun ZipOutputStream.entry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

/** ODT: mimetype first and stored (the ODF rule), then the manifest,
 *  minimal styles, and content.xml with paragraphs, headings
 *  (text:outline-level preserved) and tables. */
object OdtWriter {
    private const val MIME = "application/vnd.oasis.opendocument.text"

    fun write(document: ExtractedDocument): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val mimeBytes = MIME.toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            mimeEntry.size = mimeBytes.size.toLong()
            val crc = CRC32()
            crc.update(mimeBytes)
            mimeEntry.crc = crc.value
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()
            zip.entry("META-INF/manifest.xml", manifest())
            zip.entry("styles.xml", styles())
            zip.entry("content.xml", content(document))
        }
        return out.toByteArray()
    }

    private fun manifest(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
<manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>
<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
<manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
</manifest:manifest>"""

    private fun styles(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" office:version="1.2"><office:styles/></office:document-styles>"""

    private fun content(document: ExtractedDocument): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append(
            """<office:document-content""" +
                """ xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"""" +
                """ xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"""" +
                """ xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"""" +
                """ office:version="1.2">"""
        )
        append("<office:body><office:text>")
        for (paragraph in document.paragraphs) {
            val level = paragraph.headingLevel
            if (level != null && level in 1..9) {
                append("<text:h text:outline-level=\"").append(level).append("\">")
                    .append(xmlEscape(paragraph.text)).append("</text:h>")
            } else {
                append("<text:p>").append(xmlEscape(paragraph.text)).append("</text:p>")
            }
        }
        for (table in document.tables) {
            append("<table:table>")
            for (row in table.rows) {
                append("<table:table-row>")
                for (cell in row) {
                    append("<table:table-cell><text:p>")
                        .append(xmlEscape(cell))
                        .append("</text:p></table:table-cell>")
                }
                append("</table:table-row>")
            }
            append("</table:table>")
        }
        append("</office:text></office:body></office:document-content>")
    }
}

/** XLSX: one worksheet per table (a paragraphs-only document becomes a
 *  single one-column sheet named "Document"). All cells are shared
 *  strings — the extraction model holds text, and numbers are never
 *  re-typed or invented. */
object XlsxWriter {
    fun write(document: ExtractedDocument): ByteArray {
        val sheets: List<Pair<String, List<List<String>>>> =
            if (document.tables.isNotEmpty()) {
                document.tables.mapIndexed { i, table ->
                    "Table ${i + 1}" to table.rows
                }
            } else {
                listOf("Document" to document.paragraphs.map { listOf(it.text) })
            }
        val shared = LinkedHashMap<String, Int>()
        var totalRefs = 0
        for ((_, rows) in sheets) {
            for (row in rows) {
                for (cell in row) {
                    if (cell !in shared) shared[cell] = shared.size
                    totalRefs++
                }
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", contentTypes(sheets.size))
            zip.entry("_rels/.rels", rootRels())
            zip.entry("xl/workbook.xml", workbook(sheets.map { it.first }))
            zip.entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.entry("xl/sharedStrings.xml", sharedStrings(shared, totalRefs))
            sheets.forEachIndexed { i, (_, rows) ->
                zip.entry("xl/worksheets/sheet${i + 1}.xml", sheet(rows, shared))
            }
        }
        return out.toByteArray()
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbook(names: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        names.forEachIndexed { i, name ->
            append("<sheet name=\"").append(xmlEscape(name))
                .append("\" sheetId=\"").append(i + 1)
                .append("\" r:id=\"rId").append(i + 1).append("\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("</Relationships>")
    }

    private fun sharedStrings(shared: Map<String, Int>, totalRefs: Int): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
            append(" count=\"").append(totalRefs)
                .append("\" uniqueCount=\"").append(shared.size).append("\">")
            for ((text, _) in shared.entries.sortedBy { it.value }) {
                append("<si><t>").append(xmlEscape(text)).append("</t></si>")
            }
            append("</sst>")
        }

    private fun sheet(rows: List<List<String>>, shared: Map<String, Int>): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { r, row ->
                append("<row r=\"").append(r + 1).append("\">")
                row.forEachIndexed { c, cell ->
                    append("<c r=\"").append(columnName(c)).append(r + 1)
                        .append("\" t=\"s\"><v>").append(shared.getValue(cell))
                        .append("</v></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

    private fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            out.append(('A' + rem))
            n = (n - 1) / 26
        }
        return out.reverse().toString()
    }
}

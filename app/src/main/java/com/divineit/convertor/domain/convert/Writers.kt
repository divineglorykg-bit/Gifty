package com.divineit.convertor.domain.convert

import com.divineit.convertor.domain.extraction.DocTable
import com.divineit.convertor.domain.extraction.ExtractedDocument

/**
 * Plain-format writers (Phase 5): TXT, CSV, HTML and RTF renderings of
 * an [ExtractedDocument]. All writers are pure Kotlin and follow the
 * extraction model: paragraphs first (headings keep their level where
 * the format supports it), then tables. Nothing is invented — a format
 * that cannot hold a structure says so in its writer's contract rather
 * than silently dropping it.
 */

/** TXT: paragraphs separated by blank lines; tables as tab-separated
 *  rows under a "Table N" marker line. */
object TxtWriter {
    fun write(document: ExtractedDocument): ByteArray {
        val out = StringBuilder()
        document.paragraphs.forEachIndexed { i, paragraph ->
            if (i > 0) out.append("\n\n")
            out.append(paragraph.text)
        }
        for (table in document.tables) {
            if (out.isNotEmpty()) out.append("\n\n")
            out.append("Table ").append(table.index + 1).append('\n')
            table.rows.forEachIndexed { r, row ->
                if (r > 0) out.append('\n')
                out.append(row.joinToString("\t"))
            }
        }
        return out.toString().toByteArray(Charsets.UTF_8)
    }
}

/** CSV (RFC 4180): the document's tables, in order, separated by one
 *  blank row. A document without tables becomes a single column of its
 *  paragraphs — stated here because CSV has no paragraph concept. */
object CsvWriter {
    fun write(document: ExtractedDocument): ByteArray {
        val rows = mutableListOf<List<String>>()
        if (document.tables.isNotEmpty()) {
            document.tables.forEachIndexed { i, table ->
                if (i > 0) rows.add(emptyList())
                rows.addAll(table.rows)
            }
        } else {
            document.paragraphs.forEach { rows.add(listOf(it.text)) }
        }
        val out = StringBuilder()
        for (row in rows) {
            out.append(row.joinToString(",") { field(it) })
            out.append("\r\n")
        }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

/** HTML: headings as h1–h9, paragraphs as p, tables as table/tr/td.
 *  All text is escaped; the document title is the source name. */
object HtmlWriter {
    fun write(document: ExtractedDocument): ByteArray {
        val out = StringBuilder()
        out.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
        out.append("<title>").append(escape(document.sourceName)).append("</title>\n")
        out.append("</head>\n<body>\n")
        for (paragraph in document.paragraphs) {
            val level = paragraph.headingLevel
            if (level != null && level in 1..9) {
                out.append("<h").append(level).append('>')
                    .append(escape(paragraph.text))
                    .append("</h").append(level).append(">\n")
            } else {
                out.append("<p>").append(escape(paragraph.text)).append("</p>\n")
            }
        }
        for (table in document.tables) {
            out.append("<table>\n")
            for (row in table.rows) {
                out.append("<tr>")
                for (cell in row) {
                    out.append("<td>").append(escape(cell)).append("</td>")
                }
                out.append("</tr>\n")
            }
            out.append("</table>\n")
        }
        out.append("</body>\n</html>\n")
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}

/** RTF: one \par per paragraph (headings in a larger bold size), table
 *  rows as tab-separated lines. Non-ASCII characters are emitted as
 *  \uN? Unicode escapes, so characters such as ɛ (U+025B) and ɔ
 *  (U+0254) survive exactly — never degraded to e/o. RTF \u takes the
 *  signed 16-bit value of the UTF-16 code unit; astral characters are
 *  written as their surrogate pair. */
object RtfWriter {
    fun write(document: ExtractedDocument): ByteArray {
        val out = StringBuilder()
        out.append("{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Times New Roman;}}")
        out.append("\\f0\\fs24\n")
        for (paragraph in document.paragraphs) {
            val level = paragraph.headingLevel
            if (level != null && level in 1..9) {
                val size = 36 - 2 * level // fs is in half-points: 34..20
                out.append("{\\b\\fs").append(size).append(' ')
                    .append(escape(paragraph.text)).append("}\\par\n")
            } else {
                out.append(escape(paragraph.text)).append("\\par\n")
            }
        }
        for (table in document.tables) {
            out.append("\\par\n")
            for (row in table.rows) {
                out.append(row.joinToString("\\tab ") { escape(it) })
                out.append("\\par\n")
            }
        }
        out.append("}")
        return out.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            when {
                c == '\\' -> append("\\\\")
                c == '{' -> append("\\{")
                c == '}' -> append("\\}")
                c.code in 32..126 -> append(c)
                else -> {
                    // Signed 16-bit value, as RTF requires.
                    append("\\u").append(c.code.toShort().toInt()).append('?')
                }
            }
        }
    }
}

/** Shared table helper for the zip writers: the widest row width. */
internal fun DocTable.columnCount(): Int = rows.maxOfOrNull { it.size } ?: 0

package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat

/** RFC 4180 CSV parsing: quoted fields, doubled-quote escapes, commas,
 *  and newlines inside quoted fields. The result is a single table; no
 *  header assumption is made — every row is data. Paragraphs are empty:
 *  CSV comparison is cell-level (see the diff engine). */
class CsvExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        val text = String(bytes, Charsets.UTF_8)
        val rows = parse(text)
        val tables = if (rows.isEmpty()) {
            emptyList()
        } else {
            listOf(DocTable(rows, 0))
        }
        return ExtractedDocument(fileName, DocumentFormat.CSV, emptyList(), tables)
    }

    internal fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var fieldStarted = false
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i += 2
                        } else {
                            inQuotes = false
                            i++
                        }
                    } else {
                        field.append(c)
                        i++
                    }
                }
                c == '"' && !fieldStarted -> {
                    inQuotes = true
                    fieldStarted = true
                    i++
                }
                c == ',' -> {
                    row.add(field.toString())
                    field.clear()
                    fieldStarted = false
                    i++
                }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                        i++
                    }
                    row.add(field.toString())
                    field.clear()
                    fieldStarted = false
                    rows.add(row)
                    row = mutableListOf()
                    i++
                }
                else -> {
                    field.append(c)
                    fieldStarted = true
                    i++
                }
            }
        }
        // Trailing row only if the input did not end with a newline.
        if (field.isNotEmpty() || fieldStarted || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}

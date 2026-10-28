package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat

/** Basic RTF text extraction (pure Kotlin). Handles plain text, \par /
 *  \line / \tab, \'hh hex escapes and \uN Unicode escapes with their
 *  fallback character. Font tables, colour tables, stylesheets, pictures
 *  and other destinations are skipped whole. Tables, images and complex
 *  formatting are NOT recovered — a documented Phase 3 limitation. */
class RtfExtractor : DocumentExtractor {

    private val ignorable = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object",
        "header", "footer", "footnote", "xml", "datastore"
    )

    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        val text = String(bytes, Charsets.ISO_8859_1)
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '{' -> {
                    // Peek a destination control word, e.g. {\fonttbl.
                    if (i + 1 < text.length && text[i + 1] == '\\') {
                        var j = i + 2
                        if (j < text.length && text[j] == '*') j++
                        val start = j
                        while (j < text.length && text[j].isLetter()) j++
                        if (text.substring(start, j) in ignorable) {
                            i = skipGroup(text, i)
                            continue
                        }
                    }
                    i++
                }
                c == '}' -> i++
                c == '\\' -> {
                    if (i + 1 >= text.length) {
                        i++
                        continue
                    }
                    val next = text[i + 1]
                    when {
                        next == '\\' || next == '{' || next == '}' -> {
                            out.append(next)
                            i += 2
                        }
                        next == '\'' -> {
                            if (i + 3 < text.length) {
                                text.substring(i + 2, i + 4).toIntOrNull(16)
                                    ?.let { out.append(it.toChar()) }
                                i += 4
                            } else {
                                i = text.length
                            }
                        }
                        next == '*' -> i += 2
                        next.isLetter() -> {
                            var j = i + 1
                            while (j < text.length && text[j].isLetter()) j++
                            val word = text.substring(i + 1, j)
                            var k = j
                            var negative = false
                            if (k < text.length && text[k] == '-') {
                                negative = true
                                k++
                            }
                            val numStart = k
                            while (k < text.length && text[k].isDigit()) k++
                            val num = if (k > numStart) {
                                text.substring(numStart, k).toIntOrNull()
                                    ?.let { if (negative) -it else it }
                            } else {
                                null
                            }
                            when (word) {
                                "par", "line" -> out.append('\n')
                                "tab" -> out.append(' ')
                                "u" -> {
                                    if (num != null) {
                                        val code = ((num % 65536) + 65536) % 65536
                                        out.append(String(Character.toChars(code)))
                                    }
                                    // Delimiter space, or else one fallback char.
                                    if (k < text.length && text[k] == ' ') {
                                        k++
                                    } else if (k < text.length) {
                                        k++
                                    }
                                }
                                else -> {
                                    if (k < text.length && text[k] == ' ') k++
                                }
                            }
                            i = k
                        }
                        else -> i += 2
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        val paragraphs = splitParagraphs(out.toString())
            .mapIndexed { index, t -> DocParagraph(t, index) }
        return ExtractedDocument(fileName, DocumentFormat.RTF, paragraphs, emptyList())
    }

    /** Index just past the group starting at `start` (which points at '{'). */
    private fun skipGroup(text: String, start: Int): Int {
        var depth = 0
        var j = start
        while (j < text.length) {
            when (text[j]) {
                '\\' -> j += 2
                '{' -> {
                    depth++
                    j++
                }
                '}' -> {
                    depth--
                    j++
                    if (depth == 0) return j
                }
                else -> j++
            }
        }
        return j
    }
}

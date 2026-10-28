package com.divineit.convertor.domain.convert

import com.divineit.convertor.domain.extraction.DocParagraph
import com.divineit.convertor.domain.extraction.ExtractedDocument

/**
 * Document editor operations (Phase 5): pure functions over the
 * extracted document's paragraphs. Every operation returns a new
 * document with paragraph indices re-assigned in order, so the editor
 * screen can treat its state as a single immutable value and undo is
 * just "keep the previous value". Tables are carried through
 * unchanged — the editor edits paragraph text; table cell editing is
 * not part of this phase and the UI says so.
 *
 * Out-of-range indices are no-ops, never crashes.
 */
object DocumentEditor {

    private fun reindex(
        document: ExtractedDocument,
        paragraphs: List<DocParagraph>
    ): ExtractedDocument =
        document.copy(
            paragraphs = paragraphs.mapIndexed { i, p -> p.copy(index = i) }
        )

    fun updateParagraph(
        document: ExtractedDocument,
        index: Int,
        newText: String
    ): ExtractedDocument {
        if (index !in document.paragraphs.indices) return document
        val paragraphs = document.paragraphs.mapIndexed { i, p ->
            if (i == index) p.copy(text = newText) else p
        }
        return reindex(document, paragraphs)
    }

    /** Inserts a paragraph after [afterIndex]; -1 inserts at the start. */
    fun insertParagraph(
        document: ExtractedDocument,
        afterIndex: Int,
        text: String = "",
        headingLevel: Int? = null
    ): ExtractedDocument {
        val at = (afterIndex + 1).coerceIn(0, document.paragraphs.size)
        val paragraphs = document.paragraphs.toMutableList()
        paragraphs.add(at, DocParagraph(text, at, headingLevel))
        return reindex(document, paragraphs)
    }

    fun deleteParagraph(
        document: ExtractedDocument,
        index: Int
    ): ExtractedDocument {
        if (index !in document.paragraphs.indices) return document
        val paragraphs = document.paragraphs.toMutableList()
        paragraphs.removeAt(index)
        return reindex(document, paragraphs)
    }

    /** Moves a paragraph by [delta] places (normally -1 or +1);
     *  clamped at both ends. */
    fun moveParagraph(
        document: ExtractedDocument,
        index: Int,
        delta: Int
    ): ExtractedDocument {
        if (index !in document.paragraphs.indices) return document
        val target = (index + delta).coerceIn(0, document.paragraphs.lastIndex)
        if (target == index) return document
        val paragraphs = document.paragraphs.toMutableList()
        val moved = paragraphs.removeAt(index)
        paragraphs.add(target, moved)
        return reindex(document, paragraphs)
    }

    /** Sets or clears (null) a paragraph's heading level, 1–9. */
    fun setHeading(
        document: ExtractedDocument,
        index: Int,
        level: Int?
    ): ExtractedDocument {
        if (index !in document.paragraphs.indices) return document
        val paragraphs = document.paragraphs.mapIndexed { i, p ->
            if (i == index) p.copy(headingLevel = level?.coerceIn(1, 9)) else p
        }
        return reindex(document, paragraphs)
    }

    /**
     * Literal find-and-replace across paragraph texts (tables are not
     * touched). Returns the new document and the exact number of
     * replacements made — counted, never estimated. An empty search
     * string replaces nothing.
     */
    fun findReplace(
        document: ExtractedDocument,
        find: String,
        replace: String,
        ignoreCase: Boolean = false
    ): Pair<ExtractedDocument, Int> {
        if (find.isEmpty()) return document to 0
        var total = 0
        val paragraphs = document.paragraphs.map { paragraph ->
            val count = countOccurrences(paragraph.text, find, ignoreCase)
            if (count == 0) {
                paragraph
            } else {
                total += count
                paragraph.copy(text = paragraph.text.replace(find, replace, ignoreCase))
            }
        }
        if (total == 0) return document to 0
        return reindex(document, paragraphs) to total
    }

    private fun countOccurrences(
        text: String,
        find: String,
        ignoreCase: Boolean
    ): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = text.indexOf(find, from, ignoreCase)
            if (at < 0) return count
            count++
            from = at + find.length
        }
    }

    /** Words across all paragraph texts (a word is a maximal run of
     *  non-whitespace). Tables are excluded and the UI labels it so. */
    fun wordCount(document: ExtractedDocument): Int =
        document.paragraphs.sumOf { paragraph ->
            paragraph.text.split(Regex("\\s+")).count { it.isNotEmpty() }
        }

    fun characterCount(document: ExtractedDocument): Int =
        document.paragraphs.sumOf { it.text.length }
}

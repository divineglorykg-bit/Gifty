package com.divineit.convertor.domain.correction

import com.divineit.convertor.domain.alignment.ParagraphAligner
import com.divineit.convertor.domain.extraction.DocParagraph
import com.divineit.convertor.domain.extraction.DocTable
import com.divineit.convertor.domain.extraction.ExtractedDocument

/**
 * Builds the corrected document from the product document plus every
 * correction whose status is APPLIED. Undo needs no separate machinery:
 * change a status back and rebuild — the corrected document is always a
 * pure function of (original, product, correction statuses).
 *
 * Paragraphs are rebuilt by walking the same alignment the planner
 * used, so inserted paragraphs land in original order, removed
 * paragraphs disappear, and fragment edits splice into the exact
 * character offsets the tokenizer recorded.
 */
object CorrectionApplier {

    fun buildCorrected(
        orig: ExtractedDocument,
        prod: ExtractedDocument,
        corrections: List<PlannedCorrection>
    ): ExtractedDocument {
        val applied = corrections.filter {
            it.status == CorrectionStatus.APPLIED
        }
        val fragmentEdits = mutableMapOf<Int, MutableList<FragmentEdit>>()
        val insertParagraphs = mutableSetOf<Int>()
        val removeParagraphs = mutableSetOf<Int>()
        val setCells = mutableListOf<CorrectionTarget.SetCell>()
        val insertRows = mutableListOf<CorrectionTarget.InsertRow>()
        val removeRows = mutableListOf<CorrectionTarget.RemoveRow>()
        val insertTables = mutableSetOf<Int>()
        val removeTables = mutableSetOf<Int>()
        for (correction in applied) {
            when (val target = correction.target) {
                is CorrectionTarget.Fragments ->
                    for (edit in target.edits) {
                        fragmentEdits.getOrPut(edit.productParagraphIndex) {
                            mutableListOf()
                        }.add(edit)
                    }
                is CorrectionTarget.InsertParagraph ->
                    insertParagraphs.add(target.originalParagraphIndex)
                is CorrectionTarget.RemoveParagraph ->
                    removeParagraphs.add(target.productParagraphIndex)
                is CorrectionTarget.SetCell -> setCells.add(target)
                is CorrectionTarget.InsertRow -> insertRows.add(target)
                is CorrectionTarget.RemoveRow -> removeRows.add(target)
                is CorrectionTarget.InsertTable ->
                    insertTables.add(target.originalTableIndex)
                is CorrectionTarget.RemoveTable ->
                    removeTables.add(target.productTableIndex)
            }
        }

        val pairings = ParagraphAligner.align(
            orig.paragraphs.map { it.text },
            prod.paragraphs.map { it.text }
        )
        val paragraphs = mutableListOf<DocParagraph>()
        for (pairing in pairings) {
            val o = pairing.origIndex?.let { orig.paragraphs[it] }
            val p = pairing.prodIndex?.let { prod.paragraphs[it] }
            when {
                o != null && p != null -> {
                    val edits = fragmentEdits[p.index].orEmpty()
                    val text = if (edits.isEmpty()) {
                        p.text
                    } else {
                        applyFragmentEdits(p.text, edits)
                    }
                    paragraphs.add(
                        DocParagraph(text, paragraphs.size, p.headingLevel)
                    )
                }
                p == null && o != null -> {
                    if (o.index in insertParagraphs) {
                        paragraphs.add(
                            DocParagraph(o.text, paragraphs.size, o.headingLevel)
                        )
                    }
                }
                o == null && p != null -> {
                    if (p.index !in removeParagraphs) {
                        paragraphs.add(
                            DocParagraph(p.text, paragraphs.size, p.headingLevel)
                        )
                    }
                }
            }
        }

        val tables = mutableListOf<DocTable>()
        val tableCount = maxOf(orig.tables.size, prod.tables.size)
        for (ti in 0 until tableCount) {
            val ot = orig.tables.getOrNull(ti)
            val pt = prod.tables.getOrNull(ti)
            when {
                ot != null && pt != null -> {
                    val rows = pt.rows
                        .map { it.toMutableList() }
                        .toMutableList()
                    for (cell in setCells) {
                        if (cell.tableIndex == ti &&
                            cell.rowIndex in rows.indices &&
                            cell.cellIndex in rows[cell.rowIndex].indices
                        ) {
                            rows[cell.rowIndex][cell.cellIndex] = cell.after
                        }
                    }
                    val removedRowIndices = removeRows
                        .filter { it.tableIndex == ti }
                        .map { it.productRowIndex }
                        .sortedDescending()
                    for (ri in removedRowIndices) {
                        if (ri in rows.indices) rows.removeAt(ri)
                    }
                    val inserts = insertRows
                        .filter { it.tableIndex == ti }
                        .sortedBy { it.originalRowIndex }
                    for (row in inserts) {
                        val position = row.originalRowIndex
                            .coerceIn(0, rows.size)
                        rows.add(position, row.cells.toMutableList())
                    }
                    tables.add(DocTable(rows.map { it.toList() }, tables.size))
                }
                pt == null && ot != null -> {
                    if (ti in insertTables) {
                        tables.add(DocTable(ot.rows, tables.size))
                    }
                }
                ot == null && pt != null -> {
                    if (ti !in removeTables) {
                        tables.add(DocTable(pt.rows, tables.size))
                    }
                }
            }
        }

        return ExtractedDocument(
            prod.sourceName,
            prod.format,
            paragraphs,
            tables,
            prod.pageCount
        )
    }

    /** Splices edits in descending offset order, then re-normalises
     *  spacing exactly as extraction does (single spaces, trimmed), so a
     *  corrected paragraph compares cleanly against the original. */
    private fun applyFragmentEdits(
        text: String,
        edits: List<FragmentEdit>
    ): String {
        var result = text
        for (edit in edits.sortedByDescending { it.charStart }) {
            val start = edit.charStart.coerceIn(0, result.length)
            val end = edit.charEnd.coerceIn(start, result.length)
            result = result.substring(0, start) +
                edit.replacement +
                result.substring(end)
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }
}

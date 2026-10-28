package com.divineit.convertor.domain.correction

import com.divineit.convertor.domain.alignment.ParagraphAligner
import com.divineit.convertor.domain.diff.Confidence
import com.divineit.convertor.domain.diff.DifferenceCategory
import com.divineit.convertor.domain.diff.MyersDiff
import com.divineit.convertor.domain.diff.Token
import com.divineit.convertor.domain.diff.TokenKind
import com.divineit.convertor.domain.diff.Tokenizer
import com.divineit.convertor.domain.extraction.DocParagraph
import com.divineit.convertor.domain.extraction.ExtractedDocument
import com.divineit.convertor.domain.unicode.UnicodeAnalyzer

/**
 * Turns a comparison into a list of planned corrections. The planner
 * re-derives the alignment and token runs itself, so every correction
 * carries exact character offsets (via the tokenizer) rather than
 * guessing positions from the report's display text.
 *
 * Safeguard policy (AUTO-CORRECT): a correction is low-risk only when it
 * changes exactly one token, restores the original text exactly, is
 * HIGH confidence, and its category is one of SUBSTITUTION,
 * UNICODE_SUBSTITUTION, PUNCTUATION, DUPLICATION, DELETION or INSERTION.
 * Number changes, multi-token passages, transpositions, word-order
 * moves, paragraph changes and table structure always require
 * individual approval. Column-count differences get no correction at
 * all (columns are never invented). Replacement-character paragraphs
 * are corrected through their token corrections; the UNRESOLVED flag
 * itself plans nothing.
 */
object CorrectionPlanner {

    fun plan(
        orig: ExtractedDocument,
        prod: ExtractedDocument
    ): List<PlannedCorrection> {
        val out = mutableListOf<PlannedCorrection>()
        var nextId = 1
        fun id() = nextId++

        val pairings = ParagraphAligner.align(
            orig.paragraphs.map { it.text },
            prod.paragraphs.map { it.text }
        )
        for (pairing in pairings) {
            val o = pairing.origIndex?.let { orig.paragraphs[it] }
            val p = pairing.prodIndex?.let { prod.paragraphs[it] }
            when {
                o == null && p != null -> out.add(
                    PlannedCorrection(
                        id(), DifferenceCategory.INSERTION, Confidence.HIGH,
                        requiresIndividualApproval = true,
                        locationLabel = "Paragraph ${p.index + 1} (product)",
                        before = p.text, after = "",
                        evidence = "Paragraph present in the product but " +
                            "not in the original.",
                        target = CorrectionTarget.RemoveParagraph(p.index)
                    )
                )
                p == null && o != null -> out.add(
                    PlannedCorrection(
                        id(), DifferenceCategory.DELETION, Confidence.HIGH,
                        requiresIndividualApproval = true,
                        locationLabel = "Paragraph ${o.index + 1} (original)",
                        before = "", after = o.text,
                        evidence = "Paragraph missing in the product.",
                        target = CorrectionTarget.InsertParagraph(o.index, o.text)
                    )
                )
                o != null && p != null && o.text != p.text ->
                    out.addAll(planParagraph(o, p) { id() })
            }
        }

        out.addAll(planTables(orig, prod) { id() })
        return out
    }

    private data class Run(
        val deletes: List<Int>,
        val inserts: List<Int>,
        val equalsBefore: Int,
        val equalBeforeOrigIndex: Int?,
        val equalBeforeProdIndex: Int?
    )

    private data class Draft(
        val run: Run,
        val category: DifferenceCategory,
        val confidence: Confidence,
        val requiresIndividualApproval: Boolean,
        val before: String,
        val after: String,
        val evidence: String,
        val target: CorrectionTarget
    )

    private fun planParagraph(
        o: DocParagraph,
        p: DocParagraph,
        nextId: () -> Int
    ): List<PlannedCorrection> {
        val oTokens = Tokenizer.tokenize(o.text)
        val pTokens = Tokenizer.tokenize(p.text)
        val ops = MyersDiff.diff(
            oTokens.map { it.text },
            pTokens.map { it.text }
        )
        val runs = mutableListOf<Run>()
        var deletes = mutableListOf<Int>()
        var inserts = mutableListOf<Int>()
        var equalsSinceLastRun = 0
        var lastEqualOrig: Int? = null
        var lastEqualProd: Int? = null
        fun flush() {
            if (deletes.isNotEmpty() || inserts.isNotEmpty()) {
                runs.add(
                    Run(
                        deletes, inserts, equalsSinceLastRun,
                        lastEqualOrig, lastEqualProd
                    )
                )
                deletes = mutableListOf()
                inserts = mutableListOf()
                equalsSinceLastRun = 0
            }
        }
        for (op in ops) {
            when (op) {
                is MyersDiff.Op.Equal -> {
                    flush()
                    equalsSinceLastRun++
                    lastEqualOrig = op.origIndex
                    lastEqualProd = op.prodIndex
                }
                is MyersDiff.Op.Delete -> deletes.add(op.origIndex)
                is MyersDiff.Op.Insert -> inserts.add(op.prodIndex)
            }
        }
        flush()

        val drafts = mutableListOf<Draft>()
        for (run in runs) {
            drafts.add(classifyRun(run, o, p, oTokens, pTokens))
        }

        // Transposition merge (mirrors DiffEngine): a single-token
        // deletion and a single-token insertion of the same word,
        // separated by exactly one equal token, is one swap.
        val afterTransposition = mutableListOf<Draft>()
        var i = 0
        while (i < drafts.size) {
            val first = drafts[i]
            val second = drafts.getOrNull(i + 1)
            val merged = if (second != null) {
                mergeTransposition(first, second, o, p, oTokens, pTokens)
            } else {
                null
            }
            if (merged != null) {
                afterTransposition.add(merged)
                i += 2
            } else {
                afterTransposition.add(first)
                i++
            }
        }

        // Moved-block merge: identical token sequences (length >= 2)
        // deleted at one place and inserted at another are one move.
        val deletionDrafts = afterTransposition.filter {
            it.category == DifferenceCategory.DELETION &&
                it.run.deletes.size >= 2
        }
        val insertionDrafts = afterTransposition.filter {
            it.category == DifferenceCategory.INSERTION &&
                it.run.inserts.size >= 2
        }
        val usedDeletions = mutableSetOf<Draft>()
        val usedInsertions = mutableSetOf<Draft>()
        val movedDrafts = mutableListOf<Draft>()
        for (del in deletionDrafts) {
            val delTexts = del.run.deletes.map { oTokens[it].text }
            val match = insertionDrafts.firstOrNull {
                it !in usedInsertions &&
                    it.run.inserts.map { idx -> pTokens[idx].text } == delTexts
            } ?: continue
            usedDeletions.add(del)
            usedInsertions.add(match)
            val delEdits = (del.target as CorrectionTarget.Fragments).edits
            val insEdits = (match.target as CorrectionTarget.Fragments).edits
            movedDrafts.add(
                Draft(
                    del.run,
                    DifferenceCategory.WORD_ORDER, Confidence.MEDIUM,
                    requiresIndividualApproval = true,
                    before = delTexts.joinToString(" "),
                    after = delTexts.joinToString(" "),
                    evidence = "Block moved within the paragraph.",
                    target = CorrectionTarget.Fragments(insEdits + delEdits)
                )
            )
        }
        val finalDrafts = afterTransposition.filter {
            it !in usedDeletions && it !in usedInsertions
        } + movedDrafts

        return finalDrafts.map { draft ->
            PlannedCorrection(
                nextId(), draft.category, draft.confidence,
                draft.requiresIndividualApproval,
                locationLabel = "Paragraph ${o.index + 1}",
                before = draft.before, after = draft.after,
                evidence = draft.evidence, target = draft.target
            )
        }
    }

    private fun classifyRun(
        run: Run,
        o: DocParagraph,
        p: DocParagraph,
        oTokens: List<Token>,
        pTokens: List<Token>
    ): Draft {
        val oText = run.deletes.joinToString(" ") { oTokens[it].text }
        val pText = run.inserts.joinToString(" ") { pTokens[it].text }
        return when {
            run.inserts.isEmpty() -> {
                // Tokens missing in the product: insert them back.
                val position = insertionPosition(run, pTokens)
                val fragment = o.text.substring(
                    oTokens[run.deletes.first()].start,
                    oTokens[run.deletes.last()].end
                )
                Draft(
                    run, DifferenceCategory.DELETION, Confidence.HIGH,
                    requiresIndividualApproval = run.deletes.size > 1,
                    before = "", after = oText,
                    evidence = "Present in the original, absent in the product.",
                    target = CorrectionTarget.Fragments(
                        listOf(
                            FragmentEdit(
                                p.index, position, position,
                                spaced(fragment, p.text, position)
                            )
                        )
                    )
                )
            }
            run.deletes.isEmpty() -> {
                // Extra tokens in the product: remove them.
                val start = pTokens[run.inserts.first()].start
                val end = pTokens[run.inserts.last()].end
                val isDup = run.inserts.size == 1 &&
                    ((run.inserts[0] > 0 &&
                        pTokens[run.inserts[0] - 1].text == pText) ||
                        (run.inserts.last() + 1 < pTokens.size &&
                            pTokens[run.inserts.last() + 1].text == pText))
                if (isDup) {
                    Draft(
                        run, DifferenceCategory.DUPLICATION, Confidence.HIGH,
                        requiresIndividualApproval = false,
                        before = pText, after = "",
                        evidence = "Word appears twice in the product " +
                            "where the original has it once.",
                        target = CorrectionTarget.Fragments(
                            listOf(FragmentEdit(p.index, start, end, ""))
                        )
                    )
                } else {
                    Draft(
                        run, DifferenceCategory.INSERTION, Confidence.HIGH,
                        requiresIndividualApproval = run.inserts.size > 1,
                        before = pText, after = "",
                        evidence = "Present in the product, absent in " +
                            "the original.",
                        target = CorrectionTarget.Fragments(
                            listOf(FragmentEdit(p.index, start, end, ""))
                        )
                    )
                }
            }
            run.deletes.size == 1 && run.inserts.size == 1 -> {
                val ot = oTokens[run.deletes[0]]
                val pt = pTokens[run.inserts[0]]
                val (category, individual, evidence) = classifySingleToken(ot, pt)
                Draft(
                    run, category, Confidence.HIGH,
                    requiresIndividualApproval = individual,
                    before = pt.text, after = ot.text,
                    evidence = evidence,
                    target = CorrectionTarget.Fragments(
                        listOf(
                            FragmentEdit(
                                p.index, pt.start, pt.end,
                                o.text.substring(ot.start, ot.end)
                            )
                        )
                    )
                )
            }
            else -> {
                Draft(
                    run, DifferenceCategory.SUBSTITUTION, Confidence.MEDIUM,
                    requiresIndividualApproval = true,
                    before = pText, after = oText,
                    evidence = "Passage differs; alignment is less certain " +
                        "across multiple words.",
                    target = CorrectionTarget.Fragments(
                        listOf(
                            FragmentEdit(
                                p.index,
                                pTokens[run.inserts.first()].start,
                                pTokens[run.inserts.last()].end,
                                o.text.substring(
                                    oTokens[run.deletes.first()].start,
                                    oTokens[run.deletes.last()].end
                                )
                            )
                        )
                    )
                )
            }
        }
    }

    /** (category, requiresIndividualApproval, evidence) for a 1-token
     *  substitution, mirroring DiffEngine's classification. */
    private fun classifySingleToken(
        ot: Token,
        pt: Token
    ): Triple<DifferenceCategory, Boolean, String> {
        if (ot.kind == TokenKind.NUMBER && pt.kind == TokenKind.NUMBER) {
            return Triple(
                DifferenceCategory.NUMBER_CHANGE, true, "Number changed."
            )
        }
        if (ot.kind == TokenKind.PUNCT && pt.kind == TokenKind.PUNCT) {
            return Triple(
                DifferenceCategory.PUNCTUATION, false, "Punctuation changed."
            )
        }
        val issue = UnicodeAnalyzer.classifyPair(ot.text, pt.text)
        if (issue == UnicodeAnalyzer.IssueKind.WATCHLIST_SUBSTITUTION) {
            return Triple(
                DifferenceCategory.UNICODE_SUBSTITUTION, false,
                "Character substitution from the OCR watchlist."
            )
        }
        if (issue == UnicodeAnalyzer.IssueKind.NORMALISATION_DIFFERENCE) {
            return Triple(
                DifferenceCategory.UNICODE_SUBSTITUTION, false,
                "Same visible text, different Unicode encoding " +
                    "(composed vs decomposed)."
            )
        }
        return Triple(
            DifferenceCategory.SUBSTITUTION, false, "Word substituted."
        )
    }

    private fun mergeTransposition(
        first: Draft,
        second: Draft,
        o: DocParagraph,
        p: DocParagraph,
        oTokens: List<Token>,
        pTokens: List<Token>
    ): Draft? {
        if (second.run.equalsBefore != 1) return null
        val eqOrig = second.run.equalBeforeOrigIndex ?: return null
        val eqProd = second.run.equalBeforeProdIndex ?: return null
        val firstDelete = first.category == DifferenceCategory.DELETION &&
            first.run.deletes.size == 1 && first.run.inserts.isEmpty()
        val firstInsert = first.category == DifferenceCategory.INSERTION &&
            first.run.inserts.size == 1 && first.run.deletes.isEmpty()
        val secondDelete = second.category == DifferenceCategory.DELETION &&
            second.run.deletes.size == 1 && second.run.inserts.isEmpty()
        val secondInsert = second.category == DifferenceCategory.INSERTION &&
            second.run.inserts.size == 1 && second.run.deletes.isEmpty()
        val word: String
        val origTokensInvolved: List<Int>
        val prodTokensInvolved: List<Int>
        when {
            firstDelete && secondInsert -> {
                word = first.run.deletes.map { oTokens[it].text }.single()
                if (second.run.inserts.map { pTokens[it].text }.single() != word) {
                    return null
                }
                origTokensInvolved = listOf(first.run.deletes[0], eqOrig)
                prodTokensInvolved = listOf(eqProd, second.run.inserts[0])
            }
            firstInsert && secondDelete -> {
                word = first.run.inserts.map { pTokens[it].text }.single()
                if (second.run.deletes.map { oTokens[it].text }.single() != word) {
                    return null
                }
                origTokensInvolved = listOf(eqOrig, second.run.deletes[0])
                prodTokensInvolved = listOf(first.run.inserts[0], eqProd)
            }
            else -> return null
        }
        val origStart = origTokensInvolved.minOf { oTokens[it].start }
        val origEnd = origTokensInvolved.maxOf { oTokens[it].end }
        val prodStart = prodTokensInvolved.minOf { pTokens[it].start }
        val prodEnd = prodTokensInvolved.maxOf { pTokens[it].end }
        val middle = oTokens[eqOrig].text
        val (before, after) = if (firstDelete) {
            "$middle $word" to "$word $middle"
        } else {
            "$word $middle" to "$middle $word"
        }
        return Draft(
            second.run,
            DifferenceCategory.TRANSPOSITION, Confidence.HIGH,
            requiresIndividualApproval = true,
            before = before, after = after,
            evidence = "Two adjacent words appear swapped in the product.",
            target = CorrectionTarget.Fragments(
                listOf(
                    FragmentEdit(
                        p.index, prodStart, prodEnd,
                        o.text.substring(origStart, origEnd)
                    )
                )
            )
        )
    }

    /** Character position in the product paragraph where missing tokens
     *  are re-inserted: the end of the preceding matched token, or the
     *  paragraph start when the run opens the paragraph. */
    private fun insertionPosition(
        run: Run,
        pTokens: List<Token>
    ): Int {
        val before = run.equalBeforeProdIndex
        return if (before != null) pTokens[before].end else 0
    }

    /** Adds a separating space on whichever side of an inserted fragment
     *  does not already have one, so splicing never glues words together. */
    private fun spaced(fragment: String, text: String, position: Int): String {
        val prefix = if (position > 0 && text[position - 1] != ' ') " " else ""
        val suffix = if (position < text.length && text[position] != ' ') " " else ""
        return prefix + fragment + suffix
    }

    private fun planTables(
        orig: ExtractedDocument,
        prod: ExtractedDocument,
        nextId: () -> Int
    ): List<PlannedCorrection> {
        val out = mutableListOf<PlannedCorrection>()
        val tableCount = maxOf(orig.tables.size, prod.tables.size)
        for (ti in 0 until tableCount) {
            val ot = orig.tables.getOrNull(ti)
            val pt = prod.tables.getOrNull(ti)
            when {
                ot == null && pt != null -> out.add(
                    PlannedCorrection(
                        nextId(), DifferenceCategory.STRUCTURAL, Confidence.HIGH,
                        requiresIndividualApproval = true,
                        locationLabel = "Table ${ti + 1}",
                        before = "table with ${pt.rows.size} rows", after = "",
                        evidence = "Extra table in the product.",
                        target = CorrectionTarget.RemoveTable(ti)
                    )
                )
                pt == null && ot != null -> out.add(
                    PlannedCorrection(
                        nextId(), DifferenceCategory.STRUCTURAL, Confidence.HIGH,
                        requiresIndividualApproval = true,
                        locationLabel = "Table ${ti + 1}",
                        before = "", after = "table with ${ot.rows.size} rows",
                        evidence = "Table missing in the product.",
                        target = CorrectionTarget.InsertTable(ti, ot.rows)
                    )
                )
                ot != null && pt != null -> {
                    for (ri in 0 until minOf(ot.rows.size, pt.rows.size)) {
                        for (ci in 0 until minOf(
                            ot.rows[ri].size, pt.rows[ri].size
                        )) {
                            val oc = ot.rows[ri][ci]
                            val pc = pt.rows[ri][ci]
                            if (oc != pc) {
                                val bothNumeric = oc.toDoubleOrNull() != null &&
                                    pc.toDoubleOrNull() != null
                                out.add(
                                    PlannedCorrection(
                                        nextId(),
                                        if (bothNumeric) {
                                            DifferenceCategory.NUMBER_CHANGE
                                        } else {
                                            DifferenceCategory.SUBSTITUTION
                                        },
                                        Confidence.HIGH,
                                        requiresIndividualApproval = bothNumeric,
                                        locationLabel = "Table ${ti + 1}, " +
                                            "row ${ri + 1}, cell ${ci + 1}",
                                        before = pc, after = oc,
                                        evidence = "Cell differs.",
                                        target = CorrectionTarget.SetCell(
                                            ti, ri, ci, oc
                                        )
                                    )
                                )
                            }
                        }
                    }
                    if (ot.rows.size > pt.rows.size) {
                        for (ri in pt.rows.size until ot.rows.size) {
                            out.add(
                                PlannedCorrection(
                                    nextId(), DifferenceCategory.DELETION,
                                    Confidence.HIGH,
                                    requiresIndividualApproval = true,
                                    locationLabel = "Table ${ti + 1}, row ${ri + 1}",
                                    before = "",
                                    after = ot.rows[ri].joinToString(" | "),
                                    evidence = "Row missing in the product.",
                                    target = CorrectionTarget.InsertRow(
                                        ti, ri, ot.rows[ri]
                                    )
                                )
                            )
                        }
                    }
                    if (pt.rows.size > ot.rows.size) {
                        for (ri in ot.rows.size until pt.rows.size) {
                            out.add(
                                PlannedCorrection(
                                    nextId(), DifferenceCategory.INSERTION,
                                    Confidence.HIGH,
                                    requiresIndividualApproval = true,
                                    locationLabel = "Table ${ti + 1}, row ${ri + 1}",
                                    before = pt.rows[ri].joinToString(" | "),
                                    after = "",
                                    evidence = "Extra row in the product.",
                                    target = CorrectionTarget.RemoveRow(ti, ri)
                                )
                            )
                        }
                    }
                }
            }
        }
        return out
    }
}

package com.divineit.convertor.domain.diff

import com.divineit.convertor.domain.alignment.ParagraphAligner
import com.divineit.convertor.domain.extraction.DocParagraph
import com.divineit.convertor.domain.extraction.ExtractedDocument
import com.divineit.convertor.domain.unicode.UnicodeAnalyzer

/** Difference categories. FORMATTING_ONLY is reserved for Phase 4+ (no
 *  formatting is extracted yet, so Phase 3 never emits it). UNRESOLVED
 *  marks content the engine cannot decide — it is reported, never
 *  silently changed. */
enum class DifferenceCategory {
    SUBSTITUTION,
    INSERTION,
    DELETION,
    TRANSPOSITION,
    DUPLICATION,
    WORD_ORDER,
    NUMBER_CHANGE,
    PUNCTUATION,
    UNICODE_SUBSTITUTION,
    FORMATTING_ONLY,
    STRUCTURAL,
    UNRESOLVED
}

/** Evidence-based confidence. No percentages are shown: a category plus
 *  its evidence is the honest precision this engine can offer. */
enum class Confidence { HIGH, MEDIUM, LOW }

data class Difference(
    val category: DifferenceCategory,
    val confidence: Confidence,
    val paragraphIndex: Int?,
    val tableIndex: Int?,
    val rowIndex: Int?,
    val cellIndex: Int?,
    val originalText: String,
    val productText: String,
    val evidence: String
)

data class ComparisonStats(
    val totalDifferences: Int,
    val byCategory: Map<DifferenceCategory, Int>,
    val originalParagraphs: Int,
    val productParagraphs: Int,
    val originalWords: Int,
    val productWords: Int,
    val tablesCompared: Int
)

enum class VerificationStatus {
    CONTENT_MATCH_VERIFIED,
    CONTENT_MATCH_WITH_WARNINGS,
    DIFFERENCES_REMAIN,
    VERIFICATION_INCOMPLETE
}

data class ComparisonResult(
    val differences: List<Difference>,
    val stats: ComparisonStats,
    val status: VerificationStatus
)

/**
 * Multi-level comparison: paragraph alignment, then token diffs inside
 * matched paragraphs, then cell-level table comparison. The original is
 * the reference authority; this engine only *reports* — correction is
 * Phase 4 and never runs implicitly.
 */
object DiffEngine {

    fun compare(orig: ExtractedDocument, prod: ExtractedDocument): ComparisonResult {
        val differences = mutableListOf<Difference>()

        val pairings = ParagraphAligner.align(
            orig.paragraphs.map { it.text },
            prod.paragraphs.map { it.text }
        )
        for (pairing in pairings) {
            val o = pairing.origIndex?.let { orig.paragraphs[it] }
            val p = pairing.prodIndex?.let { prod.paragraphs[it] }
            when {
                o == null && p != null -> differences.add(
                    Difference(
                        DifferenceCategory.INSERTION, Confidence.HIGH,
                        null, null, null, null, "", p.text,
                        "Paragraph present in the product but not in the original."
                    )
                )
                p == null && o != null -> differences.add(
                    Difference(
                        DifferenceCategory.DELETION, Confidence.HIGH,
                        o.index, null, null, null, o.text, "",
                        "Paragraph missing in the product."
                    )
                )
                o != null && p != null && o.text != p.text -> {
                    if (UnicodeAnalyzer.containsReplacementChar(p.text)) {
                        differences.add(
                            Difference(
                                DifferenceCategory.UNRESOLVED, Confidence.MEDIUM,
                                o.index, null, null, null, o.text, p.text,
                                "Product paragraph contains replacement characters " +
                                    "(�): some source content could not be read."
                            )
                        )
                    }
                    differences.addAll(diffParagraphTokens(o, p))
                }
            }
        }

        val mergedParagraphs = mergeMovedParagraphs(differences)
        differences.clear()
        differences.addAll(mergedParagraphs)

        differences.addAll(diffTables(orig, prod))

        val stats = ComparisonStats(
            totalDifferences = differences.size,
            byCategory = differences.groupingBy { it.category }.eachCount(),
            originalParagraphs = orig.paragraphs.size,
            productParagraphs = prod.paragraphs.size,
            originalWords = orig.paragraphs.sumOf { wordCount(it.text) },
            productWords = prod.paragraphs.sumOf { wordCount(it.text) },
            tablesCompared = minOf(orig.tables.size, prod.tables.size)
        )
        val status = when {
            differences.isEmpty() -> VerificationStatus.CONTENT_MATCH_VERIFIED
            differences.all { it.category == DifferenceCategory.FORMATTING_ONLY } ->
                VerificationStatus.CONTENT_MATCH_WITH_WARNINGS
            else -> VerificationStatus.DIFFERENCES_REMAIN
        }
        return ComparisonResult(differences, stats, status)
    }

    /**
     * Moved-paragraph pass: an original-only paragraph whose exact text
     * reappears as a product-only paragraph is one moved paragraph, not a
     * deletion plus an insertion. Matches greedily in order, each entry
     * used once, so duplicate paragraphs are not over-merged.
     */
    private fun mergeMovedParagraphs(
        differences: List<Difference>
    ): List<Difference> {
        val deletions = differences.filter {
            it.category == DifferenceCategory.DELETION &&
                it.paragraphIndex != null
        }
        val insertions = differences.filter {
            it.category == DifferenceCategory.INSERTION &&
                it.paragraphIndex == null &&
                it.tableIndex == null
        }
        val usedDeletions = mutableSetOf<Difference>()
        val usedInsertions = mutableSetOf<Difference>()
        val merged = differences.toMutableList()
        for (deletion in deletions) {
            val match = insertions.firstOrNull {
                it !in usedInsertions && it.productText == deletion.originalText
            } ?: continue
            usedDeletions.add(deletion)
            usedInsertions.add(match)
            merged.add(
                Difference(
                    DifferenceCategory.WORD_ORDER, Confidence.MEDIUM,
                    deletion.paragraphIndex, null, null, null,
                    deletion.originalText, deletion.originalText,
                    "Paragraph moved to a different position."
                )
            )
        }
        return merged.filter { it !in usedDeletions && it !in usedInsertions }
    }

    private fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }

    private data class Run(
        val deletes: List<Int>,
        val inserts: List<Int>,
        /** Equal tokens since the previous run (1 = directly adjacent). */
        val equalsBefore: Int,
        /** Text of the equal token immediately before this run, if any. */
        val equalBeforeText: String?
    )

    private data class RunEntry(val run: Run, val diff: Difference)

    private fun diffParagraphTokens(
        o: DocParagraph,
        p: DocParagraph
    ): List<Difference> {
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
        var lastEqualText: String? = null
        fun flush() {
            if (deletes.isNotEmpty() || inserts.isNotEmpty()) {
                runs.add(Run(deletes, inserts, equalsSinceLastRun, lastEqualText))
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
                    lastEqualText = oTokens[op.origIndex].text
                }
                is MyersDiff.Op.Delete -> deletes.add(op.origIndex)
                is MyersDiff.Op.Insert -> inserts.add(op.prodIndex)
            }
        }
        flush()

        val entries = mutableListOf<RunEntry>()
        for (run in runs) {
            val oText = run.deletes.joinToString(" ") { oTokens[it].text }
            val pText = run.inserts.joinToString(" ") { pTokens[it].text }
            val diff = when {
                run.inserts.isEmpty() -> Difference(
                    DifferenceCategory.DELETION, Confidence.HIGH,
                    o.index, null, null, null, oText, "",
                    "Present in the original, absent in the product."
                )
                run.deletes.isEmpty() -> {
                    val isDup = run.inserts.size == 1 &&
                        ((run.inserts[0] > 0 &&
                            pTokens[run.inserts[0] - 1].text == pText) ||
                            (run.inserts.last() + 1 < pTokens.size &&
                                pTokens[run.inserts.last() + 1].text == pText))
                    if (isDup) {
                        Difference(
                            DifferenceCategory.DUPLICATION, Confidence.HIGH,
                            o.index, null, null, null, "", pText,
                            "Word appears twice in the product where the " +
                                "original has it once."
                        )
                    } else {
                        Difference(
                            DifferenceCategory.INSERTION, Confidence.HIGH,
                            o.index, null, null, null, "", pText,
                            "Present in the product, absent in the original."
                        )
                    }
                }
                run.deletes.size == 1 && run.inserts.size == 1 -> {
                    val ot = oTokens[run.deletes[0]]
                    val pt = pTokens[run.inserts[0]]
                    val (category, evidence) = classifySingleToken(ot, pt)
                    Difference(
                        category, Confidence.HIGH,
                        o.index, null, null, null, ot.text, pt.text, evidence
                    )
                }
                else -> Difference(
                    DifferenceCategory.SUBSTITUTION, Confidence.MEDIUM,
                    o.index, null, null, null, oText, pText,
                    "Passage differs; alignment is less certain " +
                        "across multiple words."
                )
            }
            entries.add(RunEntry(run, diff))
        }

        // Transposition pass: a single-token deletion and a single-token
        // insertion of the same word, separated by exactly one equal
        // token, is two adjacent words swapped — not a delete plus an
        // insert. (Myers reports a swap as two separate runs, so this
        // cannot be caught during single-run classification.)
        val afterTransposition = mutableListOf<RunEntry>()
        var i = 0
        while (i < entries.size) {
            val first = entries[i]
            val second = entries.getOrNull(i + 1)
            val transposed = if (
                second != null &&
                second.run.equalsBefore == 1 &&
                second.run.equalBeforeText != null
            ) {
                val firstSingleDelete = first.diff.category ==
                    DifferenceCategory.DELETION && first.run.deletes.size == 1
                val firstSingleInsert = first.diff.category ==
                    DifferenceCategory.INSERTION && first.run.inserts.size == 1
                val secondSingleDelete = second.diff.category ==
                    DifferenceCategory.DELETION && second.run.deletes.size == 1
                val secondSingleInsert = second.diff.category ==
                    DifferenceCategory.INSERTION && second.run.inserts.size == 1
                val sameWord = first.diff.originalText.isNotEmpty() &&
                    first.diff.originalText == second.diff.productText ||
                    first.diff.productText.isNotEmpty() &&
                    first.diff.productText == second.diff.originalText
                val pairMatches = (firstSingleDelete && secondSingleInsert) ||
                    (firstSingleInsert && secondSingleDelete)
                if (pairMatches && sameWord) {
                    val word = if (firstSingleDelete) {
                        first.diff.originalText
                    } else {
                        first.diff.productText
                    }
                    val middle = second.run.equalBeforeText
                    val (origText, prodText) = if (firstSingleDelete) {
                        "$word $middle" to "$middle $word"
                    } else {
                        "$middle $word" to "$word $middle"
                    }
                    RunEntry(
                        second.run,
                        Difference(
                            DifferenceCategory.TRANSPOSITION, Confidence.HIGH,
                            o.index, null, null, null, origText, prodText,
                            "Two adjacent words appear swapped in the product."
                        )
                    )
                } else {
                    null
                }
            } else {
                null
            }
            if (transposed != null) {
                afterTransposition.add(transposed)
                i += 2
            } else {
                afterTransposition.add(first)
                i++
            }
        }

        // Moved-block pass: a deleted run and an inserted run with
        // identical token sequences (length >= 2) in the same paragraph
        // is one moved block, not a deletion plus an insertion.
        val deletionEntries = afterTransposition.filter {
            it.diff.category == DifferenceCategory.DELETION &&
                it.run.deletes.size >= 2
        }
        val insertionEntries = afterTransposition.filter {
            it.diff.category == DifferenceCategory.INSERTION &&
                it.run.inserts.size >= 2
        }
        val usedDeletions = mutableSetOf<Difference>()
        val usedInsertions = mutableSetOf<Difference>()
        val merged = afterTransposition.map { it.diff }.toMutableList()
        for (del in deletionEntries) {
            val delTexts = del.run.deletes.map { oTokens[it].text }
            val match = insertionEntries.firstOrNull {
                it.diff !in usedInsertions &&
                    it.run.inserts.map { idx -> pTokens[idx].text } == delTexts
            } ?: continue
            usedDeletions.add(del.diff)
            usedInsertions.add(match.diff)
            merged.add(
                Difference(
                    DifferenceCategory.WORD_ORDER, Confidence.MEDIUM,
                    o.index, null, null, null,
                    delTexts.joinToString(" "),
                    delTexts.joinToString(" "),
                    "Block moved within the paragraph."
                )
            )
        }
        return merged.filter { it !in usedDeletions && it !in usedInsertions }
    }

    private fun classifySingleToken(
        ot: Token,
        pt: Token
    ): Pair<DifferenceCategory, String> {
        if (ot.kind == TokenKind.NUMBER && pt.kind == TokenKind.NUMBER) {
            return DifferenceCategory.NUMBER_CHANGE to "Number changed."
        }
        if (ot.kind == TokenKind.PUNCT && pt.kind == TokenKind.PUNCT) {
            return DifferenceCategory.PUNCTUATION to "Punctuation changed."
        }
        val unicodeIssue = UnicodeAnalyzer.classifyPair(ot.text, pt.text)
        if (unicodeIssue == UnicodeAnalyzer.IssueKind.WATCHLIST_SUBSTITUTION) {
            return DifferenceCategory.UNICODE_SUBSTITUTION to
                "Character substitution from the OCR watchlist. " +
                "Character changes: ${CharDiff.summarize(ot.text, pt.text)}."
        }
        if (unicodeIssue == UnicodeAnalyzer.IssueKind.NORMALISATION_DIFFERENCE) {
            return DifferenceCategory.UNICODE_SUBSTITUTION to
                "Same visible text, different Unicode encoding " +
                "(composed vs decomposed)."
        }
        val detail = if (ot.kind == TokenKind.WORD && pt.kind == TokenKind.WORD) {
            " Character changes: ${CharDiff.summarize(ot.text, pt.text)}."
        } else {
            ""
        }
        return DifferenceCategory.SUBSTITUTION to "Word substituted.$detail"
    }

    private fun diffTables(
        orig: ExtractedDocument,
        prod: ExtractedDocument
    ): List<Difference> {
        val differences = mutableListOf<Difference>()
        val tableCount = maxOf(orig.tables.size, prod.tables.size)
        for (ti in 0 until tableCount) {
            val ot = orig.tables.getOrNull(ti)
            val pt = prod.tables.getOrNull(ti)
            when {
                ot == null && pt != null -> differences.add(
                    Difference(
                        DifferenceCategory.STRUCTURAL, Confidence.HIGH,
                        null, ti, null, null, "",
                        "table with ${pt.rows.size} rows",
                        "Extra table in the product."
                    )
                )
                pt == null && ot != null -> differences.add(
                    Difference(
                        DifferenceCategory.STRUCTURAL, Confidence.HIGH,
                        null, ti, null, null,
                        "table with ${ot.rows.size} rows", "",
                        "Table missing in the product."
                    )
                )
                ot != null && pt != null -> {
                    val origCols = ot.rows.firstOrNull()?.size ?: 0
                    val prodCols = pt.rows.firstOrNull()?.size ?: 0
                    if (origCols != prodCols) {
                        differences.add(
                            Difference(
                                DifferenceCategory.STRUCTURAL, Confidence.MEDIUM,
                                null, ti, null, null,
                                "$origCols columns", "$prodCols columns",
                                "Table column count differs."
                            )
                        )
                    }
                    for (ri in 0 until minOf(ot.rows.size, pt.rows.size)) {
                        for (ci in 0 until minOf(
                            ot.rows[ri].size, pt.rows[ri].size
                        )) {
                            val oc = ot.rows[ri][ci]
                            val pc = pt.rows[ri][ci]
                            if (oc != pc) {
                                val bothNumeric = oc.toDoubleOrNull() != null &&
                                    pc.toDoubleOrNull() != null
                                differences.add(
                                    Difference(
                                        if (bothNumeric) {
                                            DifferenceCategory.NUMBER_CHANGE
                                        } else {
                                            DifferenceCategory.SUBSTITUTION
                                        },
                                        Confidence.HIGH,
                                        null, ti, ri, ci, oc, pc,
                                        "Cell differs."
                                    )
                                )
                            }
                        }
                    }
                    if (ot.rows.size > pt.rows.size) {
                        for (ri in pt.rows.size until ot.rows.size) {
                            differences.add(
                                Difference(
                                    DifferenceCategory.DELETION, Confidence.HIGH,
                                    null, ti, ri, null,
                                    ot.rows[ri].joinToString(" | "), "",
                                    "Row missing in the product."
                                )
                            )
                        }
                    }
                    if (pt.rows.size > ot.rows.size) {
                        for (ri in ot.rows.size until pt.rows.size) {
                            differences.add(
                                Difference(
                                    DifferenceCategory.INSERTION, Confidence.HIGH,
                                    null, ti, ri, null, "",
                                    pt.rows[ri].joinToString(" | "),
                                    "Extra row in the product."
                                )
                            )
                        }
                    }
                }
            }
        }
        return differences
    }
}

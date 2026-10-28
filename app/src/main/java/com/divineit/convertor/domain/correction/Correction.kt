package com.divineit.convertor.domain.correction

import com.divineit.convertor.domain.diff.Confidence
import com.divineit.convertor.domain.diff.DifferenceCategory

/** One exact splice into a product paragraph, in character offsets of
 *  the extracted product text. charStart == charEnd means a pure
 *  insertion. Multiple edits to one paragraph are applied in descending
 *  charStart order so earlier offsets stay valid. */
data class FragmentEdit(
    val productParagraphIndex: Int,
    val charStart: Int,
    val charEnd: Int,
    val replacement: String
)

/** What a correction changes when applied. Every target restores text
 *  from the original document — the correction engine never invents
 *  content, so there is deliberately no "rewrite freely" target. */
sealed interface CorrectionTarget {
    data class Fragments(val edits: List<FragmentEdit>) : CorrectionTarget
    data class InsertParagraph(
        val originalParagraphIndex: Int,
        val text: String
    ) : CorrectionTarget
    data class RemoveParagraph(val productParagraphIndex: Int) : CorrectionTarget
    data class SetCell(
        val tableIndex: Int,
        val rowIndex: Int,
        val cellIndex: Int,
        val after: String
    ) : CorrectionTarget
    data class InsertRow(
        val tableIndex: Int,
        val originalRowIndex: Int,
        val cells: List<String>
    ) : CorrectionTarget
    data class RemoveRow(
        val tableIndex: Int,
        val productRowIndex: Int
    ) : CorrectionTarget
    data class InsertTable(
        val originalTableIndex: Int,
        val rows: List<List<String>>
    ) : CorrectionTarget
    data class RemoveTable(val productTableIndex: Int) : CorrectionTarget
}

enum class CorrectionStatus { PENDING, APPROVED, REJECTED, APPLIED, UNDONE }

/**
 * One planned correction, derived from one difference (or one merged
 * difference group, e.g. a transposition). `requiresIndividualApproval`
 * marks the changes AUTO-CORRECT must never touch: number changes,
 * multi-token passages, moves, and every structural or paragraph-level
 * change. Low-risk corrections are exactly the single-token,
 * HIGH-confidence restorations in the safe categories — see
 * CorrectionPlanner.
 */
data class PlannedCorrection(
    val id: Int,
    val category: DifferenceCategory,
    val confidence: Confidence,
    val requiresIndividualApproval: Boolean,
    val locationLabel: String,
    val before: String,
    val after: String,
    val evidence: String,
    val target: CorrectionTarget,
    val status: CorrectionStatus = CorrectionStatus.PENDING
) {
    val isLowRisk: Boolean
        get() = !requiresIndividualApproval
}

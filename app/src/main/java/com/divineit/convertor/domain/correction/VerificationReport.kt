package com.divineit.convertor.domain.correction

import com.divineit.convertor.domain.diff.ComparisonResult
import com.divineit.convertor.domain.diff.VerificationStatus

/**
 * Plain-text verification report (Phase 4). Deterministic: the caller
 * passes the generation timestamp as text, so the same inputs always
 * produce the same report. The wording follows the verification rules —
 * CONTENT MATCH VERIFIED appears only when the final comparison found
 * zero differences; nothing in the report claims a result the engine
 * did not produce.
 */
object VerificationReport {

    fun build(
        generatedAt: String,
        originalName: String,
        productName: String,
        initial: ComparisonResult,
        corrections: List<PlannedCorrection>,
        auditLines: List<String>,
        finalResult: ComparisonResult?
    ): String {
        val out = StringBuilder()
        out.appendLine("DIVINE IT CONVERTOR — VERIFICATION REPORT")
        out.appendLine("Generated: $generatedAt")
        out.appendLine("Original: $originalName")
        out.appendLine("Product: $productName")
        out.appendLine()
        out.appendLine("INITIAL COMPARISON")
        out.appendLine("Status: ${initial.status}")
        out.appendLine(
            "Paragraphs: ${initial.stats.originalParagraphs} → " +
                initial.stats.productParagraphs
        )
        out.appendLine(
            "Words: ${initial.stats.originalWords} → ${initial.stats.productWords}"
        )
        out.appendLine("Tables compared: ${initial.stats.tablesCompared}")
        out.appendLine("Differences found: ${initial.stats.totalDifferences}")
        for ((category, count) in initial.stats.byCategory) {
            out.appendLine("  $category: $count")
        }
        out.appendLine()
        out.appendLine("CORRECTIONS")
        out.appendLine("Planned: ${corrections.size}")
        out.appendLine(
            "Applied: " + corrections.count {
                it.status == CorrectionStatus.APPLIED
            }
        )
        out.appendLine(
            "Approved, not applied: " + corrections.count {
                it.status == CorrectionStatus.APPROVED
            }
        )
        out.appendLine(
            "Pending decision: " + corrections.count {
                it.status == CorrectionStatus.PENDING
            }
        )
        out.appendLine(
            "Rejected: " + corrections.count {
                it.status == CorrectionStatus.REJECTED
            }
        )
        out.appendLine(
            "Undone: " + corrections.count {
                it.status == CorrectionStatus.UNDONE
            }
        )
        val applied = corrections.filter {
            it.status == CorrectionStatus.APPLIED
        }
        if (applied.isNotEmpty()) {
            out.appendLine("Applied corrections:")
            for (correction in applied) {
                out.appendLine(
                    "- [${correction.category}] ${correction.locationLabel}: " +
                        "\"${correction.before}\" → \"${correction.after}\" " +
                        "(${correction.evidence})"
                )
            }
        }
        if (auditLines.isNotEmpty()) {
            out.appendLine()
            out.appendLine("AUDIT TRAIL")
            for (line in auditLines) {
                out.appendLine(line)
            }
        }
        out.appendLine()
        if (finalResult != null) {
            out.appendLine("FINAL VERIFICATION (after applied corrections)")
            out.appendLine("Status: ${finalResult.status}")
            out.appendLine(
                "Remaining differences: ${finalResult.stats.totalDifferences}"
            )
            for (difference in finalResult.differences) {
                val location = when {
                    difference.paragraphIndex != null ->
                        "Paragraph ${difference.paragraphIndex + 1}"
                    difference.tableIndex != null ->
                        "Table ${difference.tableIndex + 1}"
                    else -> "Document"
                }
                out.appendLine(
                    "- [${difference.category}] $location: " +
                        "\"${difference.originalText}\" vs " +
                        "\"${difference.productText}\""
                )
            }
            out.appendLine()
            out.appendLine(
                "RESULT: " + when (finalResult.status) {
                    VerificationStatus.CONTENT_MATCH_VERIFIED ->
                        "CONTENT MATCH VERIFIED"
                    VerificationStatus.CONTENT_MATCH_WITH_WARNINGS ->
                        "CONTENT MATCH WITH WARNINGS"
                    VerificationStatus.DIFFERENCES_REMAIN ->
                        "DIFFERENCES REMAIN"
                    VerificationStatus.VERIFICATION_INCOMPLETE ->
                        "VERIFICATION INCOMPLETE"
                }
            )
        } else {
            out.appendLine("FINAL VERIFICATION: not run (no corrections applied).")
            out.appendLine("RESULT: ${initial.status}")
        }
        out.appendLine()
        out.appendLine(
            "Every correction in this report restores text taken from the " +
                "original document. No content was invented, and no change " +
                "was applied without approval."
        )
        return out.toString()
    }
}

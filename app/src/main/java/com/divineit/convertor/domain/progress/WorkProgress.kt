package com.divineit.convertor.domain.progress

/**
 * Honest progress for long-running work.
 *
 * A percentage exists ONLY when the work is genuinely countable —
 * for example a PDF whose pages are read one by one. When the work
 * is a single un-splittable step (one ML Kit call for one image,
 * writing one output file), [done]/[total] stay null and the UI
 * shows a spinner with the label instead of an invented number.
 */
data class WorkProgress(
    val label: String,
    val done: Int? = null,
    val total: Int? = null
) {
    /** 0–100 when countable, null otherwise. */
    val percent: Int?
        get() = if (done != null && total != null && total > 0) {
            ((done.toLong() * 100) / total.toLong())
                .toInt()
                .coerceIn(0, 100)
        } else {
            null
        }

    /** Label with the percentage appended when one exists,
     *  e.g. "Reading PDF — page 12 of 40 — 30%". */
    val displayText: String
        get() = percent?.let { "$label — $it%" } ?: label
}

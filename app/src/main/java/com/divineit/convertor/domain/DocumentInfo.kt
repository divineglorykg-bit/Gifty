package com.divineit.convertor.domain

/** Everything the upload slot shows about one selected file. Page count is
 *  nullable by design: many formats do not expose one, and Phase 2 never
 *  invents one. sha256 holds the full hex digest once computed. */
data class DocumentInfo(
    val slot: DocumentSlot,
    val uri: String,
    val displayName: String,
    val extension: String,
    val sizeBytes: Long,
    val format: DocumentFormat,
    val kind: ContentKind,
    val status: DocumentStatus,
    val statusMessage: String,
    val pageCount: Int?,
    val pageCountEstimate: Boolean,
    val sha256: String?,
    val previewSnippet: String?
) {
    /** First 16 hex chars for compact display; the full digest is stored. */
    val sha256Short: String?
        get() = sha256?.take(16)?.let { "$it…" }

    val isReady: Boolean
        get() = status == DocumentStatus.READY
}

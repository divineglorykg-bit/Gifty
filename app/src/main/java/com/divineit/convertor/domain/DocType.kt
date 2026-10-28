package com.divineit.convertor.domain

/** Document format, determined from file content first and extension second.
 *  The extension alone is never trusted. */
enum class DocumentFormat {
    PDF, DOCX, TXT, DOC, RTF, ODT, HTML, CSV, XLSX, IMAGE, UNKNOWN
}

/** Best-effort content kind for the upload-slot badge. Phase 2 can only
 *  prove TEXT_BASED (plain text), SCANNED (image files), ENCRYPTED
 *  (PDF with an /Encrypt marker) and DAMAGED (empty/unreadable). Everything
 *  else is UNKNOWN until text extraction lands in Phase 3. */
enum class ContentKind {
    TEXT_BASED, SCANNED, ENCRYPTED, DAMAGED, UNSUPPORTED, UNKNOWN
}

/** Whether a selected file passed validation and may be used. Comparison
 *  and correction buttons stay disabled unless both slots are READY. */
enum class DocumentStatus { VALIDATING, READY, ERROR }

enum class DocumentSlot { ORIGINAL, PRODUCT }

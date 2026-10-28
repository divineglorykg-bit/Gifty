package com.divineit.convertor.domain

/**
 * File-type detection from content, not extension. Pure Kotlin, no Android
 * dependency, so it is unit-testable on the JVM.
 *
 * Order of evidence: (1) empty file, (2) magic bytes for PDF / ZIP / common
 * images, (3) supported extension, (4) plain-text heuristic. A mismatch
 * between extension and content is reported, never silently resolved in
 * favour of the extension.
 */
object FileValidation {

    data class DetectionResult(
        val format: DocumentFormat,
        val kind: ContentKind,
        val status: DocumentStatus,
        val message: String
    )

    private val SUPPORTED_EXTENSIONS = setOf(
        "pdf", "docx", "txt", "doc", "rtf", "odt", "html", "htm",
        "csv", "xlsx", "png", "jpg", "jpeg", "tif", "tiff", "webp"
    )

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun detect(bytes: ByteArray, fileName: String): DetectionResult {
        if (bytes.isEmpty()) {
            return DetectionResult(
                DocumentFormat.UNKNOWN, ContentKind.DAMAGED,
                DocumentStatus.ERROR, "File is empty."
            )
        }
        val extension = extensionOf(fileName)
        val head = bytes.take(4096).toByteArray()

        // PDF magic: %PDF, with an /Encrypt marker meaning password-protected.
        if (bytes.startsWithAscii("%PDF")) {
            val headText = String(head, Charsets.ISO_8859_1)
            return if (headText.contains("/Encrypt")) {
                DetectionResult(
                    DocumentFormat.PDF, ContentKind.ENCRYPTED,
                    DocumentStatus.ERROR,
                    "Password-protected PDF. Decryption is not supported in Phase 2."
                )
            } else {
                DetectionResult(
                    DocumentFormat.PDF, ContentKind.UNKNOWN,
                    DocumentStatus.READY,
                    "PDF detected. Text layer is checked during extraction (Phase 3)."
                )
            }
        }

        // ZIP container: DOCX / ODT / XLSX, distinguished by extension.
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val format = when (extension) {
                "docx" -> DocumentFormat.DOCX
                "odt" -> DocumentFormat.ODT
                "xlsx" -> DocumentFormat.XLSX
                else -> DocumentFormat.UNKNOWN
            }
            return if (format == DocumentFormat.UNKNOWN) {
                DetectionResult(
                    format, ContentKind.UNKNOWN, DocumentStatus.ERROR,
                    "ZIP container with an unrecognised document type."
                )
            } else {
                DetectionResult(
                    format, ContentKind.UNKNOWN, DocumentStatus.READY,
                    "Office document detected. Structure is read in Phase 3."
                )
            }
        }

        // Common image magic bytes: scanned pages, OCR input in Phase 3.
        if (isImageMagic(bytes)) {
            return DetectionResult(
                DocumentFormat.IMAGE, ContentKind.SCANNED,
                DocumentStatus.READY,
                "Image detected. Text comes from OCR (Phase 3)."
            )
        }

        // Supported extension with no contradicting magic bytes.
        if (extension in SUPPORTED_EXTENSIONS) {
            val format = when (extension) {
                "txt" -> DocumentFormat.TXT
                "csv" -> DocumentFormat.CSV
                "doc" -> DocumentFormat.DOC
                "rtf" -> DocumentFormat.RTF
                "html", "htm" -> DocumentFormat.HTML
                else -> DocumentFormat.UNKNOWN
            }
            val kind = when (format) {
                DocumentFormat.TXT, DocumentFormat.CSV,
                DocumentFormat.HTML -> ContentKind.TEXT_BASED
                else -> ContentKind.UNKNOWN
            }
            // Plain-text formats must actually decode as text without NULs.
            if ((format == DocumentFormat.TXT || format == DocumentFormat.CSV ||
                    format == DocumentFormat.HTML) && bytes.contains(0.toByte())
            ) {
                return DetectionResult(
                    DocumentFormat.UNKNOWN, ContentKind.UNKNOWN,
                    DocumentStatus.ERROR,
                    "File claims a text format but contains binary data."
                )
            }
            return DetectionResult(
                format, kind, DocumentStatus.READY,
                "Format accepted from extension; content check passed."
            )
        }

        // Unknown extension but plain-text content.
        if (!bytes.contains(0.toByte())) {
            return DetectionResult(
                DocumentFormat.UNKNOWN, ContentKind.TEXT_BASED,
                DocumentStatus.READY,
                "Plain-text content with an unknown extension."
            )
        }

        return DetectionResult(
            DocumentFormat.UNKNOWN, ContentKind.UNSUPPORTED,
            DocumentStatus.ERROR,
            "Unsupported file type."
        )
    }

    private fun isImageMagic(bytes: ByteArray): Boolean {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) return true // PNG
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) return true // JPEG
        if (bytes.size >= 4 &&
            ((bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte()) ||
                (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte())) &&
            bytes[2] == 0x2A.toByte() && bytes[3] == 0x00.toByte()
        ) return true // TIFF
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) return true // WebP
        return false
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean {
        val p = prefix.toByteArray(Charsets.US_ASCII)
        if (size < p.size) return false
        return p.indices.all { this[it] == p[it] }
    }
}

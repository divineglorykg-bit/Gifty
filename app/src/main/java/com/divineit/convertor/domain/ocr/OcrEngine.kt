package com.divineit.convertor.domain.ocr

/**
 * OCR engine boundary. Implementations are device-only (ML Kit), so the
 * interface stays free of Android types: raw image bytes in, recognised
 * text out, asynchronously. Recognised text is a *reading* of the page,
 * never a verified copy — it always goes through the same comparison
 * and correction safeguards as any other extraction.
 */
interface OcrEngine {
    fun recognizeText(
        imageBytes: ByteArray,
        onResult: (Result<String>) -> Unit
    )
}

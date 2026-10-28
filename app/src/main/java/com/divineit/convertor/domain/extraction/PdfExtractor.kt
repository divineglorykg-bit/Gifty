package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

/**
 * PDF text-layer extraction via PdfBox-Android.
 *
 * DEVICE-ONLY: this file is NOT compiled by the JVM harness and has never
 * run in this environment. It needs the dependency
 * `com.tom-roush:pdfbox-android:2.0.27.0` (see app/build.gradle.kts).
 * If Gradle cannot resolve that artifact, delete this file, remove the
 * dependency line, and remove the PDF branch in ComparisonViewModel —
 * everything else builds without it.
 *
 * Scanned pages have no text layer: this extractor returns only the
 * embedded text (possibly empty). OCR for scanned pages is a Phase 4
 * capability, and an empty result here must never be mistaken for an
 * empty document.
 */
class PdfExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        PDDocument.load(bytes.inputStream()).use { document ->
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            val paragraphs = splitParagraphs(text)
                .mapIndexed { index, t -> DocParagraph(t, index) }
            return ExtractedDocument(
                fileName,
                DocumentFormat.PDF,
                paragraphs,
                emptyList(),
                document.numberOfPages
            )
        }
    }
}

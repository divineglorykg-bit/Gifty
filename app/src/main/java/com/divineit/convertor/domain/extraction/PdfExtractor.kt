package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * PDF text-layer extraction via PdfBox-Android.
 *
 * DEVICE-ONLY: this file is NOT compiled by the JVM harness and has never
 * run in this environment. It needs the dependency
 * `com.tom-roush:pdfbox-android:2.0.27.0` (see app/build.gradle.kts).
 *
 * Pages are stripped one by one so [onPageProgress] can report real
 * page progress for large documents (done, total). The page texts are
 * joined exactly as a whole-document strip would present them, and the
 * shared paragraph splitter downstream is unchanged.
 *
 * Scanned pages have no text layer: this extractor returns only the
 * embedded text (possibly empty). OCR for scanned pages lives in the
 * OCR engine, and an empty result here must never be mistaken for an
 * empty document.
 */
class PdfExtractor(
    private val onPageProgress: ((done: Int, total: Int) -> Unit)? = null
) : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        PDDocument.load(bytes.inputStream()).use { document ->
            val total = document.numberOfPages
            val stripper = PDFTextStripper()
            val text = StringBuilder()
            for (page in 1..total) {
                stripper.startPage = page
                stripper.endPage = page
                text.append(stripper.getText(document))
                onPageProgress?.invoke(page, total)
            }
            val paragraphs = splitParagraphs(text.toString())
                .mapIndexed { index, t -> DocParagraph(t, index) }
            return ExtractedDocument(
                fileName,
                DocumentFormat.PDF,
                paragraphs,
                emptyList(),
                total
            )
        }
    }
}

package com.divineit.convertor.domain.pdf

import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

/**
 * PDF page tools (Phase 5), built on PdfBox-Android.
 *
 * DEVICE-ONLY: like PdfExtractor, this file is compiled against the
 * PdfBox-Android artifact (com.tom_roush packages) and cannot run in
 * the JVM harness. Its API usage is compile-checked locally against
 * the real AAR before any cloud build. The app initialises
 * PDFBoxResourceLoader in ConvertorApp, which PdfBox-Android needs
 * before any of this runs.
 *
 * Every operation loads from bytes and returns new bytes; the source
 * documents are never modified, and every PDDocument is closed.
 */
object PdfTools {

    fun pageCount(bytes: ByteArray): Int =
        PDDocument.load(bytes.inputStream()).use { it.numberOfPages }

    /** Concatenates the given PDFs, in the order given. */
    fun merge(documents: List<ByteArray>): ByteArray {
        require(documents.isNotEmpty()) { "Nothing to merge." }
        if (documents.size == 1) return documents.first()
        val out = ByteArrayOutputStream()
        val merger = PDFMergerUtility()
        merger.destinationStream = out
        documents.forEach { merger.addSource(it.inputStream()) }
        merger.mergeDocuments()
        return out.toByteArray()
    }

    /** Splits into one single-page PDF per page, in page order. */
    fun splitPerPage(bytes: ByteArray): List<ByteArray> =
        PDDocument.load(bytes.inputStream()).use { document ->
            val splitter = Splitter()
            splitter.setSplitAtPage(1)
            val parts = splitter.split(document)
            try {
                parts.map { part ->
                    val out = ByteArrayOutputStream()
                    part.save(out)
                    out.toByteArray()
                }
            } finally {
                parts.forEach { runCatching { it.close() } }
            }
        }

    /** Rotates every page by [degrees] (multiples of 90; negative
     *  values rotate the other way). The stored rotation is kept in
     *  the 0–270 range PDF uses. */
    fun rotateAll(bytes: ByteArray, degrees: Int): ByteArray =
        PDDocument.load(bytes.inputStream()).use { document ->
            val step = ((degrees % 360) + 360) % 360
            for (page in document.pages) {
                page.rotation = (page.rotation + step) % 360
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            out.toByteArray()
        }
}

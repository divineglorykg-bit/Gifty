package com.divineit.convertor.domain

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Page-count extraction where the format actually exposes one. Pure Kotlin.
 *
 * - PDF: counts "/Type /Page" occurrences in the raw bytes. This is an
 *   ESTIMATE (a page tree can hide pages and the marker can appear in
 *   content streams); it is always labelled as such in the UI.
 * - DOCX: reads docProps/app.xml <Pages> when present.
 * - ODT: reads meta.xml meta:page-count when present.
 * - Everything else: null (unknown). Phase 2 never invents a page count.
 */
object DocumentMetadata {

    data class PageCount(val count: Int, val estimate: Boolean)

    fun pageCount(bytes: ByteArray, format: DocumentFormat): PageCount? {
        return when (format) {
            DocumentFormat.PDF -> pdfPageEstimate(bytes)
            DocumentFormat.DOCX -> zipEntryPageCount(bytes, "docProps/app.xml",
                Regex("<Pages>(\\d+)</Pages>"), estimate = false)
            DocumentFormat.ODT -> zipEntryPageCount(bytes, "meta.xml",
                Regex("meta:page-count=\"(\\d+)\""), estimate = false)
            else -> null
        }
    }

    private fun pdfPageEstimate(bytes: ByteArray): PageCount? {
        val text = String(bytes, Charsets.ISO_8859_1)
        val count = Regex("/Type\\s*/Page(?!s)").findAll(text).count()
        return if (count > 0) PageCount(count, estimate = true) else null
    }

    private fun zipEntryPageCount(
        bytes: ByteArray,
        entryName: String,
        pattern: Regex,
        estimate: Boolean
    ): PageCount? {
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        val match = pattern.find(text)
                        if (match != null) {
                            return PageCount(match.groupValues[1].toInt(), estimate)
                        }
                        return null
                    }
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

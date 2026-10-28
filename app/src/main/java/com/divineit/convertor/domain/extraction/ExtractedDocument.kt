package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat

/** A paragraph of extracted text. headingLevel is 1–9 for headings, null
 *  for body text. Text is whitespace-normalised at extraction: runs of
 *  whitespace collapse to single spaces, so line-wrap and indentation
 *  differences do not count as content differences (exact spacing
 *  comparison is a later refinement, noted in the Phase 1 plan). */
data class DocParagraph(
    val text: String,
    val index: Int,
    val headingLevel: Int? = null
)

/** A table: rows of cell texts, in document order. */
data class DocTable(
    val rows: List<List<String>>,
    val index: Int
)

/** The extracted form of one document. Paragraphs and tables are kept
 *  separate so structural comparison is possible; text alone never proves
 *  faithful reproduction. */
data class ExtractedDocument(
    val sourceName: String,
    val format: DocumentFormat,
    val paragraphs: List<DocParagraph>,
    val tables: List<DocTable>,
    val pageCount: Int? = null
) {
    /** All paragraph text in order, for alignment and display. */
    val fullText: String
        get() = paragraphs.joinToString("\n") { it.text }
}

/** One format's extractor. Pure Kotlin (except PdfExtractor, which needs
 *  PdfBox-Android and is therefore device-only). */
interface DocumentExtractor {
    fun extract(bytes: ByteArray, fileName: String): ExtractedDocument
}

/** Paragraph splitting shared by the text-based extractors: normalise
 *  line endings, split on blank lines, join wrapped lines with spaces,
 *  collapse whitespace runs, drop empty blocks. */
internal fun splitParagraphs(raw: String): List<String> {
    val normalised = raw.replace("\r\n", "\n").replace('\r', '\n')
    return normalised.split(Regex("\n\\s*\n"))
        .map { block ->
            block.replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        .filter { it.isNotEmpty() }
}

/** Extractor lookup. PDF returns null here on purpose: PDF extraction
 *  lives in PdfExtractor (PdfBox-Android, device-only) and is wired
 *  directly by the comparison ViewModel, so the pure-Kotlin factory — and
 *  the JVM harness — never touch the PdfBox dependency. */
object ExtractorFactory {
    fun extractorFor(format: DocumentFormat): DocumentExtractor? =
        when (format) {
            DocumentFormat.TXT -> TxtExtractor()
            DocumentFormat.CSV -> CsvExtractor()
            DocumentFormat.HTML -> HtmlExtractor()
            DocumentFormat.DOCX -> DocxExtractor()
            DocumentFormat.ODT -> OdtExtractor()
            DocumentFormat.XLSX -> XlsxExtractor()
            DocumentFormat.RTF -> RtfExtractor()
            DocumentFormat.DOC -> null // Binary .doc: HWPF module, later phase.
            DocumentFormat.PDF -> null // See PdfExtractor (device-only).
            DocumentFormat.IMAGE -> null // OCR, Phases 3–4.
            DocumentFormat.UNKNOWN -> null
        }
}

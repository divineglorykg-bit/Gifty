package com.divineit.convertor.domain.convert

import com.divineit.convertor.domain.export.DocxWriter
import com.divineit.convertor.domain.extraction.ExtractedDocument

/** A format the Conversion screen can produce (Phase 5). */
enum class ConvertTarget(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    TXT("Plain text (TXT)", "txt", "text/plain"),
    DOCX(
        "Word (DOCX)", "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ),
    PDF("PDF document", "pdf", "application/pdf"),
    HTML("Web page (HTML)", "html", "text/html"),
    RTF("Rich text (RTF)", "rtf", "application/rtf"),
    ODT("OpenDocument text (ODT)", "odt", "application/vnd.oasis.opendocument.text"),
    CSV("Comma-separated (CSV)", "csv", "text/csv"),
    XLSX(
        "Excel workbook (XLSX)", "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
}

/**
 * Conversion dispatch (Phase 5). Every target is produced from the
 * extracted document model, so a conversion is exactly as faithful as
 * the extraction was — layout that extraction never captured (fonts,
 * exact spacing, images) is not invented here. PDF is the one target
 * not written by this engine: it needs the Android PDF renderer, so
 * [write] refuses it and the device writer handles it instead.
 */
object ConversionEngine {

    val targets: List<ConvertTarget> = ConvertTarget.values().toList()

    fun write(document: ExtractedDocument, target: ConvertTarget): ByteArray =
        when (target) {
            ConvertTarget.TXT -> TxtWriter.write(document)
            ConvertTarget.DOCX -> DocxWriter.write(document)
            ConvertTarget.HTML -> HtmlWriter.write(document)
            ConvertTarget.RTF -> RtfWriter.write(document)
            ConvertTarget.ODT -> OdtWriter.write(document)
            ConvertTarget.CSV -> CsvWriter.write(document)
            ConvertTarget.XLSX -> XlsxWriter.write(document)
            ConvertTarget.PDF -> throw IllegalArgumentException(
                "PDF output is produced by the device PDF writer, not the engine."
            )
        }

    /** "report.docx" + TXT -> "report.txt"; a name with no extension
     *  simply gains one. */
    fun outputName(sourceName: String, target: ConvertTarget): String {
        val base = sourceName.substringBeforeLast('.', sourceName)
            .ifBlank { "document" }
        return "$base.${target.extension}"
    }
}

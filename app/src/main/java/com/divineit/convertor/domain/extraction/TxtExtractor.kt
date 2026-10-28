package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat

/** Plain-text extraction. A byte-order mark is stripped. Paragraphs are
 *  blocks separated by blank lines; wrapped lines join with spaces. */
class TxtExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        var text = String(bytes, Charsets.UTF_8)
        if (text.startsWith("\uFEFF")) text = text.substring(1)
        val paragraphs = splitParagraphs(text)
            .mapIndexed { index, t -> DocParagraph(t, index) }
        return ExtractedDocument(fileName, DocumentFormat.TXT, paragraphs, emptyList())
    }
}

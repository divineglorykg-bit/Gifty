package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat

/** Basic HTML text extraction (pure Kotlin, no Jsoup dependency in
 *  Phase 3). Scripts and styles are removed, block boundaries become
 *  paragraph breaks, remaining tags are stripped, and common entities
 *  plus numeric character references are decoded. Complex layouts,
 *  tables and lists lose their structure here — that is a documented
 *  Phase 3 limitation; a Jsoup-based extractor is the Phase 5 upgrade. */
class HtmlExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        var text = String(bytes, Charsets.UTF_8)
        text = text.replace(Regex("(?is)<script.*?</script>"), " ")
        text = text.replace(Regex("(?is)<style.*?</style>"), " ")
        text = text.replace(
            Regex("(?i)</?(p|div|li|tr|h[1-6]|ul|ol|table|blockquote|br)[^>]*>"),
            "\n"
        )
        text = text.replace(Regex("<[^>]+>"), "")
        text = decodeEntities(text)
        val paragraphs = splitParagraphs(text)
            .mapIndexed { index, t -> DocParagraph(t, index) }
        return ExtractedDocument(fileName, DocumentFormat.HTML, paragraphs, emptyList())
    }

    private fun decodeEntities(text: String): String {
        var out = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        out = Regex("&#(\\d+);").replace(out) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }
        return out
    }
}

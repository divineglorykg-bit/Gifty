package com.divineit.convertor.domain.extraction

import com.divineit.convertor.domain.DocumentFormat

/** HTML text extraction (pure Kotlin, no Jsoup dependency). Scripts
 *  and styles are removed, block boundaries become paragraph breaks,
 *  remaining tags are stripped, and common entities plus numeric
 *  character references are decoded.
 *
 *  Phase 5 upgrade: tables keep their structure — each table becomes
 *  a DocTable of rows and cells, matching the other extractors.
 *  Complex layouts and lists still flatten to paragraphs; that is a
 *  documented limitation of the regex approach. */
class HtmlExtractor : DocumentExtractor {
    override fun extract(bytes: ByteArray, fileName: String): ExtractedDocument {
        var text = String(bytes, Charsets.UTF_8)
        text = text.replace(Regex("(?is)<script.*?</script>"), " ")
        text = text.replace(Regex("(?is)<style.*?</style>"), " ")

        // Tables first, before the tag-stripping pass flattens them.
        val tables = mutableListOf<DocTable>()
        text = Regex("(?is)<table[^>]*>(.*?)</table>").replace(text) { match ->
            val rows = Regex("(?is)<tr[^>]*>(.*?)</tr>")
                .findAll(match.groupValues[1])
                .map { row ->
                    Regex("(?is)<t[dh][^>]*>(.*?)</t[dh]>")
                        .findAll(row.groupValues[1])
                        .map { cell -> cellText(cell.groupValues[1]) }
                        .toList()
                }
                .filter { it.isNotEmpty() }
                .toList()
            if (rows.isNotEmpty()) {
                tables.add(DocTable(rows, tables.size))
            }
            "\n\n"
        }

        text = text.replace(
            Regex("(?i)</?(p|div|li|tr|h[1-6]|ul|ol|blockquote|br)[^>]*>"),
            "\n"
        )
        text = text.replace(Regex("<[^>]+>"), "")
        text = decodeEntities(text)
        val paragraphs = splitParagraphs(text)
            .mapIndexed { index, t -> DocParagraph(t, index) }
        return ExtractedDocument(fileName, DocumentFormat.HTML, paragraphs, tables)
    }

    /** Cell text: inner tags stripped, entities decoded, whitespace
     *  normalised — the same treatment paragraphs get. */
    private fun cellText(fragment: String): String {
        val stripped = fragment.replace(Regex("<[^>]+>"), " ")
        return normaliseWhitespace(decodeEntities(stripped))
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

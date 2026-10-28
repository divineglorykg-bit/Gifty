package com.divineit.convertor.domain.export

import com.divineit.convertor.domain.extraction.ExtractedDocument
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, honest DOCX writer: turns an ExtractedDocument into a valid
 * DOCX (paragraphs with optional heading styles, then tables). The
 * extraction model does not preserve paragraph/table interleaving or
 * character-level formatting, so the export does not claim to: all
 * paragraphs come first, then all tables, in plain text runs. What it
 * does guarantee is that the text content round-trips exactly through
 * DocxExtractor.
 */
object DocxWriter {

    fun write(document: ExtractedDocument): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypes().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(rootRels().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml(document).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write(stylesXml().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(documentRels().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun escape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun documentXml(document: ExtractedDocument): String {
        val body = StringBuilder()
        for (paragraph in document.paragraphs) {
            body.append("<w:p>")
            val level = paragraph.headingLevel
            if (level != null && level in 1..9) {
                body.append(
                    "<w:pPr><w:pStyle w:val=\"Heading$level\"/></w:pPr>"
                )
            }
            body.append("<w:r><w:t xml:space=\"preserve\">")
            body.append(escape(paragraph.text))
            body.append("</w:t></w:r></w:p>")
        }
        for (table in document.tables) {
            body.append("<w:tbl>")
            for (row in table.rows) {
                body.append("<w:tr>")
                for (cell in row) {
                    body.append("<w:tc><w:p><w:r>")
                    body.append("<w:t xml:space=\"preserve\">")
                    body.append(escape(cell))
                    body.append("</w:t></w:r></w:p></w:tc>")
                }
                body.append("</w:tr>")
            }
            body.append("</w:tbl>")
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/" +
            "wordprocessingml/2006/main\"><w:body>" +
            body.toString() +
            "<w:sectPr/></w:body></w:document>"
    }

    private fun stylesXml(): String {
        val styles = StringBuilder()
        styles.append(
            "<w:style w:type=\"paragraph\" w:default=\"1\" " +
                "w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
        )
        for (level in 1..9) {
            styles.append(
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading$level\">" +
                    "<w:name w:val=\"heading $level\"/>" +
                    "<w:basedOn w:val=\"Normal\"/>" +
                    "<w:pPr><w:keepNext/></w:pPr>" +
                    "<w:rPr><w:b/></w:rPr></w:style>"
            )
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/" +
            "wordprocessingml/2006/main\">" +
            styles.toString() +
            "</w:styles>"
    }

    private fun contentTypes(): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/" +
            "content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd." +
            "openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"" +
            "application/vnd.openxmlformats-officedocument." +
            "wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"" +
            "application/vnd.openxmlformats-officedocument." +
            "wordprocessingml.styles+xml\"/>" +
            "</Types>"
    }

    private fun rootRels(): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/" +
            "package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats" +
            ".org/officeDocument/2006/relationships/officeDocument\" " +
            "Target=\"word/document.xml\"/>" +
            "</Relationships>"
    }

    private fun documentRels(): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/" +
            "package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats" +
            ".org/officeDocument/2006/relationships/styles\" " +
            "Target=\"styles.xml\"/>" +
            "</Relationships>"
    }
}

package com.divineit.convertor.domain.pdf

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.divineit.convertor.domain.extraction.ExtractedDocument
import java.io.ByteArrayOutputStream

/**
 * Text-layout PDF writer (Phase 5): renders an [ExtractedDocument] to
 * a simple, clean PDF with Android's built-in PdfDocument — headings
 * in larger bold serif, body in 12pt serif, tables as pipe-separated
 * rows. This is a reading layout generated from the extracted text,
 * NOT a reproduction of the source document's design; conversion UI
 * copy says exactly that. DEVICE-ONLY (android.graphics).
 */
object PdfTextWriter {

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 48
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN

    private class Block(val layout: StaticLayout, val spaceAfter: Int)

    fun write(document: ExtractedDocument): ByteArray {
        val blocks = buildBlocks(document)
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(pageInfo(pageNumber))
        var usedHeight = 0 // points of content height used on this page

        fun newPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(pageInfo(pageNumber))
            usedHeight = 0
        }

        for (block in blocks) {
            var drawn = 0
            val height = block.layout.height
            while (drawn < height) {
                val available = CONTENT_HEIGHT - usedHeight
                if (available <= 0) {
                    newPage()
                    continue
                }
                val slice = minOf(available, height - drawn)
                val canvas = page.canvas
                canvas.save()
                canvas.clipRect(
                    MARGIN.toFloat(),
                    (MARGIN + usedHeight).toFloat(),
                    (MARGIN + CONTENT_WIDTH).toFloat(),
                    (MARGIN + usedHeight + slice).toFloat()
                )
                canvas.translate(
                    MARGIN.toFloat(),
                    (MARGIN + usedHeight - drawn).toFloat()
                )
                block.layout.draw(canvas)
                canvas.restore()
                drawn += slice
                usedHeight += slice
            }
            usedHeight += block.spaceAfter
            if (usedHeight >= CONTENT_HEIGHT && block !== blocks.last()) {
                newPage()
            }
        }
        pdf.finishPage(page)
        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    private fun pageInfo(number: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()

    private fun buildBlocks(document: ExtractedDocument): List<Block> {
        val body = TextPaint().apply {
            typeface = Typeface.SERIF
            textSize = 12f
            color = Color.BLACK
        }
        val blocks = mutableListOf<Block>()
        for (paragraph in document.paragraphs) {
            val level = paragraph.headingLevel
            if (level != null && level in 1..9) {
                val paint = TextPaint().apply {
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    textSize = (22 - 1.5f * level).coerceAtLeast(13f)
                    color = Color.BLACK
                }
                blocks.add(Block(layout(paragraph.text, paint), spaceAfter = 10))
            } else {
                blocks.add(Block(layout(paragraph.text, body), spaceAfter = 8))
            }
        }
        for (table in document.tables) {
            for (row in table.rows) {
                val line = row.joinToString(" | ")
                blocks.add(Block(layout(line, body), spaceAfter = 2))
            }
            if (table !== document.tables.last()) {
                blocks.add(Block(layout("", body), spaceAfter = 6))
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(Block(layout("", body), spaceAfter = 0))
        }
        return blocks
    }

    private fun layout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
}

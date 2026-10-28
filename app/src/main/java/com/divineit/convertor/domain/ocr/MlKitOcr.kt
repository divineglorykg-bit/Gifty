package com.divineit.convertor.domain.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * ML Kit OCR — DEVICE-ONLY. This file is NOT compiled by the JVM harness
 * and has never run in this environment. It needs the dependency
 * `com.google.mlkit:text-recognition:16.0.0` (see app/build.gradle.kts).
 * If Gradle cannot resolve that artifact, delete this file, remove the
 * dependency line, and remove the OCR branches in ComparisonViewModel —
 * everything else builds without it.
 *
 * ML Kit's Latin recogniser does not officially support the Twi open
 * vowels ɛ/ɔ; expect them to come back as e/o and be caught by the
 * Unicode watchlist in comparison, not silently trusted. A Tesseract
 * engine with a custom .traineddata (the plan's fallback) can replace
 * this implementation behind the same OcrEngine interface.
 */
class MlKitOcrEngine : OcrEngine {

    override fun recognizeText(
        imageBytes: ByteArray,
        onResult: (Result<String>) -> Unit
    ) {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (bitmap == null) {
            onResult(Result.failure(IllegalStateException("Image could not be decoded.")))
            return
        }
        recognizeBitmap(bitmap, onResult)
    }

    fun recognizeBitmap(bitmap: Bitmap, onResult: (Result<String>) -> Unit) {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        client.process(image)
            .addOnSuccessListener { visionText ->
                client.close()
                onResult(Result.success(visionText.text))
            }
            .addOnFailureListener { error ->
                client.close()
                onResult(Result.failure(error))
            }
    }
}

/** OCR for a scanned PDF: renders each page with PdfRenderer at 2x and
 *  recognises it. Device-only, like the engine above. Page texts are
 *  joined with blank lines so page boundaries become paragraph breaks. */
object ScannedPdfOcr {

    fun ocrAllPages(
        resolver: ContentResolver,
        uri: Uri,
        onResult: (Result<String>) -> Unit
    ) {
        val descriptor = resolver.openFileDescriptor(uri, "r")
        if (descriptor == null) {
            onResult(
                Result.failure(
                    IllegalStateException("Could not open the PDF for OCR.")
                )
            )
            return
        }
        val renderer = PdfRenderer(descriptor)
        val engine = MlKitOcrEngine()
        val pageTexts = mutableListOf<String>()
        fun nextPage(index: Int) {
            if (index >= renderer.pageCount) {
                renderer.close()
                descriptor.close()
                onResult(Result.success(pageTexts.joinToString("\n\n")))
                return
            }
            val page = renderer.openPage(index)
            val bitmap = Bitmap.createBitmap(
                page.width * 2,
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(
                bitmap, null, null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            page.close()
            engine.recognizeBitmap(bitmap) { result ->
                result.onSuccess { text ->
                    pageTexts.add(text.trim())
                    nextPage(index + 1)
                }
                result.onFailure { error ->
                    renderer.close()
                    descriptor.close()
                    onResult(Result.failure(error))
                }
            }
        }
        nextPage(0)
    }
}

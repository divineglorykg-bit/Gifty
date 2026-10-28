package com.divineit.convertor.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import com.divineit.convertor.domain.DocumentFormat
import com.divineit.convertor.domain.FileValidation
import com.divineit.convertor.domain.extraction.ExtractedDocument
import com.divineit.convertor.domain.extraction.ExtractorFactory
import com.divineit.convertor.domain.extraction.PdfExtractor
import com.divineit.convertor.domain.extraction.TxtExtractor
import com.divineit.convertor.domain.ocr.MlKitOcrEngine
import com.divineit.convertor.domain.ocr.ScannedPdfOcr
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** A file picked in one of the Phase 5 tool screens: its bytes are
 *  read once (64 MB cap, same as comparison) and its format detected
 *  from content, never from the extension alone. */
internal class PickedDocument(
    val uri: Uri,
    val displayName: String,
    val format: DocumentFormat,
    val bytes: ByteArray
)

/** Shared file loading and extraction for the tool screens, following
 *  the same rules as the comparison flow: PDF text layer first with
 *  an OCR fallback for scanned pages, images by OCR, everything else
 *  through the pure-Kotlin extractors. */
internal object ToolFiles {

    private const val CAP_BYTES = 64L * 1024 * 1024

    fun displayName(application: Application, uri: Uri): String {
        val resolver = application.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    fun load(application: Application, uri: Uri): PickedDocument {
        val name = displayName(application, uri)
        val bytes = readBytes(application, uri, name)
        val format = FileValidation.detect(bytes, name).format
        return PickedDocument(uri, name, format, bytes)
    }

    fun readBytes(application: Application, uri: Uri, name: String): ByteArray {
        val resolver = application.contentResolver
        return resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > CAP_BYTES) {
                    throw IllegalStateException(
                        "File “$name” is larger than 64 MB and cannot be opened yet."
                    )
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw IllegalStateException(
            "Could not open “$name”. It may have moved or its permission " +
                "expired — please pick it again."
        )
    }

    suspend fun extract(application: Application, picked: PickedDocument): ExtractedDocument {
        when (picked.format) {
            DocumentFormat.PDF -> {
                val extracted = PdfExtractor().extract(picked.bytes, picked.displayName)
                if (extracted.paragraphs.isNotEmpty()) return extracted
                val text = ocrPdf(application, picked.uri)
                if (text.isBlank()) {
                    throw IllegalStateException(
                        "“${picked.displayName}” appears to be a scanned PDF " +
                            "with no text layer, and OCR found no readable text."
                    )
                }
                return TxtExtractor().extract(
                    text.toByteArray(Charsets.UTF_8), picked.displayName
                )
            }
            DocumentFormat.IMAGE -> {
                val text = ocrImage(picked.bytes)
                if (text.isBlank()) {
                    throw IllegalStateException(
                        "OCR found no readable text in “${picked.displayName}”."
                    )
                }
                return TxtExtractor().extract(
                    text.toByteArray(Charsets.UTF_8), picked.displayName
                )
            }
            else -> {
                val extractor = ExtractorFactory.extractorFor(picked.format)
                    ?: throw IllegalStateException(
                        "Files of this type cannot be read yet " +
                            "(${picked.displayName})."
                    )
                return extractor.extract(picked.bytes, picked.displayName)
            }
        }
    }

    suspend fun ocrImage(bytes: ByteArray): String =
        suspendCancellableCoroutine { continuation ->
            MlKitOcrEngine().recognizeText(bytes) { result ->
                result.onSuccess { continuation.resumeWith(Result.success(it)) }
                result.onFailure { continuation.resumeWithException(it) }
            }
        }

    suspend fun ocrPdf(application: Application, uri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            ScannedPdfOcr.ocrAllPages(application.contentResolver, uri) { result ->
                result.onSuccess { continuation.resumeWith(Result.success(it)) }
                result.onFailure { continuation.resumeWithException(it) }
            }
        }
}

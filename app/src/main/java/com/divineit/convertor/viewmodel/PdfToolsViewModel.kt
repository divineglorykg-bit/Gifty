package com.divineit.convertor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.domain.DocumentFormat
import com.divineit.convertor.domain.convert.TxtWriter
import com.divineit.convertor.domain.extraction.PdfExtractor
import com.divineit.convertor.domain.pdf.PdfTools
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PDF Tools (Phase 5): page count, merge two PDFs, split into
 *  per-page PDFs (delivered as one ZIP), rotate all pages, and text
 *  extraction. Operations produce output bytes the screen saves
 *  through the system file picker. */
class PdfToolsViewModel(application: Application) : AndroidViewModel(application) {

    data class State(
        val busy: Boolean = false,
        val pdfName: String? = null,
        val pageCount: Int? = null,
        val secondName: String? = null,
        val message: String? = null,
        val outputLabel: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var firstBytes: ByteArray? = null
    private var secondBytes: ByteArray? = null

    var outputBytes: ByteArray? = null
        private set
    var outputMime: String = "application/pdf"
        private set

    fun pickFirst(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, outputLabel = null)
            outputBytes = null
            try {
                val (picked, pages) = withContext(Dispatchers.IO) {
                    val loaded = ToolFiles.load(getApplication(), uri)
                    if (loaded.format != DocumentFormat.PDF) {
                        throw IllegalStateException(
                            "“${loaded.displayName}” is not a PDF. PDF Tools work on PDF files."
                        )
                    }
                    loaded to PdfTools.pageCount(loaded.bytes)
                }
                firstBytes = picked.bytes
                _state.value = _state.value.copy(
                    busy = false,
                    pdfName = picked.displayName,
                    pageCount = pages,
                    message = "PDF loaded: $pages page(s)."
                )
            } catch (e: Exception) {
                firstBytes = null
                _state.value = _state.value.copy(
                    busy = false,
                    pdfName = null,
                    pageCount = null,
                    message = e.message ?: "That PDF could not be opened."
                )
            }
        }
    }

    fun pickSecond(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            try {
                val picked = withContext(Dispatchers.IO) {
                    val loaded = ToolFiles.load(getApplication(), uri)
                    if (loaded.format != DocumentFormat.PDF) {
                        throw IllegalStateException(
                            "“${loaded.displayName}” is not a PDF."
                        )
                    }
                    loaded
                }
                secondBytes = picked.bytes
                _state.value = _state.value.copy(
                    busy = false,
                    secondName = picked.displayName,
                    message = "Second PDF ready. Merge will place it after the first."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = e.message ?: "That PDF could not be opened."
                )
            }
        }
    }

    private fun requireFirst(): ByteArray? {
        val bytes = firstBytes
        if (bytes == null) {
            _state.value = _state.value.copy(message = "Pick a PDF first.")
        }
        return bytes
    }

    private fun run(label: String, mime: String, work: () -> ByteArray) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, outputLabel = null)
            try {
                val bytes = withContext(Dispatchers.Default) { work() }
                outputBytes = bytes
                outputMime = mime
                _state.value = _state.value.copy(
                    busy = false,
                    outputLabel = label,
                    message = "$label is ready. Save it to keep the file."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = e.message ?: "The operation failed."
                )
            }
        }
    }

    fun merge() {
        val first = requireFirst() ?: return
        val second = secondBytes ?: run {
            _state.value = _state.value.copy(
                message = "Pick a second PDF to merge with the first."
            )
            return
        }
        run("Merged PDF", "application/pdf") {
            PdfTools.merge(listOf(first, second))
        }
    }

    fun split() {
        val first = requireFirst() ?: return
        run("Split pages (ZIP of one PDF per page)", "application/zip") {
            val pages = PdfTools.splitPerPage(first)
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                pages.forEachIndexed { i, pageBytes ->
                    zip.putNextEntry(ZipEntry("page-${i + 1}.pdf"))
                    zip.write(pageBytes)
                    zip.closeEntry()
                }
            }
            out.toByteArray()
        }
    }

    fun rotate() {
        val first = requireFirst() ?: return
        run("Rotated PDF", "application/pdf") {
            PdfTools.rotateAll(first, 90)
        }
    }

    fun extractText() {
        val first = requireFirst() ?: return
        val name = _state.value.pdfName ?: "document.pdf"
        run("Extracted text", "text/plain") {
            val doc = PdfExtractor().extract(first, name)
            if (doc.paragraphs.isEmpty()) {
                throw IllegalStateException(
                    "This PDF has no text layer — it looks like a scan. " +
                        "Use the OCR screen to read it instead."
                )
            }
            TxtWriter.write(doc)
        }
    }
}

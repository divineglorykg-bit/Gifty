package com.divineit.convertor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.domain.DocumentFormat
import com.divineit.convertor.domain.export.DocxWriter
import com.divineit.convertor.domain.extraction.TxtExtractor
import com.divineit.convertor.domain.progress.WorkProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** OCR and Text Extraction (Phase 5): pick an image or a PDF and
 *  read its text with on-device ML Kit OCR. Scanned PDFs are read
 *  page by page. Latin-script recognition: characters such as ɛ and ɔ
 *  may come back as e and o — the comparison flow flags those, and
 *  this screen does not silently "fix" them either. */
class OcrViewModel(application: Application) : AndroidViewModel(application) {

    data class State(
        val busy: Boolean = false,
        val sourceName: String? = null,
        val text: String? = null,
        val message: String? = null,
        val progress: WorkProgress? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    var outputBytes: ByteArray? = null
        private set

    fun pick(uri: Uri) {
        viewModelScope.launch {
            _state.value = State(
                busy = true,
                progress = WorkProgress("Reading text…")
            )
            outputBytes = null
            try {
                val picked = withContext(Dispatchers.IO) {
                    ToolFiles.load(getApplication(), uri)
                }
                val text = when (picked.format) {
                    DocumentFormat.IMAGE ->
                        ToolFiles.ocrImage(picked.bytes)
                    DocumentFormat.PDF ->
                        ToolFiles.ocrPdf(getApplication(), picked.uri) { done, total ->
                            _state.value = _state.value.copy(
                                progress = WorkProgress(
                                    "Reading scanned PDF — page $done of $total",
                                    done, total
                                )
                            )
                        }
                    else -> throw IllegalStateException(
                        "Pick an image or a PDF — OCR reads pictures of text."
                    )
                }
                _state.value = if (text.isBlank()) {
                    State(
                        sourceName = picked.displayName,
                        message = "OCR found no readable text in “${picked.displayName}”."
                    )
                } else {
                    State(
                        sourceName = picked.displayName,
                        text = text,
                        message = "Text read. Save it as TXT or DOCX to keep it."
                    )
                }
            } catch (e: Exception) {
                _state.value = State(
                    message = e.message ?: "OCR failed for that file."
                )
            }
        }
    }

    fun prepareSave(asDocx: Boolean) {
        val text = _state.value.text ?: return
        outputBytes = if (asDocx) {
            val doc = TxtExtractor().extract(
                text.toByteArray(Charsets.UTF_8),
                _state.value.sourceName ?: "ocr-text"
            )
            DocxWriter.write(doc)
        } else {
            text.toByteArray(Charsets.UTF_8)
        }
    }
}

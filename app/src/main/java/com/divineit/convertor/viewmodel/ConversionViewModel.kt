package com.divineit.convertor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.domain.convert.ConversionEngine
import com.divineit.convertor.domain.convert.ConvertTarget
import com.divineit.convertor.domain.extraction.ExtractedDocument
import com.divineit.convertor.domain.pdf.PdfTextWriter
import com.divineit.convertor.domain.progress.WorkProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Document Conversion (Phase 5): pick one file, extract it once,
 *  then render it to the chosen target format. The output bytes are
 *  held here until the screen's save picker writes them out. */
class ConversionViewModel(application: Application) : AndroidViewModel(application) {

    data class State(
        val busy: Boolean = false,
        val sourceName: String? = null,
        val sourceFormat: String? = null,
        val paragraphCount: Int = 0,
        val tableCount: Int = 0,
        val target: ConvertTarget = ConvertTarget.DOCX,
        val message: String? = null,
        val progress: WorkProgress? = null,
        val outputReady: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var extracted: ExtractedDocument? = null

    /** Bytes of the last conversion, consumed by the save picker. */
    var outputBytes: ByteArray? = null
        private set

    val outputTarget: ConvertTarget
        get() = _state.value.target

    fun pick(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = true, message = null, outputReady = false,
                progress = WorkProgress("Reading the file…")
            )
            outputBytes = null
            try {
                val (picked, doc) = withContext(Dispatchers.IO) {
                    val loaded = ToolFiles.load(getApplication(), uri)
                    loaded to ToolFiles.extract(getApplication(), loaded) { p ->
                        _state.value = _state.value.copy(progress = p)
                    }
                }
                extracted = doc
                _state.value = _state.value.copy(
                    busy = false,
                    progress = null,
                    sourceName = picked.displayName,
                    sourceFormat = picked.format.name,
                    paragraphCount = doc.paragraphs.size,
                    tableCount = doc.tables.size,
                    message = "Ready to convert."
                )
            } catch (e: Exception) {
                extracted = null
                _state.value = _state.value.copy(
                    busy = false,
                    progress = null,
                    sourceName = null,
                    message = e.message ?: "That file could not be read."
                )
            }
        }
    }

    fun selectTarget(target: ConvertTarget) {
        _state.value = _state.value.copy(target = target, outputReady = false)
        outputBytes = null
    }

    fun convert() {
        val doc = extracted ?: run {
            _state.value = _state.value.copy(message = "Pick a file first.")
            return
        }
        val target = _state.value.target
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = true, message = null,
                progress = WorkProgress("Writing the ${target.label} file…")
            )
            try {
                val bytes = withContext(Dispatchers.Default) {
                    if (target == ConvertTarget.PDF) {
                        PdfTextWriter.write(doc)
                    } else {
                        ConversionEngine.write(doc, target)
                    }
                }
                outputBytes = bytes
                _state.value = _state.value.copy(
                    busy = false,
                    progress = null,
                    outputReady = true,
                    message = "Converted to ${target.label}. Save it to keep the file."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    progress = null,
                    message = e.message ?: "Conversion failed."
                )
            }
        }
    }

    fun outputName(): String {
        val name = _state.value.sourceName ?: "document"
        return ConversionEngine.outputName(name, _state.value.target)
    }
}

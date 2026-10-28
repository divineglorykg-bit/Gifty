package com.divineit.convertor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.domain.convert.DocumentEditor
import com.divineit.convertor.domain.convert.TxtWriter
import com.divineit.convertor.domain.export.DocxWriter
import com.divineit.convertor.domain.extraction.ExtractedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Document Editor (Phase 5): pick a file, edit its paragraphs
 *  (text, order, headings), find & replace, then save as DOCX or TXT.
 *  Structural edits keep an undo snapshot; keystroke-level text edits
 *  apply directly. Tables are carried through unchanged — this phase
 *  edits paragraph text, and the screen says so. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    data class State(
        val busy: Boolean = false,
        val sourceName: String? = null,
        val document: ExtractedDocument? = null,
        val message: String? = null,
        val canUndo: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val undoStack = ArrayDeque<ExtractedDocument>()

    var outputBytes: ByteArray? = null
        private set

    fun pick(uri: Uri) {
        viewModelScope.launch {
            _state.value = State(busy = true)
            undoStack.clear()
            outputBytes = null
            try {
                val doc = withContext(Dispatchers.IO) {
                    val loaded = ToolFiles.load(getApplication(), uri)
                    ToolFiles.extract(getApplication(), loaded)
                }
                _state.value = State(
                    sourceName = doc.sourceName,
                    document = doc,
                    message = "Edit the paragraphs, then save your document."
                )
            } catch (e: Exception) {
                _state.value = State(
                    message = e.message ?: "That file could not be read."
                )
            }
        }
    }

    private fun apply(newDocument: ExtractedDocument, recordUndo: Boolean) {
        val current = _state.value.document ?: return
        if (recordUndo) {
            undoStack.addLast(current)
            if (undoStack.size > 50) undoStack.removeFirst()
        }
        _state.value = _state.value.copy(
            document = newDocument,
            canUndo = undoStack.isNotEmpty(),
            message = null
        )
        outputBytes = null
    }

    fun updateParagraph(index: Int, text: String) {
        val doc = _state.value.document ?: return
        if (doc.paragraphs.getOrNull(index)?.text == text) return
        apply(DocumentEditor.updateParagraph(doc, index, text), recordUndo = false)
    }

    fun addParagraph(afterIndex: Int) {
        val doc = _state.value.document ?: return
        apply(DocumentEditor.insertParagraph(doc, afterIndex), recordUndo = true)
    }

    fun deleteParagraph(index: Int) {
        val doc = _state.value.document ?: return
        apply(DocumentEditor.deleteParagraph(doc, index), recordUndo = true)
    }

    fun moveParagraph(index: Int, delta: Int) {
        val doc = _state.value.document ?: return
        apply(DocumentEditor.moveParagraph(doc, index, delta), recordUndo = true)
    }

    fun toggleHeading(index: Int) {
        val doc = _state.value.document ?: return
        val current = doc.paragraphs.getOrNull(index)?.headingLevel
        apply(
            DocumentEditor.setHeading(doc, index, if (current == null) 1 else null),
            recordUndo = true
        )
    }

    fun findReplace(find: String, replace: String, ignoreCase: Boolean) {
        val doc = _state.value.document ?: return
        val (updated, count) = DocumentEditor.findReplace(doc, find, replace, ignoreCase)
        if (count == 0) {
            _state.value = _state.value.copy(
                message = "No matches for “$find” in the paragraphs."
            )
            return
        }
        apply(updated, recordUndo = true)
        _state.value = _state.value.copy(
            message = "Replaced $count occurrence(s) of “$find”."
        )
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        _state.value = _state.value.copy(
            document = previous,
            canUndo = undoStack.isNotEmpty(),
            message = "Undone."
        )
        outputBytes = null
    }

    fun wordCount(): Int = _state.value.document?.let { DocumentEditor.wordCount(it) } ?: 0

    fun characterCount(): Int =
        _state.value.document?.let { DocumentEditor.characterCount(it) } ?: 0

    /** Renders the current document for the save picker. */
    fun prepareSave(asDocx: Boolean) {
        val doc = _state.value.document ?: return
        outputBytes = if (asDocx) DocxWriter.write(doc) else TxtWriter.write(doc)
    }
}

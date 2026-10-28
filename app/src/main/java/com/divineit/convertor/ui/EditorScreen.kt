package com.divineit.convertor.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.domain.convert.ConvertTarget
import com.divineit.convertor.ui.components.ProgressBlock
import com.divineit.convertor.viewmodel.EditorViewModel

/** Document Editor (Phase 5): edit a document's paragraphs — rewrite
 *  text, add/remove/reorder paragraphs, mark headings, find &
 *  replace — then save as DOCX or TXT. Tables are shown for reference
 *  but their cells are not edited in this phase; the note in the
 *  header says so plainly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    editorViewModel: EditorViewModel = viewModel()
) {
    val state by editorViewModel.state.collectAsState()
    val context = LocalContext.current
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) editorViewModel.pick(uri)
    }
    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.DOCX.mimeType)
    ) { uri ->
        if (uri != null) {
            editorViewModel.outputBytes?.let { bytes ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Document saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val saveTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.TXT.mimeType)
    ) { uri ->
        if (uri != null) {
            editorViewModel.outputBytes?.let { bytes ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Document saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val document = state.document

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Editor") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.busy) {
                val p = state.progress
                if (p != null) {
                    ProgressBlock(progress = p)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.sourceName == null) "Open a document to edit"
                    else "Open a different document"
                )
            }
            state.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (document != null) {
                Text(
                    "${state.sourceName} — ${document.paragraphs.size} paragraph(s), " +
                        "${editorViewModel.wordCount()} words, " +
                        "${editorViewModel.characterCount()} characters. " +
                        "Tables (${document.tables.size}) are carried through " +
                        "unchanged; this editor changes paragraph text.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            label = { Text("Find") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            label = { Text("Replace with") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ignoreCase,
                                onCheckedChange = { ignoreCase = it }
                            )
                            Text("Ignore case")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    editorViewModel.findReplace(findText, replaceText, ignoreCase)
                                }
                            ) {
                                Text("Replace all")
                            }
                            OutlinedButton(
                                onClick = { editorViewModel.undo() },
                                enabled = state.canUndo
                            ) {
                                Text("Undo")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            editorViewModel.prepareSave(asDocx = true)
                            saveDocx.launch(baseName(state.sourceName) + ".docx")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save as DOCX")
                    }
                    OutlinedButton(
                        onClick = {
                            editorViewModel.prepareSave(asDocx = false)
                            saveTxt.launch(baseName(state.sourceName) + ".txt")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save as TXT")
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(document.paragraphs) { index, paragraph ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    if (paragraph.headingLevel != null) {
                                        "Paragraph ${index + 1} — heading level " +
                                            paragraph.headingLevel
                                    } else {
                                        "Paragraph ${index + 1}"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                                OutlinedTextField(
                                    value = paragraph.text,
                                    onValueChange = {
                                        editorViewModel.updateParagraph(index, it)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row {
                                    TextButton(
                                        onClick = { editorViewModel.toggleHeading(index) }
                                    ) {
                                        Text(
                                            if (paragraph.headingLevel == null) {
                                                "Make heading"
                                            } else {
                                                "Remove heading"
                                            }
                                        )
                                    }
                                    TextButton(
                                        onClick = { editorViewModel.moveParagraph(index, -1) }
                                    ) { Text("Up") }
                                    TextButton(
                                        onClick = { editorViewModel.moveParagraph(index, 1) }
                                    ) { Text("Down") }
                                    TextButton(
                                        onClick = { editorViewModel.addParagraph(index) }
                                    ) { Text("Add below") }
                                    TextButton(
                                        onClick = { editorViewModel.deleteParagraph(index) }
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun baseName(sourceName: String?): String {
    val name = sourceName ?: "document"
    return name.substringBeforeLast('.', name).ifBlank { "document" } + "-edited"
}

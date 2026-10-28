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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.domain.convert.ConvertTarget
import com.divineit.convertor.viewmodel.OcrViewModel

/** OCR and Text Extraction (Phase 5): read the text out of a photo,
 *  screenshot, scan or scanned PDF, on-device. The result is shown
 *  exactly as read — nothing is corrected silently. Latin-script
 *  recognition may return ɛ as e and ɔ as o; proofread names and
 *  words that use them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onBack: () -> Unit,
    ocrViewModel: OcrViewModel = viewModel()
) {
    val state by ocrViewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) ocrViewModel.pick(uri)
    }
    val saveTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.TXT.mimeType)
    ) { uri ->
        if (uri != null) {
            ocrViewModel.outputBytes?.let { bytes ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Text saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.DOCX.mimeType)
    ) { uri ->
        if (uri != null) {
            ocrViewModel.outputBytes?.let { bytes ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Text saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR and Text Extraction") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Reading text… a scanned PDF is read page by page and can take a while.")
            }
            Button(
                onClick = {
                    picker.launch(arrayOf("image/*", "application/pdf"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.sourceName == null) "Pick an image or PDF"
                    else "Pick a different image or PDF"
                )
            }
            if (state.sourceName != null) {
                Text("Source: ${state.sourceName}", style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.text != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ocrViewModel.prepareSave(asDocx = false)
                            saveTxt.launch("ocr-text.txt")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save as TXT")
                    }
                    OutlinedButton(
                        onClick = {
                            ocrViewModel.prepareSave(asDocx = true)
                            saveDocx.launch("ocr-text.docx")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save as DOCX")
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        state.text ?: "",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

package com.divineit.convertor.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.divineit.convertor.viewmodel.PdfToolsViewModel

/** PDF Tools (Phase 5): merge two PDFs, split a PDF into one file
 *  per page (delivered as a single ZIP), rotate every page by 90°,
 *  and extract the text layer to a TXT file. Scanned PDFs have no
 *  text layer — extraction says so and points to OCR. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit,
    pdfToolsViewModel: PdfToolsViewModel = viewModel()
) {
    val state by pdfToolsViewModel.state.collectAsState()
    val context = LocalContext.current

    val pickFirst = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pdfToolsViewModel.pickFirst(uri)
    }
    val pickSecond = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pdfToolsViewModel.pickSecond(uri)
    }

    fun writeAndToast(uri: android.net.Uri?) {
        if (uri == null) return
        val bytes = pdfToolsViewModel.outputBytes ?: return
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        Toast.makeText(context, "File saved.", Toast.LENGTH_SHORT).show()
    }

    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> writeAndToast(uri) }
    val saveZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> writeAndToast(uri) }
    val saveTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> writeAndToast(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Tools") },
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
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Your PDF", style = MaterialTheme.typography.titleMedium)
                    if (state.pdfName != null) {
                        Text("${state.pdfName} — ${state.pageCount} page(s)")
                    }
                    Button(
                        onClick = { pickFirst.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.pdfName == null) "Pick a PDF" else "Pick a different PDF")
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tools", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { pdfToolsViewModel.rotate() },
                        enabled = state.pdfName != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rotate all pages 90°")
                    }
                    OutlinedButton(
                        onClick = { pdfToolsViewModel.split() },
                        enabled = state.pdfName != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Split into one PDF per page (ZIP)")
                    }
                    OutlinedButton(
                        onClick = { pdfToolsViewModel.extractText() },
                        enabled = state.pdfName != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Extract text to TXT")
                    }
                    if (state.secondName != null) {
                        Text("Second PDF: ${state.secondName}")
                    }
                    OutlinedButton(
                        onClick = { pickSecond.launch(arrayOf("application/pdf")) },
                        enabled = state.pdfName != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick a second PDF to merge")
                    }
                    OutlinedButton(
                        onClick = { pdfToolsViewModel.merge() },
                        enabled = state.pdfName != null && state.secondName != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Merge the two PDFs")
                    }
                }
            }
            if (state.outputLabel != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(state.outputLabel ?: "", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = {
                                when (pdfToolsViewModel.outputMime) {
                                    "application/zip" -> saveZip.launch("split-pages.zip")
                                    "text/plain" -> saveTxt.launch("extracted-text.txt")
                                    else -> savePdf.launch("result.pdf")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save result")
                        }
                    }
                }
            }
            state.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.domain.convert.ConvertTarget
import com.divineit.convertor.viewmodel.ConversionViewModel

/** Document Conversion (Phase 5). One file in, one converted file
 *  out. Conversion renders the extracted text, headings and tables —
 *  it does not reproduce the source file's visual design, and the
 *  note under the targets says so. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionScreen(
    onBack: () -> Unit,
    conversionViewModel: ConversionViewModel = viewModel()
) {
    val state by conversionViewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) conversionViewModel.pick(uri)
    }

    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.DOCX.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val saveTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.TXT.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.PDF.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val saveHtml = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.HTML.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val saveRtf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.RTF.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val saveOdt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.ODT.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.CSV.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }
    val saveXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConvertTarget.XLSX.mimeType)
    ) { uri -> writeOutput(context, uri, conversionViewModel) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Conversion") },
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
                    Text("1. Choose a file", style = MaterialTheme.typography.titleMedium)
                    if (state.sourceName != null) {
                        Text(
                            "${state.sourceName} (${state.sourceFormat}) — " +
                                "${state.paragraphCount} paragraph(s), " +
                                "${state.tableCount} table(s)"
                        )
                    }
                    Button(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.sourceName == null) "Pick a file" else "Pick a different file")
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("2. Convert to", style = MaterialTheme.typography.titleMedium)
                    for (target in ConvertTarget.values()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = state.target == target,
                                onClick = { conversionViewModel.selectTarget(target) }
                            )
                            Text(target.label)
                        }
                    }
                    Text(
                        "Conversion carries the text, headings and tables. " +
                            "It does not reproduce the original file's fonts " +
                            "or page design.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { conversionViewModel.convert() },
                        enabled = state.sourceName != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Convert")
                    }
                }
            }
            if (state.outputReady) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("3. Save the converted file", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = {
                                val name = conversionViewModel.outputName()
                                when (state.target) {
                                    ConvertTarget.DOCX -> saveDocx.launch(name)
                                    ConvertTarget.TXT -> saveTxt.launch(name)
                                    ConvertTarget.PDF -> savePdf.launch(name)
                                    ConvertTarget.HTML -> saveHtml.launch(name)
                                    ConvertTarget.RTF -> saveRtf.launch(name)
                                    ConvertTarget.ODT -> saveOdt.launch(name)
                                    ConvertTarget.CSV -> saveCsv.launch(name)
                                    ConvertTarget.XLSX -> saveXlsx.launch(name)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save ${state.target.label}")
                        }
                    }
                }
            }
            state.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

private fun writeOutput(
    context: android.content.Context,
    uri: android.net.Uri?,
    conversionViewModel: ConversionViewModel
) {
    if (uri == null) return
    val bytes = conversionViewModel.outputBytes ?: return
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    Toast.makeText(context, "Converted file saved.", Toast.LENGTH_SHORT).show()
}

package com.divineit.convertor.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.R
import com.divineit.convertor.domain.DocumentSlot
import com.divineit.convertor.theme.Muted
import com.divineit.convertor.ui.components.UploadSlotCard
import com.divineit.convertor.viewmodel.CompareViewModel
import com.divineit.convertor.viewmodel.ComparisonViewModel
import com.divineit.convertor.viewmodel.SettingsViewModel

/** The two-slot comparison workspace. Upload, validation,
 *  preview (the extracted text of any readable file) and project saving
 *  work; COMPARE FILES runs the real extraction + diff engine; and
 *  AUTO-CORRECT, REVIEW CORRECTIONS, UNDO CORRECTIONS, GENERATE REPORT
 *  and EXPORT CORRECTED DOCX are live, wired to the Phase 4 correction
 *  engine with its approval safeguards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    projectId: Long?,
    onBack: () -> Unit,
    onShowDifferences: () -> Unit,
    onShowReview: () -> Unit,
    onShowReport: () -> Unit,
    compareViewModel: CompareViewModel = viewModel(),
    comparisonViewModel: ComparisonViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    ),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val original by compareViewModel.original.collectAsState()
    val product by compareViewModel.product.collectAsState()
    val busy by compareViewModel.busy.collectAsState()
    val message by compareViewModel.message.collectAsState()
    val comparisonState by comparisonViewModel.state.collectAsState()
    val correctionsState by comparisonViewModel.correctionsState.collectAsState()
    val notice by comparisonViewModel.notice.collectAsState()
    val previewState by comparisonViewModel.preview.collectAsState()
    val localOnly by settingsViewModel.localOnly.collectAsState()

    var previewSlot by remember { mutableStateOf<DocumentSlot?>(null) }

    val originalPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) compareViewModel.selectDocument(DocumentSlot.ORIGINAL, uri)
    }
    val productPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) compareViewModel.selectDocument(DocumentSlot.PRODUCT, uri)
    }

    LaunchedEffect(projectId) {
        if (projectId != null) compareViewModel.loadProject(projectId)
    }

    // One-shot navigation: a finished comparison opens the report once.
    LaunchedEffect(Unit) {
        comparisonViewModel.navigateEvent.collect { onShowDifferences() }
    }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument." +
                "wordprocessingml.document"
        )
    ) { uri ->
        if (uri != null) {
            val bytes = comparisonViewModel.correctedDocxBytes()
            if (bytes != null) {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                }
                android.widget.Toast.makeText(
                    context, "Corrected DOCX exported.", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare Two Files") },
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
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (message != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(message!!, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { compareViewModel.clearMessage() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            when (val comparison = comparisonState) {
                is ComparisonViewModel.UiState.Running -> {
                    Text(
                        "Comparing… extracting and aligning both files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is ComparisonViewModel.UiState.Error -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Comparison failed: ${comparison.message}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                else -> {}
            }
            if (notice != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(notice!!, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { comparisonViewModel.clearNotice() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            UploadSlotCard(
                slot = DocumentSlot.ORIGINAL,
                title = stringResource(R.string.slot_original_title),
                subtitle = stringResource(R.string.slot_original_subtitle),
                info = original,
                busy = busy,
                onPick = { originalPicker.launch(arrayOf("*/*")) },
                onRemove = { compareViewModel.removeDocument(DocumentSlot.ORIGINAL) },
                onPreview = { previewSlot = DocumentSlot.ORIGINAL }
            )
            UploadSlotCard(
                slot = DocumentSlot.PRODUCT,
                title = stringResource(R.string.slot_product_title),
                subtitle = stringResource(R.string.slot_product_subtitle),
                info = product,
                busy = busy,
                onPick = { productPicker.launch(arrayOf("*/*")) },
                onRemove = { compareViewModel.removeDocument(DocumentSlot.PRODUCT) },
                onPreview = { previewSlot = DocumentSlot.PRODUCT }
            )

            LaunchedEffect(previewSlot) {
                val slot = previewSlot
                if (slot == null) {
                    comparisonViewModel.clearPreview()
                } else {
                    val info = if (slot == DocumentSlot.ORIGINAL) original else product
                    if (info != null) comparisonViewModel.loadPreview(info)
                }
            }

            if (previewSlot != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Preview", style = MaterialTheme.typography.titleMedium)
                        when (val ps = previewState) {
                            is ComparisonViewModel.PreviewUiState.Loading -> {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "Reading the document…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted
                                )
                            }
                            is ComparisonViewModel.PreviewUiState.Ready -> {
                                Text(
                                    "${ps.paragraphCount} paragraphs · " +
                                        "${ps.totalChars} characters",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted
                                )
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 320.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        ps.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (ps.truncated) {
                                    Text(
                                        "Showing the first part of the document.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Muted
                                    )
                                }
                            }
                            is ComparisonViewModel.PreviewUiState.Failed -> {
                                Text(
                                    ps.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted
                                )
                            }
                            ComparisonViewModel.PreviewUiState.Idle -> Unit
                        }
                        TextButton(onClick = { previewSlot = null }) {
                            Text("Close preview")
                        }
                    }
                }
            }

            Text("Actions", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { originalPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.btn_upload_original)) }
                Button(
                    onClick = { productPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.btn_upload_product)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { previewSlot = DocumentSlot.ORIGINAL },
                    enabled = original?.isReady == true,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.btn_preview_original)) }
                OutlinedButton(
                    onClick = { previewSlot = DocumentSlot.PRODUCT },
                    enabled = product?.isReady == true,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.btn_preview_product)) }
            }

            // Comparison (Phase 3) and correction (Phase 4) are live.
            Text(
                stringResource(R.string.note_engines_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Button(
                onClick = {
                    val o = original
                    val p = product
                    if (o != null && p != null) {
                        comparisonViewModel.runComparison(o, p)
                    }
                },
                enabled = original?.isReady == true &&
                    product?.isReady == true &&
                    comparisonState !is ComparisonViewModel.UiState.Running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_compare)) }
            OutlinedButton(
                onClick = onShowDifferences,
                enabled = comparisonState is ComparisonViewModel.UiState.Result,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_differences)) }
            OutlinedButton(
                onClick = {
                    comparisonViewModel.autoCorrect()
                    onShowReview()
                },
                enabled = comparisonState is ComparisonViewModel.UiState.Result,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_autocorrect)) }
            OutlinedButton(
                onClick = {
                    comparisonViewModel.prepareCorrections()
                    onShowReview()
                },
                enabled = comparisonState is ComparisonViewModel.UiState.Result,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_review)) }
            OutlinedButton(
                onClick = { comparisonViewModel.undoAllApplied() },
                enabled = correctionsState.appliedCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_undo)) }
            OutlinedButton(
                onClick = onShowReport,
                enabled = comparisonState is ComparisonViewModel.UiState.Result,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_report)) }
            Button(
                onClick = { exportLauncher.launch("corrected.docx") },
                enabled = correctionsState.appliedCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_export_docx)) }

            Button(
                onClick = { compareViewModel.saveProject(localOnly) },
                enabled = !busy && (original != null || product != null),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_save_project)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


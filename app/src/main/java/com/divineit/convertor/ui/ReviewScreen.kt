package com.divineit.convertor.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.domain.correction.CorrectionStatus
import com.divineit.convertor.domain.correction.PlannedCorrection
import com.divineit.convertor.theme.Muted
import com.divineit.convertor.viewmodel.ComparisonViewModel

/** Correction review (Phase 4). Every planned correction is listed with
 *  its evidence; nothing is applied without approval, and number
 *  changes, moves and structural changes always need an individual tap.
 *  Applied corrections can be undone here, one by one or all at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val context = LocalContext.current
    val correctionsState by viewModel.correctionsState.collectAsState()
    val comparisonState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.prepareCorrections() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument." +
                "wordprocessingml.document"
        )
    ) { uri ->
        if (uri != null) {
            val bytes = viewModel.correctedDocxBytes()
            if (bytes != null) {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                }
                Toast.makeText(context, "Corrected DOCX exported.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Corrections") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        if (comparisonState !is ComparisonViewModel.UiState.Result) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "No comparison has been run yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Pick an original and a product file, then tap " +
                        "COMPARE FILES.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Corrections: ${correctionsState.corrections.size} planned · " +
                                "${correctionsState.pendingCount} pending · " +
                                "${correctionsState.approvedCount} approved · " +
                                "${correctionsState.appliedCount} applied",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val finalResult = correctionsState.correctedResult
                        if (finalResult != null) {
                            Text(
                                "After applied corrections: ${finalResult.status} " +
                                    "(${finalResult.stats.totalDifferences} differences remain)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.applyApproved() },
                                enabled = correctionsState.approvedCount > 0
                            ) { Text("Apply approved") }
                            OutlinedButton(
                                onClick = { viewModel.undoAllApplied() },
                                enabled = correctionsState.appliedCount > 0
                            ) { Text("Undo all") }
                        }
                        Button(
                            onClick = { exportLauncher.launch("corrected.docx") },
                            enabled = correctionsState.appliedCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("EXPORT CORRECTED DOCX") }
                        Text(
                            "AUTO-CORRECT (on the Compare screen) approves " +
                                "only single-token, high-confidence fixes. " +
                                "Number changes, moves and structural " +
                                "changes always need your individual approval here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted
                        )
                    }
                }
            }
            if (correctionsState.prepared &&
                correctionsState.corrections.isEmpty()
            ) {
                item {
                    Text(
                        "No corrections to review — the comparison found " +
                            "no differences.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(correctionsState.corrections) { correction ->
                CorrectionCard(
                    correction = correction,
                    onApprove = { viewModel.approve(correction.id) },
                    onReject = { viewModel.reject(correction.id) },
                    onReopen = { viewModel.reopen(correction.id) },
                    onApply = { viewModel.applyCorrection(correction.id) },
                    onUndo = { viewModel.undoCorrection(correction.id) }
                )
            }
        }
    }
}

@Composable
private fun CorrectionCard(
    correction: PlannedCorrection,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onReopen: () -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${correction.category} · ${correction.confidence}" +
                    if (correction.requiresIndividualApproval) {
                        " · needs individual approval"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                correction.locationLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            if (correction.before.isNotEmpty()) {
                Text(
                    "Product: ${correction.before.take(300)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (correction.after.isNotEmpty()) {
                Text(
                    "Original: ${correction.after.take(300)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                correction.evidence,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Text(
                "Status: ${correction.status}",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (correction.status) {
                    CorrectionStatus.PENDING -> {
                        Button(onClick = onApprove) { Text("Approve") }
                        OutlinedButton(onClick = onReject) { Text("Reject") }
                    }
                    CorrectionStatus.APPROVED -> {
                        Button(onClick = onApply) { Text("Apply now") }
                        OutlinedButton(onClick = onReject) { Text("Reject") }
                    }
                    CorrectionStatus.APPLIED -> {
                        OutlinedButton(onClick = onUndo) { Text("Undo") }
                    }
                    CorrectionStatus.REJECTED, CorrectionStatus.UNDONE -> {
                        OutlinedButton(onClick = onReopen) {
                            Text("Reconsider")
                        }
                    }
                }
            }
        }
    }
}

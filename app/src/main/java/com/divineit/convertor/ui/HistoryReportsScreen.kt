package com.divineit.convertor.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.viewmodel.ReportsViewModel

/** Verification Reports (home entry, Phase 5): the correction
 *  history recorded by the Phase 4 audit log, grouped by project.
 *  The per-comparison Verification Report still lives in the Compare
 *  flow; this screen is the history of every decision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryReportsScreen(
    onBack: () -> Unit,
    reportsViewModel: ReportsViewModel = viewModel()
) {
    val state by reportsViewModel.state.collectAsState()
    val context = LocalContext.current

    val saveReport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            state.reportText?.let { text ->
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Report saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verification Reports") },
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
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.reportText != null) {
                Text(
                    state.reportTitle ?: "Report",
                    style = MaterialTheme.typography.titleMedium
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        state.reportText ?: "",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Button(
                    onClick = { saveReport.launch("correction-history.txt") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save report as TXT")
                }
                OutlinedButton(
                    onClick = { reportsViewModel.closeReport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to the list")
                }
            } else {
                Text(
                    "Every correction you approved, rejected, applied or " +
                        "undid is recorded. Choose a project to read its " +
                        "full history.",
                    style = MaterialTheme.typography.bodyMedium
                )
                for (group in state.groups) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reportsViewModel.openGroup(group) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(group.title, style = MaterialTheme.typography.titleMedium)
                            Text("${group.entryCount} recorded decision(s)")
                        }
                    }
                }
                state.message?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(
                    onClick = { reportsViewModel.refresh() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

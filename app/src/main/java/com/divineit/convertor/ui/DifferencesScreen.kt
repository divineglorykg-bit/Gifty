package com.divineit.convertor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.divineit.convertor.domain.diff.Difference
import com.divineit.convertor.domain.diff.VerificationStatus
import com.divineit.convertor.theme.Muted
import com.divineit.convertor.viewmodel.ComparisonViewModel

/** The comparison report (Phase 3). It lists every difference the engine
 *  found, with its evidence. Nothing here changes any file — correction
 *  is Phase 4. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifferencesScreen(
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Differences") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        when (val current = state) {
            is ComparisonViewModel.UiState.Result -> {
                val result = current.result
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
                                    statusLabel(result.status),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Original: ${current.originalName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted
                                )
                                Text(
                                    "Product: ${current.productName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted
                                )
                                Text(
                                    "Paragraphs: ${result.stats.originalParagraphs} " +
                                        "→ ${result.stats.productParagraphs}; " +
                                        "words: ${result.stats.originalWords} → " +
                                        result.stats.productWords,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Differences: ${result.stats.totalDifferences}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                for ((category, count) in result.stats.byCategory) {
                                    Text(
                                        "$category: $count",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    if (result.differences.isEmpty()) {
                        item {
                            Text(
                                "No differences found. The extracted content " +
                                    "matches under the Phase 3 comparison.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    items(result.differences) { difference ->
                        DifferenceCard(difference)
                    }
                }
            }
            is ComparisonViewModel.UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    Text(
                        "Comparison failed.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
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
            }
        }
    }
}

private fun statusLabel(status: VerificationStatus): String {
    return when (status) {
        VerificationStatus.CONTENT_MATCH_VERIFIED -> "CONTENT MATCH VERIFIED"
        VerificationStatus.CONTENT_MATCH_WITH_WARNINGS ->
            "CONTENT MATCH WITH WARNINGS"
        VerificationStatus.DIFFERENCES_REMAIN -> "DIFFERENCES REMAIN"
        VerificationStatus.VERIFICATION_INCOMPLETE -> "VERIFICATION INCOMPLETE"
    }
}

private fun locationLabel(difference: Difference): String {
    return when {
        difference.paragraphIndex != null ->
            "Paragraph ${difference.paragraphIndex + 1}"
        difference.tableIndex != null -> {
            val base = "Table ${difference.tableIndex + 1}"
            val row = difference.rowIndex?.let { ", row ${it + 1}" } ?: ""
            val cell = difference.cellIndex?.let { ", cell ${it + 1}" } ?: ""
            base + row + cell
        }
        else -> "Document"
    }
}

@Composable
private fun DifferenceCard(difference: Difference) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${difference.category} · ${difference.confidence}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                locationLabel(difference),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            if (difference.originalText.isNotEmpty()) {
                Text(
                    "Original: ${difference.originalText.take(500)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (difference.productText.isNotEmpty()) {
                Text(
                    "Product: ${difference.productText.take(500)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                difference.evidence,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }
    }
}

package com.divineit.convertor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divineit.convertor.R
import com.divineit.convertor.navigation.Routes
import com.divineit.convertor.theme.Muted

private data class Destination(
    val title: String,
    val subtitle: String,
    val route: String,
    val comingSoon: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val destinations = listOf(
        Destination(
            "Compare Two Files",
            "Original file against product file, difference by difference.",
            Routes.compareRoute()
        ),
        Destination(
            "Document Conversion",
            "Convert between PDF, DOCX, TXT, RTF, ODT, HTML, CSV and XLSX.",
            Routes.comingSoon("conversion"), comingSoon = true
        ),
        Destination(
            "OCR and Text Extraction",
            "Read scanned PDFs and images into editable text.",
            Routes.comingSoon("ocr"), comingSoon = true
        ),
        Destination(
            "Document Editor",
            "Edit, format, annotate and sign the corrected document.",
            Routes.comingSoon("editor"), comingSoon = true
        ),
        Destination(
            "PDF Tools",
            "Pages, splitting, merging and extraction.",
            Routes.comingSoon("pdf_tools"), comingSoon = true
        ),
        Destination(
            "Verification Reports",
            "Independent re-checks and fidelity reports.",
            Routes.comingSoon("reports"), comingSoon = true
        ),
        Destination(
            "Saved Projects",
            "Reopen a comparison, or delete one.",
            Routes.PROJECTS
        ),
        Destination(
            "Settings and Privacy",
            "Local-only processing, theme and text size.",
            Routes.SETTINGS
        )
    )
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(destinations) { destination ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigate(destination.route) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            destination.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            destination.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted
                        )
                        if (destination.comingSoon) {
                            Text(
                                "Available in a later phase — see the Phase 1 plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted
                            )
                        }
                    }
                }
            }
        }
    }
}

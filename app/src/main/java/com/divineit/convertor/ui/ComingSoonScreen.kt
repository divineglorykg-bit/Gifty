package com.divineit.convertor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divineit.convertor.theme.Muted

/** Honest placeholder for destinations whose phase has not landed yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(
    title: String,
    phase: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "This screen arrives in $phase.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "It is listed on the home screen so the full scope stays " +
                    "visible, but nothing here is presented as working yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }
    }
}

package com.divineit.convertor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.R
import com.divineit.convertor.theme.Muted
import com.divineit.convertor.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val localOnly by viewModel.localOnly.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val textScale by viewModel.textScale.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings and Privacy") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Local processing only",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = localOnly,
                        onCheckedChange = { viewModel.setLocalOnly(it) }
                    )
                }
                Text(
                    "When on, no document or text is sent to any online " +
                        "service. Cloud AI and cloud OCR (optional, Phase 4) " +
                        "stay disabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }

            Column {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice("System", "system", theme) {
                        viewModel.setTheme(it)
                    }
                    ThemeChoice("Light", "light", theme) {
                        viewModel.setTheme(it)
                    }
                    ThemeChoice("Dark", "dark", theme) {
                        viewModel.setTheme(it)
                    }
                }
            }

            Column {
                Text("Text size", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Current: ${"%.1f".format(textScale)}×",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                Slider(
                    value = textScale,
                    onValueChange = { viewModel.setTextScale(it) },
                    valueRange = 0.8f..1.3f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: String,
    current: String,
    onSelect: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onSelect(value) },
        enabled = current != value
    ) { Text(label) }
}

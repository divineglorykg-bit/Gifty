package com.divineit.convertor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divineit.convertor.domain.DocumentInfo
import com.divineit.convertor.domain.DocumentSlot
import com.divineit.convertor.domain.DocumentStatus
import com.divineit.convertor.theme.Bad
import com.divineit.convertor.theme.Good
import com.divineit.convertor.theme.Muted

/** One upload slot: title, file details when selected, and pick/replace/
 *  remove/preview actions. The slot never claims more than validation
 *  proved: page count shows "—" when unknown, and an estimate is labelled. */
@Composable
fun UploadSlotCard(
    slot: DocumentSlot,
    title: String,
    subtitle: String,
    info: DocumentInfo?,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(modifier = Modifier.height(12.dp))
            if (info == null) {
                Text("No file selected.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onPick, enabled = !busy) {
                    Text("Browse files")
                }
            } else {
                DetailRow("File", info.displayName)
                DetailRow("Format", info.extension.ifBlank { "unknown" }.uppercase())
                DetailRow("Size", formatSize(info.sizeBytes))
                DetailRow(
                    "Pages",
                    when {
                        info.pageCount != null && info.pageCountEstimate ->
                            "${info.pageCount} (estimate)"
                        info.pageCount != null -> "${info.pageCount}"
                        else -> "—"
                    }
                )
                DetailRow("Type", info.kind.name.replace('_', ' '))
                DetailRow(
                    "Status",
                    when (info.status) {
                        DocumentStatus.READY -> "Ready"
                        DocumentStatus.ERROR -> "Problem"
                        DocumentStatus.VALIDATING -> "Checking…"
                    }
                )
                if (info.sha256Short != null) {
                    DetailRow("SHA-256", info.sha256Short!!)
                }
                Text(
                    info.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (info.status) {
                        DocumentStatus.ERROR -> Bad
                        DocumentStatus.READY -> Good
                        else -> Muted
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPick, enabled = !busy) {
                        Text("Replace")
                    }
                    TextButton(onClick = onRemove, enabled = !busy) {
                        Text("Remove")
                    }
                    OutlinedButton(
                        onClick = onPreview,
                        enabled = info.status == DocumentStatus.READY
                    ) {
                        Text("Preview")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

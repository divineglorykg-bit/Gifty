package com.divineit.convertor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divineit.convertor.data.ProjectEntity
import com.divineit.convertor.theme.Muted
import com.divineit.convertor.viewmodel.ProjectsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenProject: (ProjectEntity) -> Unit,
    viewModel: ProjectsViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    var pendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete project?") },
            text = {
                Text(
                    "“${pendingDelete!!.name}” will be removed from saved " +
                        "projects. The source files themselves are not touched."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(pendingDelete!!.id)
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Projects") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "No saved projects yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                Text(
                    "Save one from the Compare screen with SAVE PROJECT.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(projects) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Original: ${project.originalName ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted
                            )
                            Text(
                                "Product: ${project.productName ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted
                            )
                            Text(
                                "Saved " + SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.getDefault()
                                ).format(Date(project.createdAt)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenProject(project) }
                                ) { Text("Open") }
                                TextButton(
                                    onClick = { pendingDelete = project }
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

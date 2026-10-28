package com.divineit.convertor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.ConvertorApp
import com.divineit.convertor.data.AuditLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Verification Reports (Phase 5 wiring of the Phase 4 audit log):
 *  every correction decision ever recorded — approvals, rejections,
 *  applications, undos — grouped by saved project, plus the unfiled
 *  session group (projectId 0) for comparisons that were never saved
 *  as projects. A group's entries can be read as a plain-text report
 *  and saved. This screen reports what the audit log holds; it does
 *  not re-verify documents (that happens in the Compare flow). */
class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    data class Group(
        val projectId: Long,
        val title: String,
        val entryCount: Int
    )

    data class State(
        val loading: Boolean = true,
        val groups: List<Group> = emptyList(),
        val reportTitle: String? = null,
        val reportText: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val repository
        get() = (getApplication() as ConvertorApp).projectRepository

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val projects = repository.projects().first()
            val groups = mutableListOf<Group>()
            val sessionCount = repository.auditLog(0).first().size
            if (sessionCount > 0) {
                groups.add(Group(0, "Unfiled session corrections", sessionCount))
            }
            for (project in projects) {
                val count = repository.auditLog(project.id).first().size
                if (count > 0) {
                    groups.add(Group(project.id, project.name, count))
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                groups = groups,
                message = if (groups.isEmpty()) {
                    "No correction history yet. Reports appear here after " +
                        "you review corrections in a comparison."
                } else {
                    null
                }
            )
        }
    }

    fun openGroup(group: Group) {
        viewModelScope.launch {
            val entries = repository.auditLog(group.projectId).first()
            _state.value = _state.value.copy(
                reportTitle = group.title,
                reportText = formatReport(group.title, entries)
            )
        }
    }

    fun closeReport() {
        _state.value = _state.value.copy(reportTitle = null, reportText = null)
    }

    private fun formatReport(title: String, entries: List<AuditLogEntity>): String {
        val ordered = entries.sortedBy { it.timestamp }
        val out = StringBuilder()
        out.append("DIVINE IT CONVERTOR — CORRECTION HISTORY REPORT\n")
        out.append("Project: ").append(title).append('\n')
        out.append("Entries: ").append(ordered.size).append("\n\n")
        for (entry in ordered) {
            out.append(timestamp(entry.timestamp))
                .append(' ').append(entry.action)
                .append(" [").append(entry.category).append("] ")
                .append('(').append(entry.mode)
            when (entry.approved) {
                true -> out.append(", approved")
                false -> out.append(", not approved")
                null -> out.append("")
            }
            out.append(")\n")
            if (!entry.oldValue.isNullOrBlank() || !entry.newValue.isNullOrBlank()) {
                out.append("  before: ").append(entry.oldValue ?: "").append('\n')
                out.append("  after:  ").append(entry.newValue ?: "").append('\n')
            }
            if (!entry.evidence.isNullOrBlank()) {
                out.append("  evidence: ").append(entry.evidence).append('\n')
            }
        }
        return out.toString()
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}

package com.divineit.convertor.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Audit log for every correction (Phase 4). The table exists from Phase 2
 *  so the schema never needs a destructive migration later; no rows are
 *  written until the correction engine lands. */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val action: String,
    @ColumnInfo(name = "old_value") val oldValue: String?,
    @ColumnInfo(name = "new_value") val newValue: String?,
    val category: String,
    val evidence: String?,
    val mode: String,
    val timestamp: Long,
    val approved: Boolean?
)

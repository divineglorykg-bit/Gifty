package com.divineit.convertor.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved comparison project. Only file references are stored, never file
 *  contents — the original and product files stay where the user keeps them.
 *  If a file is moved or deleted, the app reports the missing reference and
 *  asks the user to locate it again (Phase 2 shows the error state). */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "original_uri") val originalUri: String?,
    @ColumnInfo(name = "product_uri") val productUri: String?,
    @ColumnInfo(name = "original_name") val originalName: String?,
    @ColumnInfo(name = "product_name") val productName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "local_only") val localOnly: Boolean
)

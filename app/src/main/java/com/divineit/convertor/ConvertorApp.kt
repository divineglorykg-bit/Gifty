package com.divineit.convertor

import android.app.Application
import com.divineit.convertor.data.AppDatabase
import com.divineit.convertor.data.ProjectRepository
import com.divineit.convertor.data.SettingsDataStore

/** Application holder for the Room database, repository and settings store.
 *  No document content is held here; only project references and settings. */
class ConvertorApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.auditLogDao())
    }
    val settings: SettingsDataStore by lazy { SettingsDataStore(this) }
}

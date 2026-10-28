package com.divineit.convertor

import android.app.Application
import com.divineit.convertor.data.AppDatabase
import com.divineit.convertor.data.ProjectRepository
import com.divineit.convertor.data.SettingsDataStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/** Application holder for the Room database, repository and settings store.
 *  No document content is held here; only project references and settings. */
class ConvertorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android must load its bundled resources (CMaps, fonts)
        // before any PDF is opened; without this, text extraction can
        // fail on real-world PDFs. Added in Phase 5 — earlier phases
        // missed it.
        PDFBoxResourceLoader.init(this)
    }
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.auditLogDao())
    }
    val settings: SettingsDataStore by lazy { SettingsDataStore(this) }
}

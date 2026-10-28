package com.divineit.convertor.navigation

/** Navigation destinations. Phase 2 implements home, compare, projects and
 *  settings; every other destination shows ComingSoonScreen with the phase
 *  that delivers it, so nothing is presented as working before it exists. */
object Routes {
    const val HOME = "home"

    /** projectId is -1 when no project is being opened. */
    const val COMPARE = "compare?projectId={projectId}"
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"
    const val COMING_SOON = "coming_soon/{key}"
    const val DIFFERENCES = "differences"
    const val REVIEW = "review"
    const val REPORT = "report"

    fun compareRoute(projectId: Long? = null) =
        "compare?projectId=${projectId ?: -1}"

    fun comingSoon(key: String) = "coming_soon/$key"
}

/** Key -> (screen title, delivering phase) for not-yet-built destinations. */
val COMING_SOON_SCREENS = mapOf(
    "conversion" to ("Document Conversion" to "Phase 5"),
    "ocr" to ("OCR and Text Extraction" to
        "Phase 4, built into the Compare screen for scanned PDFs and images"),
    "editor" to ("Document Editor" to "Phase 5"),
    "pdf_tools" to ("PDF Tools" to "Phase 5"),
    "reports" to ("Verification Reports" to "Phase 4")
)

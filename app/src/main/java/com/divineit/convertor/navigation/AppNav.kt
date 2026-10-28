package com.divineit.convertor.navigation

/** Navigation destinations. Phase 2 implemented home, compare, projects
 *  and settings; Phases 3–4 added the comparison flow; Phase 5 wired the
 *  five remaining home destinations (conversion, OCR, editor, PDF tools,
 *  reports) to real screens, so nothing on the home screen is a
 *  placeholder any more. The coming-soon route remains for future
 *  destinations, with an empty map. */
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
    const val CONVERSION = "conversion"
    const val OCR = "ocr"
    const val EDITOR = "editor"
    const val PDF_TOOLS = "pdf_tools"
    const val REPORTS = "reports"

    fun compareRoute(projectId: Long? = null) =
        "compare?projectId=${projectId ?: -1}"

    fun comingSoon(key: String) = "coming_soon/$key"
}

/** Key -> (screen title, delivering phase) for not-yet-built
 *  destinations. Empty since Phase 5: every home destination is real. */
val COMING_SOON_SCREENS = emptyMap<String, Pair<String, String>>()

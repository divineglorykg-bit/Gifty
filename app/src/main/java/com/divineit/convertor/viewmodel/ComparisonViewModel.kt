package com.divineit.convertor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.ConvertorApp
import com.divineit.convertor.data.AuditLogEntity
import com.divineit.convertor.domain.DocumentFormat
import com.divineit.convertor.domain.DocumentInfo
import com.divineit.convertor.domain.correction.CorrectionApplier
import com.divineit.convertor.domain.correction.CorrectionPlanner
import com.divineit.convertor.domain.correction.CorrectionStatus
import com.divineit.convertor.domain.correction.PlannedCorrection
import com.divineit.convertor.domain.correction.VerificationReport
import com.divineit.convertor.domain.diff.ComparisonResult
import com.divineit.convertor.domain.diff.DiffEngine
import com.divineit.convertor.domain.export.DocxWriter
import com.divineit.convertor.domain.extraction.DocumentExtractor
import com.divineit.convertor.domain.extraction.ExtractedDocument
import com.divineit.convertor.domain.extraction.ExtractorFactory
import com.divineit.convertor.domain.extraction.PdfExtractor
import com.divineit.convertor.domain.extraction.TxtExtractor
import com.divineit.convertor.domain.ocr.MlKitOcrEngine
import com.divineit.convertor.domain.ocr.ScannedPdfOcr
import com.divineit.convertor.domain.progress.WorkProgress
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Comparison (Phase 3) and correction (Phase 4) state. The ViewModel
 * owns the extracted documents and the comparison result; the
 * correction planner, applier and report builder are pure domain code.
 *
 * Every correction status change is written to the Room audit log
 * (projectId 0 = an unsaved compare session) and mirrored into an
 * in-memory audit trail for the verification report. No correction is
 * ever applied without an approval: AUTO-CORRECT approves only the
 * planner's low-risk set, and number changes, moves and structural
 * changes always wait for an individual tap.
 */
class ComparisonViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Idle : UiState
        data object Running : UiState
        data class Result(
            val result: ComparisonResult,
            val originalName: String,
            val productName: String
        ) : UiState
        data class Error(val message: String) : UiState
    }

    data class CorrectionsState(
        val corrections: List<PlannedCorrection> = emptyList(),
        val prepared: Boolean = false,
        val correctedResult: ComparisonResult? = null
    ) {
        val appliedCount: Int
            get() = corrections.count { it.status == CorrectionStatus.APPLIED }
        val approvedCount: Int
            get() = corrections.count { it.status == CorrectionStatus.APPROVED }
        val pendingCount: Int
            get() = corrections.count { it.status == CorrectionStatus.PENDING }
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _corrections = MutableStateFlow(CorrectionsState())
    val correctionsState: StateFlow<CorrectionsState> = _corrections.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    // ---------------------------------------------------------------
    // Document preview (completion build): real extracted text for
    // every readable format, never a placeholder note.
    // ---------------------------------------------------------------

    sealed interface PreviewUiState {
        data object Idle : PreviewUiState
        data object Loading : PreviewUiState
        data class Ready(
            val fileName: String,
            val text: String,
            val totalChars: Int,
            val paragraphCount: Int,
            val truncated: Boolean
        ) : PreviewUiState
        data class Failed(val message: String) : PreviewUiState
    }

    private val _preview = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    val preview: StateFlow<PreviewUiState> = _preview.asStateFlow()

    fun loadPreview(info: DocumentInfo) {
        _preview.value = PreviewUiState.Loading
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) { extractForPreview(info) }
                val full = doc.fullText
                if (full.isBlank()) {
                    _preview.value = PreviewUiState.Failed(
                        "No readable text was found in “${info.displayName}”."
                    )
                    return@launch
                }
                val cap = 6000
                _preview.value = PreviewUiState.Ready(
                    fileName = info.displayName,
                    text = full.take(cap),
                    totalChars = full.length,
                    paragraphCount = doc.paragraphs.size,
                    truncated = full.length > cap
                )
            } catch (e: Exception) {
                _preview.value = PreviewUiState.Failed(
                    e.message ?: "Preview is not available for this file."
                )
            }
        }
    }

    fun clearPreview() {
        _preview.value = PreviewUiState.Idle
    }

    /** Preview reads text only — it never starts OCR. A scanned PDF or
     *  an image gets an honest explanation instead of a long, invisible
     *  OCR run. */
    private fun extractForPreview(info: DocumentInfo): ExtractedDocument {
        if (info.format == DocumentFormat.IMAGE) {
            throw IllegalStateException(
                "Preview does not read images. To read this image's text, " +
                    "open “OCR and Text Extraction” from the home screen."
            )
        }
        val uri = Uri.parse(info.uri)
        val bytes = readBytes(info, uri)
        if (info.format == DocumentFormat.PDF) {
            val extracted = PdfExtractor().extract(bytes, info.displayName)
            if (extracted.paragraphs.isEmpty()) {
                throw IllegalStateException(
                    "This PDF has no text layer — it is a scanned picture — " +
                        "so there is no text to preview."
                )
            }
            return extracted
        }
        val extractor = ExtractorFactory.extractorFor(info.format)
            ?: throw IllegalStateException(
                "Preview is not available for ${info.format} files."
            )
        return extractor.extract(bytes, info.displayName)
    }

    /** One-shot navigation event, so returning from the differences
     *  screen does not immediately re-navigate. */
    private val _navigateEvent = MutableSharedFlow<Unit>()
    val navigateEvent = _navigateEvent.asSharedFlow()

    /** Comparison reads are capped so a huge file cannot exhaust memory. */
    private val capBytes = 64L * 1024 * 1024

    private var lastOriginalDoc: ExtractedDocument? = null
    private var lastProductDoc: ExtractedDocument? = null
    private var lastInitialResult: ComparisonResult? = null
    private var lastOriginalName: String = ""
    private var lastProductName: String = ""
    private var correctedDoc: ExtractedDocument? = null
    private val auditLines = mutableListOf<String>()

    private val repository
        get() = (getApplication() as ConvertorApp).projectRepository

    // ---------------------------------------------------------------
    // Phase 3: comparison
    // ---------------------------------------------------------------

    /** Live progress while a comparison runs: real page percentages
     *  for PDFs (text layer or scanned), a label-only spinner for the
     *  single-step phases. Null when idle. */
    private val _progress = MutableStateFlow<WorkProgress?>(null)
    val progress: StateFlow<WorkProgress?> = _progress.asStateFlow()

    fun runComparison(original: DocumentInfo, product: DocumentInfo) {
        viewModelScope.launch {
            _state.value = UiState.Running
            try {
                _progress.value = WorkProgress("Reading the original…")
                val origDoc = withContext(Dispatchers.IO) {
                    extract(original, "original")
                }
                _progress.value = WorkProgress("Reading the product…")
                val prodDoc = withContext(Dispatchers.IO) {
                    extract(product, "product")
                }
                _progress.value = WorkProgress("Comparing the documents…")
                val result = DiffEngine.compare(origDoc, prodDoc)
                lastOriginalDoc = origDoc
                lastProductDoc = prodDoc
                lastInitialResult = result
                lastOriginalName = original.displayName
                lastProductName = product.displayName
                correctedDoc = null
                auditLines.clear()
                _corrections.value = CorrectionsState()
                _progress.value = null
                _state.value = UiState.Result(
                    result, original.displayName, product.displayName
                )
                _navigateEvent.emit(Unit)
            } catch (e: Exception) {
                _progress.value = null
                _state.value = UiState.Error(
                    e.message ?: "Comparison failed for an unknown reason."
                )
            }
        }
    }

    private suspend fun extract(
        info: DocumentInfo,
        side: String
    ): ExtractedDocument {
        val resolver = getApplication<Application>().contentResolver
        val uri = Uri.parse(info.uri)
        if (info.format == DocumentFormat.PDF) {
            val bytes = readBytes(info, uri)
            val extracted = PdfExtractor { done, total ->
                _progress.value = WorkProgress(
                    "Reading the $side (PDF) — page $done of $total",
                    done, total
                )
            }.extract(bytes, info.displayName)
            if (extracted.paragraphs.isNotEmpty()) return extracted
            // No text layer: this is a scanned PDF. OCR is the honest
            // fallback; if it reads nothing, say so plainly.
            val text = withContext(Dispatchers.IO) {
                ocrPdf(uri) { done, total ->
                    _progress.value = WorkProgress(
                        "Reading the $side (scanned PDF) — " +
                            "page $done of $total",
                        done, total
                    )
                }
            }
            if (text.isBlank()) {
                throw IllegalStateException(
                    "“${info.displayName}” appears to be a scanned PDF " +
                        "with no text layer, and OCR found no readable text."
                )
            }
            return TxtExtractor().extract(
                text.toByteArray(Charsets.UTF_8), info.displayName
            )
        }
        if (info.format == DocumentFormat.IMAGE) {
            val bytes = readBytes(info, uri)
            val text = withContext(Dispatchers.IO) { ocrImage(bytes) }
            if (text.isBlank()) {
                throw IllegalStateException(
                    "OCR found no readable text in “${info.displayName}”."
                )
            }
            return TxtExtractor().extract(
                text.toByteArray(Charsets.UTF_8), info.displayName
            )
        }
        val bytes = readBytes(info, uri)
        val extractor: DocumentExtractor =
            ExtractorFactory.extractorFor(info.format)
                ?: throw IllegalStateException(
                    "No extractor for ${info.format} files yet."
                )
        return extractor.extract(bytes, info.displayName)
    }

    private fun readBytes(info: DocumentInfo, uri: Uri): ByteArray {
        val resolver = getApplication<Application>().contentResolver
        return resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > capBytes) {
                    throw IllegalStateException(
                        "File “${info.displayName}” is larger than 64 MB " +
                            "and cannot be compared yet."
                    )
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw IllegalStateException(
            "Could not open “${info.displayName}”. It may have moved or " +
                "its permission expired — please pick it again."
        )
    }

    private suspend fun ocrImage(bytes: ByteArray): String =
        suspendCancellableCoroutine { continuation ->
            MlKitOcrEngine().recognizeText(bytes) { result ->
                result.onSuccess { continuation.resumeWith(Result.success(it)) }
                result.onFailure { continuation.resumeWithException(it) }
            }
        }

    private suspend fun ocrPdf(
        uri: Uri,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): String =
        suspendCancellableCoroutine { continuation ->
            val resolver = getApplication<Application>().contentResolver
            ScannedPdfOcr.ocrAllPages(resolver, uri, onProgress) { result ->
                result.onSuccess { continuation.resumeWith(Result.success(it)) }
                result.onFailure { continuation.resumeWithException(it) }
            }
        }

    // ---------------------------------------------------------------
    // Phase 4: corrections
    // ---------------------------------------------------------------

    fun clearNotice() {
        _notice.value = null
    }

    /** Builds the correction plan from the last comparison (idempotent). */
    fun prepareCorrections() {
        if (_corrections.value.prepared) return
        val orig = lastOriginalDoc
        val prod = lastProductDoc
        if (orig == null || prod == null) {
            _notice.value = "Run a comparison first, then review corrections."
            return
        }
        viewModelScope.launch {
            val planned = withContext(Dispatchers.Default) {
                CorrectionPlanner.plan(orig, prod)
            }
            _corrections.value = CorrectionsState(
                corrections = planned,
                prepared = true
            )
        }
    }

    fun approve(id: Int) = setStatus(id, CorrectionStatus.APPROVED, "MANUAL")

    fun reject(id: Int) = setStatus(id, CorrectionStatus.REJECTED, "MANUAL")

    fun reopen(id: Int) = setStatus(id, CorrectionStatus.PENDING, "MANUAL")

    private fun setStatus(id: Int, status: CorrectionStatus, mode: String) {
        val current = _corrections.value
        val correction = current.corrections.firstOrNull { it.id == id } ?: return
        if (correction.status == CorrectionStatus.APPLIED) return
        if (correction.status == status) return
        val updated = current.corrections.map {
            if (it.id == id) it.copy(status = status) else it
        }
        _corrections.value = current.copy(corrections = updated)
        val action = when (status) {
            CorrectionStatus.APPROVED -> "APPROVE"
            CorrectionStatus.REJECTED -> "REJECT"
            CorrectionStatus.PENDING -> "REOPEN"
            else -> "STATUS"
        }
        recordAudit(action, correction, mode, status == CorrectionStatus.APPROVED)
    }

    /** AUTO-CORRECT: approve the low-risk set, then apply everything
     *  approved. Individual-approval corrections are never touched. */
    fun autoCorrect() {
        viewModelScope.launch {
            ensurePrepared()
            approveLowRisk(mode = "AUTO")
            applyApproved(mode = "AUTO")
            val remaining = _corrections.value.pendingCount
            _notice.value = if (remaining > 0) {
                "Auto-correct applied the safe corrections. " +
                    "$remaining change(s) still need your individual review."
            } else {
                "Auto-correct finished."
            }
        }
    }

    private suspend fun ensurePrepared() {
        if (_corrections.value.prepared) return
        val orig = lastOriginalDoc ?: return
        val prod = lastProductDoc ?: return
        val planned = withContext(Dispatchers.Default) {
            CorrectionPlanner.plan(orig, prod)
        }
        _corrections.value = CorrectionsState(
            corrections = planned,
            prepared = true
        )
    }

    private fun approveLowRisk(mode: String) {
        val current = _corrections.value
        val updated = current.corrections.map { correction ->
            if (correction.status == CorrectionStatus.PENDING &&
                correction.isLowRisk
            ) {
                recordAudit("APPROVE", correction, mode, approved = true)
                correction.copy(status = CorrectionStatus.APPROVED)
            } else {
                correction
            }
        }
        _corrections.value = current.copy(corrections = updated)
    }

    fun applyCorrection(id: Int) {
        val current = _corrections.value
        val correction = current.corrections.firstOrNull { it.id == id } ?: return
        if (correction.status != CorrectionStatus.APPROVED) return
        val updated = current.corrections.map {
            if (it.id == id) it.copy(status = CorrectionStatus.APPLIED) else it
        }
        _corrections.value = current.copy(corrections = updated)
        recordAudit("APPLY", correction, "MANUAL", approved = true)
        rebuildCorrected()
    }

    fun applyApproved(mode: String = "MANUAL") {
        val current = _corrections.value
        var changed = false
        val updated = current.corrections.map { correction ->
            if (correction.status == CorrectionStatus.APPROVED) {
                recordAudit("APPLY", correction, mode, approved = true)
                changed = true
                correction.copy(status = CorrectionStatus.APPLIED)
            } else {
                correction
            }
        }
        if (!changed) {
            _notice.value = "Nothing is approved yet. Approve corrections " +
                "in Review, or use AUTO-CORRECT for the safe set."
            return
        }
        _corrections.value = current.copy(corrections = updated)
        rebuildCorrected()
    }

    fun undoCorrection(id: Int) {
        val current = _corrections.value
        val correction = current.corrections.firstOrNull { it.id == id } ?: return
        if (correction.status != CorrectionStatus.APPLIED) return
        val updated = current.corrections.map {
            if (it.id == id) it.copy(status = CorrectionStatus.UNDONE) else it
        }
        _corrections.value = current.copy(corrections = updated)
        recordAudit("UNDO", correction, "MANUAL", approved = null)
        rebuildCorrected()
    }

    fun undoAllApplied() {
        val current = _corrections.value
        var changed = false
        val updated = current.corrections.map { correction ->
            if (correction.status == CorrectionStatus.APPLIED) {
                recordAudit("UNDO", correction, "MANUAL", approved = null)
                changed = true
                correction.copy(status = CorrectionStatus.UNDONE)
            } else {
                correction
            }
        }
        if (!changed) return
        _corrections.value = current.copy(corrections = updated)
        rebuildCorrected()
        _notice.value = "All applied corrections were undone."
    }

    /** Rebuilds the corrected document and re-verifies it against the
     *  original. The final status comes from the engine, never from the
     *  number of corrections applied. */
    private fun rebuildCorrected() {
        val orig = lastOriginalDoc ?: return
        val prod = lastProductDoc ?: return
        val corrections = _corrections.value.corrections
        viewModelScope.launch {
            val (doc, result) = withContext(Dispatchers.Default) {
                val built = CorrectionApplier.buildCorrected(orig, prod, corrections)
                built to DiffEngine.compare(orig, built)
            }
            correctedDoc = doc
            _corrections.value = _corrections.value.copy(correctedResult = result)
        }
    }

    /** DOCX bytes of the corrected document, or null before anything
     *  has been applied. */
    fun correctedDocxBytes(): ByteArray? {
        val doc = correctedDoc ?: return null
        if (_corrections.value.appliedCount == 0) return null
        return DocxWriter.write(doc)
    }

    fun reportText(): String? {
        val initial = lastInitialResult ?: return null
        return VerificationReport.build(
            generatedAt = timestamp(),
            originalName = lastOriginalName,
            productName = lastProductName,
            initial = initial,
            corrections = _corrections.value.corrections,
            auditLines = auditLines.toList(),
            finalResult = _corrections.value.correctedResult
        )
    }

    private fun recordAudit(
        action: String,
        correction: PlannedCorrection,
        mode: String,
        approved: Boolean?
    ) {
        val now = System.currentTimeMillis()
        auditLines.add(
            "${timestamp(now)} $action #${correction.id} " +
                "[${correction.category}] ${correction.locationLabel} ($mode)"
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addAuditEntry(
                    AuditLogEntity(
                        projectId = 0,
                        action = action,
                        oldValue = correction.before,
                        newValue = correction.after,
                        category = correction.category.name,
                        evidence = correction.evidence,
                        mode = mode,
                        timestamp = now,
                        approved = approved
                    )
                )
            } catch (ignored: Exception) {
                // The in-memory audit trail (used by the report) has
                // already recorded this event; a database failure must
                // not crash a correction the user just made.
            }
        }
    }

    private fun timestamp(millis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
    }
}

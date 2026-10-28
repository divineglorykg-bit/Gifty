package com.divineit.convertor.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.ConvertorApp
import com.divineit.convertor.data.ProjectEntity
import com.divineit.convertor.domain.ContentKind
import com.divineit.convertor.domain.DocumentFormat
import com.divineit.convertor.domain.DocumentInfo
import com.divineit.convertor.domain.DocumentMetadata
import com.divineit.convertor.domain.DocumentSlot
import com.divineit.convertor.domain.DocumentStatus
import com.divineit.convertor.domain.FileValidation
import com.divineit.convertor.domain.Sha256
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the two upload slots. Comparison stays disabled until both slots
 *  are READY (see CompareScreen); the engines that use them land in
 *  Phases 3–4. */
class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ConvertorApp).projectRepository

    private val _original = MutableStateFlow<DocumentInfo?>(null)
    val original: StateFlow<DocumentInfo?> = _original.asStateFlow()

    private val _product = MutableStateFlow<DocumentInfo?>(null)
    val product: StateFlow<DocumentInfo?> = _product.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Metadata reads are capped so a huge file cannot exhaust memory.
     *  Hashing always streams the whole file. */
    private val metadataCapBytes = 12L * 1024 * 1024

    fun selectDocument(slot: DocumentSlot, uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            val info = withContext(Dispatchers.IO) {
                loadDocument(slot, uri)
            }
            when (slot) {
                DocumentSlot.ORIGINAL -> _original.value = info
                DocumentSlot.PRODUCT -> _product.value = info
            }
            _busy.value = false
        }
    }

    fun removeDocument(slot: DocumentSlot) {
        when (slot) {
            DocumentSlot.ORIGINAL -> _original.value = null
            DocumentSlot.PRODUCT -> _product.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Re-opens a saved project. A stored URI whose permission was lost or
     *  whose file moved becomes an ERROR slot asking the user to re-pick. */
    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: run {
                _message.value = "Saved project not found."
                return@launch
            }
            project.originalUri?.let {
                selectDocument(DocumentSlot.ORIGINAL, Uri.parse(it))
            }
            project.productUri?.let {
                selectDocument(DocumentSlot.PRODUCT, Uri.parse(it))
            }
        }
    }

    fun saveProject(localOnly: Boolean) {
        val orig = _original.value
        val prod = _product.value
        if (orig == null && prod == null) {
            _message.value = "Select at least one file before saving."
            return
        }
        viewModelScope.launch {
            val name = listOfNotNull(orig?.displayName, prod?.displayName)
                .joinToString(" × ")
                .ifBlank { "Untitled project" }
            repository.saveProject(
                ProjectEntity(
                    name = name,
                    originalUri = orig?.uri,
                    productUri = prod?.uri,
                    originalName = orig?.displayName,
                    productName = prod?.displayName,
                    createdAt = System.currentTimeMillis(),
                    localOnly = localOnly
                )
            )
            _message.value = "Project saved."
        }
    }

    private fun loadDocument(slot: DocumentSlot, uri: Uri): DocumentInfo {
        val resolver = getApplication<Application>().contentResolver
        var displayName = uri.lastPathSegment ?: "Unknown file"
        var sizeBytes: Long = -1
        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Name/size stay best-effort; the stream read below decides.
        }
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Persistable permission is not granted for every provider;
            // the file can still be used for this session.
        }

        val extension = FileValidation.extensionOf(displayName)
        return try {
            val stream = resolver.openInputStream(uri)
                ?: return errorInfo(slot, uri, displayName, extension, sizeBytes,
                    "Could not open this file.")
            val capped = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total: Long = 0
            var truncated = false
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > metadataCapBytes) {
                        truncated = true
                        break
                    }
                    capped.write(buffer, 0, read)
                }
            }
            val headBytes = capped.toByteArray()
            val detection = FileValidation.detect(headBytes, displayName)
            if (detection.status == DocumentStatus.ERROR) {
                return errorInfo(slot, uri, displayName, extension, sizeBytes,
                    detection.message, detection.format, detection.kind)
            }
            val pageCount = if (!truncated) {
                DocumentMetadata.pageCount(headBytes, detection.format)
            } else {
                null
            }
            val sha = try {
                resolver.openInputStream(uri)?.use { Sha256.hashStream(it) }
            } catch (e: Exception) {
                null
            }
            val snippet = if (detection.format == DocumentFormat.TXT && !truncated) {
                String(headBytes, Charsets.UTF_8).take(2000)
                    .takeIf { it.isNotBlank() }
            } else {
                null
            }
            DocumentInfo(
                slot = slot,
                uri = uri.toString(),
                displayName = displayName,
                extension = extension,
                sizeBytes = if (sizeBytes >= 0) sizeBytes else total,
                format = detection.format,
                kind = detection.kind,
                status = DocumentStatus.READY,
                statusMessage = if (truncated) {
                    detection.message +
                        " File is large; page count was not scanned."
                } else {
                    detection.message
                },
                pageCount = pageCount?.count,
                pageCountEstimate = pageCount?.estimate ?: false,
                sha256 = sha,
                previewSnippet = snippet
            )
        } catch (e: Exception) {
            errorInfo(slot, uri, displayName, extension, sizeBytes,
                "Could not read this file. It may have been moved or its " +
                    "permission may have expired — please pick it again.")
        }
    }

    private fun errorInfo(
        slot: DocumentSlot,
        uri: Uri,
        displayName: String,
        extension: String,
        sizeBytes: Long,
        message: String,
        format: DocumentFormat = DocumentFormat.UNKNOWN,
        kind: ContentKind = ContentKind.DAMAGED
    ): DocumentInfo = DocumentInfo(
        slot = slot,
        uri = uri.toString(),
        displayName = displayName,
        extension = extension,
        sizeBytes = sizeBytes,
        format = format,
        kind = kind,
        status = DocumentStatus.ERROR,
        statusMessage = message,
        pageCount = null,
        pageCountEstimate = false,
        sha256 = null,
        previewSnippet = null
    )
}

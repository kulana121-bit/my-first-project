package com.notes.app.data.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class FileStorageManager(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    val appNotesDir: File
        get() = File(context.filesDir, "notes").apply { mkdirs() }

    val appExportDir: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    suspend fun createAppNote(initialContent: String, extension: String): String = withContext(Dispatchers.IO) {
        val file = File(appNotesDir, "${UUID.randomUUID()}.$extension")
        file.writeText(initialContent)
        file.absolutePath
    }

    suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        runCatching { File(path).readText() }.getOrDefault("")
    }

    suspend fun writeText(path: String, text: String) = withContext(Dispatchers.IO) {
        File(path).apply { parentFile?.mkdirs() }.writeText(text)
    }

    suspend fun importDocument(uri: Uri, extension: String): String = withContext(Dispatchers.IO) {
        val file = File(appNotesDir, "${UUID.randomUUID()}.$extension")
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read source file")
        file.absolutePath
    }

    suspend fun exportHtml(title: String, html: String): File = withContext(Dispatchers.IO) {
        val safeName = title.ifBlank { "note" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(appExportDir, "$safeName.html")
        file.writeText(html)
        file
    }

    suspend fun exportPdf(title: String, htmlText: String): File = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val lines = htmlText.replace(Regex("<[^>]*>"), "").chunked(90)
        var y = 40f
        lines.forEach {
            if (y <= 810f) {
                page.canvas.drawText(it, 40f, y, android.graphics.Paint().apply { textSize = 12f })
                y += 18f
            }
        }
        pdf.finishPage(page)
        val safeName = title.ifBlank { "note" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(appExportDir, "$safeName.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        file
    }

    suspend fun deleteAppOwnedFile(path: String?): Result<Unit> = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext Result.success(Unit)
        val file = File(path)
        if (!file.exists()) return@withContext Result.success(Unit)
        val ok = file.delete()
        if (!ok || file.exists()) {
            Result.failure(IllegalStateException("Failed to delete file: $path"))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteExternalDocument(uriString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uri = uriString.toUri()
        val doc = DocumentFile.fromSingleUri(context, uri)
        val ok = doc?.delete() == true || runCatching {
            DocumentsContract.deleteDocument(resolver, uri)
        }.getOrDefault(false)
        if (ok) Result.success(Unit) else Result.failure(IllegalStateException("Permission denied or deletion failed"))
    }

    fun hasPersistedPermission(treeUri: Uri): Boolean {
        return resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
    }
}

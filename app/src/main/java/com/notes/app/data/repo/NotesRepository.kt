package com.notes.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notes.app.data.db.FolderEntity
import com.notes.app.data.db.NoteEntity
import com.notes.app.data.db.NotesDatabase
import com.notes.app.data.db.PdfBookmarkEntity
import com.notes.app.data.model.NoteType
import com.notes.app.data.model.ThemeMode
import com.notes.app.data.storage.FileStorageManager
import com.notes.app.data.sync.DeviceSyncCoordinator
import com.notes.app.data.sync.DeviceSyncWorker
import com.notes.app.data.sync.SyncReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

data class HomeState(
    val notes: List<NoteEntity>,
    val favorites: List<NoteEntity>,
    val recent: List<NoteEntity>,
    val folders: List<FolderEntity>
)

class NotesRepository(
    private val context: Context,
    private val database: NotesDatabase,
    private val storageManager: FileStorageManager,
    private val syncCoordinator: DeviceSyncCoordinator
) {
    private val notesDao = database.notesDao()
    private val foldersDao = database.foldersDao()
    private val bookmarksDao = database.bookmarksDao()
    private val syncDao = database.syncDao()
    private val prefs = UserPreferences(context)

    val homeState: Flow<HomeState> = combine(
        notesDao.observeActiveNotes(),
        notesDao.observeFavorites(),
        notesDao.observeRecent(),
        foldersDao.observeFolders()
    ) { notes, favorites, recent, folders ->
        HomeState(notes, favorites, recent.filter { !it.isTrashed }, folders)
    }

    val trashNotes: Flow<List<NoteEntity>> = notesDao.observeTrash()
    val folders: Flow<List<FolderEntity>> = foldersDao.observeFolders()
    val syncFolders = syncDao.observeSyncFolders()
    val themeMode = prefs.themeMode
    val autoSync = prefs.autoSync
    val listMode = prefs.listMode
    val lastSync = prefs.lastSync

    suspend fun createNote(type: NoteType, title: String): NoteEntity {
        val extension = when (type) {
            NoteType.HTML -> "html"
            NoteType.TEXT -> "txt"
            NoteType.PDF -> "pdf"
            NoteType.MARKDOWN -> "md"
        }
        val now = System.currentTimeMillis()
        val path = if (type == NoteType.PDF) null else storageManager.createAppNote("", extension)
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Untitled Note" },
            type = type,
            folderId = null,
            filePath = path,
            sourceUri = null,
            isExternal = false,
            isFavorite = false,
            isTrashed = false,
            createdAt = now,
            updatedAt = now,
            openedAt = now,
            trashedAt = null
        )
        notesDao.upsert(note)
        return note
    }

    suspend fun importFile(uri: Uri, displayName: String): NoteEntity {
        val ext = displayName.substringAfterLast('.', "txt").lowercase()
        val type = when (ext) {
            "pdf" -> NoteType.PDF
            "txt" -> NoteType.TEXT
            "md" -> NoteType.MARKDOWN
            else -> NoteType.HTML
        }
        val path = storageManager.importDocument(uri, ext)
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = displayName.substringBeforeLast('.'),
            type = type,
            folderId = null,
            filePath = path,
            sourceUri = uri.toString(),
            isExternal = false,
            isFavorite = false,
            isTrashed = false,
            createdAt = now,
            updatedAt = now,
            openedAt = now,
            trashedAt = null
        )
        notesDao.upsert(note)
        return note
    }

    suspend fun readNoteContent(note: NoteEntity): String {
        val path = note.filePath ?: return ""
        return storageManager.readText(path)
    }

    suspend fun saveNoteContent(noteId: String, title: String, html: String) {
        val note = notesDao.getById(noteId) ?: return
        val path = note.filePath ?: storageManager.createAppNote(html, if (note.type == NoteType.TEXT) "txt" else "html")
        storageManager.writeText(path, html)
        notesDao.upsert(note.copy(title = title, filePath = path, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveToTrash(noteId: String) {
        notesDao.updateTrashState(noteId, true, System.currentTimeMillis(), System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(noteId: String) {
        notesDao.updateTrashState(noteId, false, null, System.currentTimeMillis())
    }

    suspend fun deletePermanently(noteId: String): Result<Unit> {
        val note = notesDao.getById(noteId) ?: return Result.success(Unit)
        val delete = if (note.isExternal && !note.sourceUri.isNullOrBlank()) {
            storageManager.deleteExternalDocument(note.sourceUri)
        } else {
            storageManager.deleteAppOwnedFile(note.filePath)
        }

        return if (delete.isSuccess) {
            notesDao.deleteById(noteId)
            bookmarksDao.deleteForNote(noteId)
            Result.success(Unit)
        } else {
            delete
        }
    }

    suspend fun emptyTrash(): Result<Unit> {
        val trashed = trashNotes.first()
        val failures = mutableListOf<String>()
        trashed.forEach {
            val result = deletePermanently(it.id)
            if (result.isFailure) failures += it.title
        }
        return if (failures.isEmpty()) Result.success(Unit) else Result.failure(IllegalStateException("Failed: ${failures.joinToString()}"))
    }

    suspend fun toggleFavorite(noteId: String, favorite: Boolean) {
        notesDao.updateFavorite(noteId, favorite, System.currentTimeMillis())
    }

    suspend fun openNote(noteId: String) {
        notesDao.updateOpenedAt(noteId, System.currentTimeMillis())
    }

    fun observePdfBookmarks(noteId: String): Flow<List<PdfBookmarkEntity>> = bookmarksDao.observeForNote(noteId)

    suspend fun addPdfBookmark(noteId: String, page: Int, label: String) {
        bookmarksDao.upsert(PdfBookmarkEntity(noteId, page, label, System.currentTimeMillis()))
    }

    suspend fun removePdfBookmark(noteId: String, page: Int) {
        bookmarksDao.delete(noteId, page)
    }

    suspend fun addFolder(name: String) {
        val now = System.currentTimeMillis()
        foldersDao.upsert(FolderEntity(UUID.randomUUID().toString(), name, now, now))
    }

    suspend fun setTheme(themeMode: ThemeMode) = prefs.setTheme(themeMode)
    suspend fun setLibraryMode(mode: String) = prefs.setListMode(mode)
    suspend fun setAutoSync(enabled: Boolean) {
        prefs.setAutoSync(enabled)
        if (enabled) scheduleAutoSync() else cancelAutoSync()
    }

    suspend fun addSyncFolder(uri: Uri, displayName: String) {
        syncCoordinator.addFolder(uri, displayName)
    }

    suspend fun removeSyncFolder(treeUri: String) {
        syncCoordinator.removeFolder(treeUri)
    }

    suspend fun syncNow(): SyncReport {
        val report = syncCoordinator.syncNow()
        prefs.setLastSync(Instant.now().toString())
        return report
    }

    fun enqueueSyncNow() {
        val request = OneTimeWorkRequestBuilder<DeviceSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork("notes_sync_now", ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleAutoSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val request = PeriodicWorkRequestBuilder<DeviceSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "notes_auto_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelAutoSync() {
        WorkManager.getInstance(context).cancelUniqueWork("notes_auto_sync")
    }

    suspend fun exportHtml(note: NoteEntity): File = withContext(Dispatchers.IO) {
        val content = readNoteContent(note)
        storageManager.exportHtml(note.title, content)
    }

    suspend fun exportPdf(note: NoteEntity): File = withContext(Dispatchers.IO) {
        val content = readNoteContent(note)
        storageManager.exportPdf(note.title, content)
    }

    fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension.lowercase() == "pdf") "application/pdf" else "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun searchByQuery(query: String): List<NoteEntity> {
        if (query.isBlank()) return notesDao.observeActiveNotes().first()
        val active = notesDao.observeActiveNotes().first()
        val matchedContentIds = active.filter { note ->
            val path = note.filePath ?: return@filter false
            runCatching { storageManager.readText(path) }.getOrDefault("").contains(query, true)
        }.map { it.id }
        return notesDao.searchNotes(query, matchedContentIds).first()
    }

    suspend fun getNote(noteId: String): NoteEntity? = notesDao.getById(noteId)

    suspend fun copyNoteConflict(original: NoteEntity): NoteEntity {
        val now = System.currentTimeMillis()
        val copiedPath = original.filePath?.let { source ->
            val target = storageManager.createAppNote(storageManager.readText(source), File(source).extension.ifBlank { "html" })
            target
        }
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            title = "${original.title} (Device Copy)",
            filePath = copiedPath,
            createdAt = now,
            updatedAt = now,
            openedAt = now
        )
        notesDao.upsert(copy)
        return copy
    }
}

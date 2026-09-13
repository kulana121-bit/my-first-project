package com.notes.app.data.sync

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.notes.app.data.db.NoteEntity
import com.notes.app.data.db.NotesDatabase
import com.notes.app.data.db.SyncFolderEntity
import com.notes.app.data.db.SyncIndexEntity
import com.notes.app.data.model.NoteType
import com.notes.app.data.storage.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceSyncCoordinator(
    private val context: Context,
    private val database: NotesDatabase,
    private val storageManager: FileStorageManager
) {
    private val supported = setOf("html", "htm", "pdf", "txt", "md")

    suspend fun addFolder(uri: Uri, displayName: String) {
        database.syncDao().upsertFolder(
            SyncFolderEntity(
                treeUri = uri.toString(),
                displayName = displayName,
                addedAt = System.currentTimeMillis(),
                lastSyncAt = null
            )
        )
    }

    suspend fun removeFolder(treeUri: String) {
        database.syncDao().removeFolder(treeUri)
        database.syncDao().clearIndex(treeUri)
    }

    suspend fun syncNow(): SyncReport = withContext(Dispatchers.IO) {
        val folders = database.syncDao().observeSyncFoldersSnapshot()
        var imported = 0
        var modified = 0
        var removed = 0
        val errors = mutableListOf<String>()

        folders.forEach { folder ->
            val treeUri = folder.treeUri.toUri()
            if (!storageManager.hasPersistedPermission(treeUri)) {
                errors += "Permission missing for ${folder.displayName}"
                return@forEach
            }
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root == null) {
                errors += "Unable to access ${folder.displayName}"
                return@forEach
            }
            val previous = database.syncDao().getIndex(folder.treeUri).associateBy { it.docUri }
            val current = mutableListOf<SyncIndexEntity>()

            root.listFiles().filter { it.isFile }.forEach { doc ->
                val name = doc.name.orEmpty()
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in supported) return@forEach
                val docUri = doc.uri.toString()
                val modifiedAt = doc.lastModified()
                val size = doc.length()
                val indexed = previous[docUri]
                if (indexed == null) {
                    val path = storageManager.importDocument(doc.uri, ext)
                    val noteId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    database.notesDao().upsert(
                        NoteEntity(
                            id = noteId,
                            title = name.substringBeforeLast('.'),
                            type = ext.toNoteType(),
                            folderId = null,
                            filePath = path,
                            sourceUri = docUri,
                            isExternal = true,
                            isFavorite = false,
                            isTrashed = false,
                            createdAt = now,
                            updatedAt = now,
                            openedAt = now,
                            trashedAt = null
                        )
                    )
                    current += SyncIndexEntity(folder.treeUri, docUri, noteId, modifiedAt, size)
                    imported++
                } else if (indexed.modifiedAt != modifiedAt || indexed.sizeBytes != size) {
                    val note = database.notesDao().getById(indexed.noteId)
                    if (note != null) {
                        val existingPath = note.filePath
                        if (existingPath != null) {
                            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                                java.io.File(existingPath).outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        database.notesDao().upsert(note.copy(updatedAt = System.currentTimeMillis()))
                    }
                    current += indexed.copy(modifiedAt = modifiedAt, sizeBytes = size)
                    modified++
                } else {
                    current += indexed
                }
            }

            val deleted = previous.keys - current.map { it.docUri }.toSet()
            deleted.forEach { docUri ->
                previous[docUri]?.let { old ->
                    database.notesDao().getById(old.noteId)?.let { note ->
                        val deleteResult = if (note.isExternal && !note.sourceUri.isNullOrBlank()) {
                            Result.success(Unit)
                        } else {
                            storageManager.deleteAppOwnedFile(note.filePath)
                        }
                        if (deleteResult.isSuccess) {
                            database.notesDao().deleteById(note.id)
                            removed++
                        }
                    }
                }
            }

            database.syncDao().clearIndex(folder.treeUri)
            database.syncDao().upsertIndex(current)
            database.syncDao().upsertFolder(folder.copy(lastSyncAt = System.currentTimeMillis()))
        }

        SyncReport(imported, modified, removed, errors)
    }
}

data class SyncReport(
    val imported: Int,
    val modified: Int,
    val removed: Int,
    val errors: List<String>
)

private fun String.toNoteType(): NoteType = when (lowercase()) {
    "pdf" -> NoteType.PDF
    "txt" -> NoteType.TEXT
    "md" -> NoteType.MARKDOWN
    else -> NoteType.HTML
}

private suspend fun com.notes.app.data.db.SyncDao.observeSyncFoldersSnapshot(): List<SyncFolderEntity> =
    kotlinx.coroutines.flow.first(observeSyncFolders())

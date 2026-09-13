package com.notes.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.notes.app.data.model.NoteType

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "notes",
    indices = [Index("folderId"), Index("isTrashed"), Index("isFavorite")],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: NoteType,
    val folderId: String?,
    val filePath: String?,
    val sourceUri: String?,
    val isExternal: Boolean,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val openedAt: Long,
    val trashedAt: Long?
)

@Entity(tableName = "pdf_bookmarks", primaryKeys = ["noteId", "page"])
data class PdfBookmarkEntity(
    val noteId: String,
    val page: Int,
    val label: String,
    val createdAt: Long
)

@Entity(tableName = "sync_folders")
data class SyncFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastSyncAt: Long?
)

@Entity(tableName = "sync_index", primaryKeys = ["treeUri", "docUri"])
data class SyncIndexEntity(
    val treeUri: String,
    val docUri: String,
    val noteId: String,
    val modifiedAt: Long,
    val sizeBytes: Long
)
